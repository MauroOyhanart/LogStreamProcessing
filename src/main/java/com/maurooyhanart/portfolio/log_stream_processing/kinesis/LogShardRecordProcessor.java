package com.maurooyhanart.portfolio.log_stream_processing.kinesis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maurooyhanart.portfolio.log_stream_processing.config.AppConfig;
import com.maurooyhanart.portfolio.log_stream_processing.model.LogEvent;
import com.maurooyhanart.portfolio.log_stream_processing.outputs.DynamoWriter;
import com.maurooyhanart.portfolio.log_stream_processing.outputs.EventBridgePublisher;
import software.amazon.kinesis.lifecycle.events.*;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class LogShardRecordProcessor implements ShardRecordProcessor {
    private static final int   BATCH_MAX_ITEMS   = 25;       // DynamoDB BatchWriteItem hard cap
    private static final long  FLUSH_MAX_AGE_MS  = 150;      // time-based flush bound
    private static final long  BATCH_MAX_BYTES   = 4_000_000L; // ~4MB safety size

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DynamoWriter dynamoWriter;
    private final EventBridgePublisher eventPublisher;

    // ---- Batch buffer ----
    private final List<Pending> buffer = new ArrayList<>(BATCH_MAX_ITEMS);
    private long bufferedBytes = 0L;
    private long firstEnqueueAtMs = 0L;

    public LogShardRecordProcessor(AppConfig config) {
        this.dynamoWriter = new DynamoWriter(config);
        this.eventPublisher = new EventBridgePublisher(config);
    }

    @Override public void initialize(InitializationInput input) { }

    @Override
    public void processRecords(ProcessRecordsInput input) {
        for (KinesisClientRecord r : input.records()) {
            final String raw = StandardCharsets.UTF_8.decode(r.data()).toString();
            try {
                LogEvent e = mapper.readValue(raw, LogEvent.class);
                if (e.level != null && e.level.equalsIgnoreCase("ERROR")) {
                    runWithBackoff(() -> eventPublisher.publish(e));
                }
                enqueue(e, r.sequenceNumber());
            } catch (Exception ignored) {
                // we could send to DLQ (dead letter queue)
            }
            maybeFlush(input.checkpointer(), false);
        }

        // make sure about the time-based flush
        maybeFlush(input.checkpointer(), false);
    }

    @Override public void leaseLost(LeaseLostInput input) {
        buffer.clear();
        bufferedBytes = 0L;
        firstEnqueueAtMs = 0L;
    }

    @Override public void shardEnded(ShardEndedInput input) {
        maybeFlush(input.checkpointer(), true);
        runWithBackoff(() -> {
            try { input.checkpointer().checkpoint(); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Override public void shutdownRequested(ShutdownRequestedInput input) {
        maybeFlush(input.checkpointer(), true);
        runWithBackoff(() -> {
            try { input.checkpointer().checkpoint(); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    // Internal helpers

    private static final class Pending {
        final LogEvent event;
        final String sequenceNumber;
        final byte[] serialized;

        Pending(LogEvent event, String seq, byte[] serialized) {
            this.event = event;
            this.sequenceNumber = seq;
            this.serialized = serialized;
        }
    }

    private void enqueue(LogEvent e, String sequenceNumber) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(e);
        if (buffer.isEmpty()) firstEnqueueAtMs = nowMs();
        buffer.add(new Pending(e, sequenceNumber, bytes));
        bufferedBytes += bytes.length;
    }

    /**
     * <p> Does the following things: </p>
     * <ul>
     * <li>decide whether to flush or not. for this, it checks if the size is exceeded, the byte size is exceded or the time of the first added record to the buffer is exceeded. </li>
     * <li>snapshot and clear buffer </li>
     * <li>write to dynamodb in slices of BATCH_MAX_ITEMS = 25, which is what DynamoDB allows. </li>
     * <li>checkpoint to the last flushed record </li>
     * </ul>
     * @param checkpointer the KCL checkpointer
     * @param force whether to force flush or not
     */
    private void maybeFlush(RecordProcessorCheckpointer checkpointer, boolean force) {
        List<Pending> toFlush = null;
        String lastSeq = null;

        boolean sizeHit  = buffer.size() >= BATCH_MAX_ITEMS;
        boolean bytesHit = bufferedBytes >= BATCH_MAX_BYTES;
        boolean timeHit  = (!buffer.isEmpty() && (nowMs() - firstEnqueueAtMs) >= FLUSH_MAX_AGE_MS);

        if (force || sizeHit || bytesHit || timeHit) {
            if (buffer.isEmpty()) return;

            toFlush = new ArrayList<>(buffer);
            lastSeq = toFlush.get(toFlush.size() - 1).sequenceNumber;

            buffer.clear();
            bufferedBytes = 0L;
            firstEnqueueAtMs = 0L;
        }

        if (toFlush == null) return;

        // Here starts the part of the code that should be definitely handled asynchronously
        List<LogEvent> slice = new ArrayList<>(BATCH_MAX_ITEMS);
        int i = 0;
        while (i < toFlush.size()) {
            slice.clear();
            int end = Math.min(i + BATCH_MAX_ITEMS, toFlush.size());
            for (int j = i; j < end; j++) slice.add(toFlush.get(j).event);

            final List<LogEvent> batchRef = new ArrayList<>(slice);
            runWithBackoff(() -> dynamoWriter.putBatch(batchRef));
            i = end;
        }

        final String seq = lastSeq;
        runWithBackoff(() -> {
            try { checkpointer.checkpoint(seq); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    /**
     * Runs an action with a basic implementation of an exponential backoff mechanism
     * @param action the action to run
     */
    private void runWithBackoff(Runnable action) {
        int maxRetries = 5;
        long baseMillis = 200L;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                return;
            } catch (Exception ex) {
                if (attempt == maxRetries) throw ex;
                long jitter = ThreadLocalRandom.current().nextLong(0, baseMillis);
                long sleep = Math.min(2500L, (long)(baseMillis * Math.pow(2, attempt))) + jitter;
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static long nowMs() { return Instant.now().toEpochMilli(); }
}
