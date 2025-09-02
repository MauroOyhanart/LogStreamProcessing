package com.maurooyhanart.portfolio.log_stream_processing;

import com.maurooyhanart.portfolio.log_stream_processing.config.AppConfig;
import com.maurooyhanart.portfolio.log_stream_processing.kinesis.LogRecordProcessorFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.retrieval.polling.PollingConfig;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.retrieval.RetrievalConfig;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.common.StreamIdentifier;
import software.amazon.kinesis.processor.SingleStreamTracker;

import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        AppConfig cfg = AppConfig.fromEnv();
        DefaultCredentialsProvider creds = DefaultCredentialsProvider.create();
        Region region = Region.of(cfg.awsRegion());
        String workerId = UUID.randomUUID().toString();

        KinesisAsyncClient kinesis = KinesisAsyncClient.builder().region(region).credentialsProvider(creds).build();
        DynamoDbAsyncClient dynamo = DynamoDbAsyncClient.builder().region(region).credentialsProvider(creds).build();
        CloudWatchAsyncClient cloudwatch = CloudWatchAsyncClient.builder().region(region).credentialsProvider(creds).build();

        LogRecordProcessorFactory factory = new LogRecordProcessorFactory(cfg);
        InitialPositionInStream initialPosition = InitialPositionInStream.LATEST;
        InitialPositionInStreamExtended initialPositionExtended =
                InitialPositionInStreamExtended.newInitialPosition(initialPosition);
        SingleStreamTracker streamTracker = new SingleStreamTracker(
                StreamIdentifier.singleStreamInstance(cfg.streamName()),
                initialPositionExtended
        );

        PollingConfig pollingConfig = new PollingConfig(cfg.streamName(), kinesis)
                .idleTimeBetweenReadsInMillis(0)
                .maxRecords(1000);

        ConfigsBuilder configs = new ConfigsBuilder(
                streamTracker,
                cfg.applicationName(),
                kinesis,
                dynamo,
                cloudwatch,
                workerId,
                factory
        );

        RetrievalConfig retrieval = configs.retrievalConfig()
                .retrievalSpecificConfig(pollingConfig);

        Scheduler scheduler = new Scheduler(
                configs.checkpointConfig(),
                configs.coordinatorConfig(),
                configs.leaseManagementConfig(),
                configs.lifecycleConfig(),
                configs.metricsConfig(),
                configs.processorConfig(),
                retrieval
        );

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
        scheduler.run();
    }
}
