package com.maurooyhanart.portfolio.log_stream_processing.config;

public record AppConfig(
        String streamName,
        String applicationName,
        String awsRegion,
        String dynamoTable,
        String eventBus,
        boolean alertsBlockCheckpoint
) {
    public static AppConfig fromEnv() {
        return new AppConfig(
                envOr("KINESIS_STREAM", "log-stream"),
                envOr("KCL_APP_NAME", "log-stream-consumer"),
                envOr("AWS_REGION", "us-east-2"),
                envOr("DDB_TABLE", "log-stream-logs"),
                envOr("EVENT_BUS", "default"),
                Boolean.parseBoolean(envOr("EVENTBRIDGE_REQUIRED", "false"))
        );
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
