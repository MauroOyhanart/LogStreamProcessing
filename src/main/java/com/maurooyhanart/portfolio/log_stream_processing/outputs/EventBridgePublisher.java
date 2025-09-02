package com.maurooyhanart.portfolio.log_stream_processing.outputs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maurooyhanart.portfolio.log_stream_processing.config.AppConfig;
import com.maurooyhanart.portfolio.log_stream_processing.model.LogEvent;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public class EventBridgePublisher {
    private final AppConfig config;
    private final EventBridgeClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventBridgePublisher(AppConfig config) {
        this.config = config;
        this.client = EventBridgeClient.builder()
                .region(Region.of(config.awsRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public void publish(LogEvent event) {
        runWithBackoff(() -> {
            try {
                String detail = mapper.writeValueAsString(event);
                PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                        .eventBusName(config.eventBus())
                        .source("log.stream.processor")
                        .detailType("LogError")
                        .detail(detail)
                        .build();
                client.putEvents(PutEventsRequest.builder().entries(entry).build());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private void runWithBackoff(Runnable action) {
        int maxRetries = 5; long baseMillis = 200L;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try { action.run(); return; }
            catch (Exception ex) {
                if (attempt == maxRetries) throw ex;
                long jitter = (long)(Math.random()*baseMillis);
                long sleep = Math.min(5000, baseMillis * (1L << attempt)) + jitter;
                try { Thread.sleep(sleep); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
}


