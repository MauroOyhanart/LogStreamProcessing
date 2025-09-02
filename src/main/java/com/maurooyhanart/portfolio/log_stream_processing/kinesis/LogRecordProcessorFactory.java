package com.maurooyhanart.portfolio.log_stream_processing.kinesis;

import com.maurooyhanart.portfolio.log_stream_processing.config.AppConfig;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;

public class LogRecordProcessorFactory implements ShardRecordProcessorFactory {
    private final AppConfig config;
    public LogRecordProcessorFactory(AppConfig config) { this.config = config; }
    @Override public ShardRecordProcessor shardRecordProcessor() {
        return new LogShardRecordProcessor(config);
    }
}


