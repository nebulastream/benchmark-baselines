package benchmarks;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import serialization.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static serialization.NexmarkAuctionSerde.deserializeAuctionBuffer;
import static serialization.NexmarkBidSerde.deserializeBidBuffer;
import static serialization.YSBSerde.deserializeYSBBuffer;

/*
STATUS

- NexmarkAuction Serdes & Batch Deserialization working
- Nexmark Bid and Person are unfinished/untested


 */



public class KafkaStreamsBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(KafkaStreamsBenchmark.class);
    private static final Properties props = new Properties();
    private static final Serde<String> stringSerde = Serdes.String();
    private static final Serde<Integer> intSerde = Serdes.Integer();
    private static final Serde<Long> longSerde = Serdes.Long();
    private static final Serde<Bytes> bytesSerde = Serdes.Bytes();

    private static final Serde<NexmarkAuctionRecord> auctionSerde = new NexmarkAuctionSerde();
    private static final Serde<NexmarkBidRecord> bidSerde = new NexmarkBidSerde();
    private static final Serde<NexmarkPersonRecord> personSerde = new NexmarkPersonSerde();
    private static final Serde<YSBRecord> ysbSerde = new YSBSerde();


    static void runKafkaStreams(final KafkaStreams streams) {
        final CountDownLatch latch = new CountDownLatch(1);
        streams.setStateListener((newState, oldState) -> {
            if (oldState == KafkaStreams.State.RUNNING && newState != KafkaStreams.State.RUNNING) {
                latch.countDown();
            }
        });

        streams.start();

        try {
            latch.await();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        logger.info("Streams Closed");
    }


    // Deserialization Tests (data comes in batches from generator)
    static Topology buildAuctionBufferTestTopology(String auctionTopic, String outputTopic) {
        StreamsBuilder builder = new StreamsBuilder();

        builder
            .stream(auctionTopic, Consumed.with(bytesSerde, bytesSerde))
            .flatMapValues( values -> deserializeAuctionBuffer(values.get()) )
            .peek((k,v) -> logger.info("Observed event: {} {}", v.auctionId, v.timestamp))
            .to(outputTopic, Produced.with(bytesSerde, auctionSerde))
        ;

        return builder.build();
    }

    static Topology buildBidBufferTestTopology(String auctionTopic, String outputTopic) {
        StreamsBuilder builder = new StreamsBuilder();

        builder
            .stream(auctionTopic, Consumed.with(bytesSerde, bytesSerde))
            .flatMapValues( values -> deserializeBidBuffer(values.get()) )
            .peek((k,v) -> logger.info("Observed BID event: personId: {}, auctionId: {}, bid: {}", v.personId, v.auctionId, v.bid))
            .to(outputTopic, Produced.with(bytesSerde, bidSerde))
        ;

        return builder.build();
    }

    static Topology buildYSBBufferTestTopology(String ysbBatchTopic, String outputTopic) {
        StreamsBuilder builder = new StreamsBuilder();

        builder
            .stream(ysbBatchTopic, Consumed.with(bytesSerde, bytesSerde))
            .flatMapValues( values -> deserializeYSBBuffer(values.get()) )
            .peek((k,v) -> logger.info("Observed YSB event: user_id: {}, campaign_id: {}, IP: {}", v.user_id, v.campaign_id, v.ip))
            .to(outputTopic, Produced.with(bytesSerde, ysbSerde))
        ;

        return builder.build();
    }

    // Tests for Kafka-internal SerDes
    static Topology buildAuctionRecordTestTopology(String auctionRecordTopic) {
        StreamsBuilder builder = new StreamsBuilder();

        builder
            .stream(auctionRecordTopic, Consumed.with(bytesSerde, auctionSerde))
            .foreach((k,v) -> logger.info("Record from 'Output_topic': {} {}", v.auctionId, v.timestamp))
        ;

        return builder.build();
    }
    // Nexmark Queries
//    static Topology topologyNexmarkQ5(String auctionTopic, String outputTopic) {
//        StreamsBuilder builder = new StreamsBuilder();
//        builder
//                .stream(auctionTopic, Consumed.with(bytesSerde, bytesSerde))
//                .flatMapValues( values -> deserializeAuctionBuffer(values.get()) )
//                .process(new KStreamSessionWindowAggregate<Serde.Bytes(), auctionSerde.getClass()>())
//        // todo continue here
//                .peek((k,v) -> logger.info("Observed event: {} {}", v.auctionId, v.timestamp))
//                .filter((k,v) -> (v.auctionId > 10))
//                .peek((k,v) -> logger.info("Transformed event: {} {}", v.auctionId, v.timestamp))
//        ;
//
//        return builder.build();
//    }

    // YSB Query
    static Topology topologyYSB(String ysbTopic) {
        StreamsBuilder builder = new StreamsBuilder();

        Aggregator<Bytes, YSBRecord, Long> ysbAggUserIdSum =
                (key, value, prevSum) -> value.user_id + prevSum;

        Duration windowSize = Duration.ofSeconds(2);
        TimeWindows tumblingWindow = TimeWindows.of(windowSize);

        builder
                .stream(ysbTopic, Consumed.with(bytesSerde, bytesSerde))
                .flatMapValues( values -> deserializeYSBBuffer(values.get()) )
                .groupByKey()
                .windowedBy(tumblingWindow)
                .aggregate(() -> 0L,
                        ysbAggUserIdSum,
                        Materialized.with(bytesSerde, longSerde))
                .toStream()
                .peek((k, v) -> logger.info("sum of window: " + v))
        ;

        return builder.build();
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to a configuration file.");
        }

        try (InputStream inputStream = new FileInputStream(args[0])) {
            props.load(inputStream);
        }

        final String personTopic = "nexmark_auctions"; // props.getProperty("topic.name.nexmark.person");       ^
        final String auctionTopic = "nexmark_persons"; // props.getProperty("topic.name.nexmark.auction");
        final String bidTopic = "nexmark_bids"; // props.getProperty("topic.name.nexmark.bid");

        final String ysbTopic = "ysb_batch"; // props.getProperty("topic.name.ysb");

        final String outputTopic = "output_topic"; // props.getProperty("topic.name.output");

        logger.info("Nexmark person topic: " + personTopic);
        logger.info("Nexmark auction topic: " + auctionTopic);
        logger.info("Nexmark bid topic: " + bidTopic);

        logger.info("YSB topic: " + ysbTopic);

        logger.info("output topic: " + outputTopic);

        try (Util utility = new Util()) {
            utility.createTopics(
                props,
                Arrays.asList(
                        new NewTopic(personTopic, Optional.empty(), Optional.empty()),
                        new NewTopic(auctionTopic, Optional.empty(), Optional.empty()),
                        new NewTopic(bidTopic, Optional.empty(), Optional.empty()),
                        new NewTopic(outputTopic, Optional.empty(), Optional.empty()),
                        new NewTopic(ysbTopic, Optional.empty(), Optional.empty())
                ));

            boolean serializationTest = false;
            if (serializationTest == true) {
                // only to test if SerDes work
                try (Util.DummyProducer rando = utility.startNewDummyProducer(props, outputTopic)) {

                    KafkaStreams kafkaStreams = new KafkaStreams(
                            buildAuctionRecordTestTopology(outputTopic),
                            props);

                    Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));

                    logger.info("SerDes Tests Started");
                    runKafkaStreams(kafkaStreams);

                }

            } else {
                KafkaStreams kafkaStreams = new KafkaStreams(
//                        buildAuctionBufferTestTopology(auctionTopic, outputTopic),
//                        buildAuctionRecordTestTopology(outputTopic),
//                        buildBidBufferTestTopology(bidTopic, outputTopic),
//                        buildYSBBufferTestTopology(ysbTopic, outputTopic),

//                        topologyNexmarkQ5(auctionTopic, outputTopic),
                        topologyYSB(ysbTopic),
                        props);

                Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));

                logger.info("Benchmarks Started");
                runKafkaStreams(kafkaStreams);
            }
        }
    }
}
