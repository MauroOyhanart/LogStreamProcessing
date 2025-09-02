package com.maurooyhanart.portfolio.log_stream_processing.outputs;

import com.maurooyhanart.portfolio.log_stream_processing.config.AppConfig;
import com.maurooyhanart.portfolio.log_stream_processing.model.LogEvent;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DynamoWriter {
    private static final int MAX_BATCH = 25;
    private static final int MAX_RETRIES = 8;
    private static final long BASE_BACKOFF_MS = 200L;

    private static final int PK_BUCKETS = 16;

    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);

    private final AppConfig config;
    private final DynamoDbClient ddb;

    public DynamoWriter(AppConfig config) {
        this.config = config;
        this.ddb = DynamoDbClient.builder()
                .region(Region.of(config.awsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Writes the given log events to DynamoDB using BatchWriteItem with retry/backoff.
     *
     * <p><strong>Behavior</strong>
     * <ul>
     *   <li>No-op if {@code events} is {@code null} or empty, or if the configured table name is blank.</li>
     *   <li>Each {@link LogEvent} is transformed into a PutRequest via {@link #buildItem(LogEvent)}.</li>
     *   <li>Requests are sliced into batches of at most {@value MAX_BATCH} items (DynamoDB hard limit).</li>
     *   <li>For each batch, if DynamoDB returns {@code UnprocessedItems}, the method retries
     *   the remaining items with exponential backoff and jitter until either all succeed
     *   or {@value MAX_RETRIES} attempts are exhausted.</li>
     * </ul>
     *
     * @param events List of {@link LogEvent} to write; each becomes a single PutRequest item.
     * @throws RuntimeException if retry budget is exhausted or the thread is interrupted during backoff.
     */
    public void putBatch(List<LogEvent> events) {
        if (events == null || events.isEmpty()) return;
        final String table = config.dynamoTable();
        if (table == null || table.isBlank()) return;

        final List<WriteRequest> all = new ArrayList<>(events.size());
        for (LogEvent e : events) {
            all.add(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(buildItem(e)).build())
                    .build());
        }

        for (int i = 0; i < all.size(); i += MAX_BATCH) {
            final List<WriteRequest> slice = new ArrayList<>(all.subList(i, Math.min(i + MAX_BATCH, all.size())));
            Map<String, List<WriteRequest>> req = new HashMap<>();
            req.put(table, slice);

            int attempt = 0;
            while (!req.isEmpty()) {
                BatchWriteItemResponse resp = ddb.batchWriteItem(
                        BatchWriteItemRequest.builder().requestItems(req).build());

                Map<String, List<WriteRequest>> unprocessed = resp.unprocessedItems();
                int requested = req.getOrDefault(table, List.of()).size();
                int remaining = unprocessed.getOrDefault(table, List.of()).size();
                // we want to see throttling
                System.out.printf("DDB batch: requested=%d unprocessed=%d attempt=%d%n",
                        requested, remaining, attempt);

                if (unprocessed.isEmpty()) break;

                req = unprocessed;

                if (attempt++ >= MAX_RETRIES) {
                    int still = unprocessed.values().stream().mapToInt(List::size).sum();
                    throw new RuntimeException("DynamoDB BatchWrite exhausted retries; " +
                            still + " items still unprocessed");
                }
                long jitter = ThreadLocalRandom.current().nextLong(0, BASE_BACKOFF_MS);
                long sleep = Math.min(2500L, (long) (BASE_BACKOFF_MS * Math.pow(2, attempt))) + jitter;
                try { Thread.sleep(sleep); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying UnprocessedItems", ie);
                }
            }
        }
    }

    /**
     * <p> Builds and returns an object that is writeable into DynamoDB. </p>
     * <p> This method generates a timestamp for the item, so that we can then group hourly when doing queries in DynamoDB.</p>
     * <p> It also ensures: </p>
     * <ul>
     *      <li>uniqueness of objects stored in the table (since all fields like service, timestamp and text, etc can be repeated and that would be marked as a duplicate, when in fact it's not),</li>
     *      <li>no hot-partitioning (there's bucketing of events (#b0–#b15) based on a hash), and </li>
     *      <li>that we can do range queries using the timestamp </li>
     * </ul>
     * @param e the item to write to DynamoDB
     * @return an object that is writeable into DynamoDB
     */
    private Map<String, AttributeValue> buildItem(LogEvent e) {
        Map<String, AttributeValue> m = new HashMap<>();

        long ts = (e.timestamp != null ? e.timestamp : Instant.now()).toEpochMilli();
        String hourBucket = HOUR_FMT.format(Instant.ofEpochMilli(ts));

        String svc = safe(e.service);
        String base = svc.isBlank() ? "unknown" : svc;

        // n-sharded partition key. the idea is to avoid hot partitions for busy services or hours
        int shard = positiveHash(base + "#" + hourBucket) % PK_BUCKETS;
        String pk = base + "#" + hourBucket + "#b" + shard;

        // Time-ordered + # + unique sort key
        String sk = ts + "#" + UUID.randomUUID();

        m.put("pk", AttributeValue.builder().s(pk).build());
        m.put("sk", AttributeValue.builder().s(sk).build());
        if (!safe(e.level).isBlank())   m.put("level",   AttributeValue.builder().s(e.level).build());
        if (!safe(e.message).isBlank()) m.put("message", AttributeValue.builder().s(e.message).build());
        if (!svc.isBlank())             m.put("service", AttributeValue.builder().s(svc).build());
        m.put("ts", AttributeValue.builder().n(Long.toString(ts)).build());

        return m;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * @param s input for the hash function
     * @return a positive hash from the string {@code s}
     */
    private static int positiveHash(String s) {
        if (s == null) {
            return 0;
        }
        int h = s.hashCode();
        if (h < 0) {
            h = -h;
        }
        return h;
    }
}
