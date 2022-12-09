package de.tub.nebulastream.benchmarks.flink.multiquery.ysb;

import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import de.tub.nebulastream.benchmarks.flink.ysb.YSBRecord;
import de.tub.nebulastream.benchmarks.flink.ysb.YSBSource;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;

public class YSB {

    private static final Logger LOG = LoggerFactory.getLogger(YSB.class);

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        final int sourceParallelism = params.getInt("sourceParallelism", 1);
        final long latencyTrackingInterval = params.getLong("latencyTrackingInterval", 0);
        final int parallelism = params.getInt("parallelism", 1);
        final int maxParallelism = params.getInt("maxParallelism", 16);
        final int numOfRecords = params.getInt("numOfRecords", 100_000);
        final int runtime = params.getInt("runtime", 10);
        final int queries = params.getInt("queries", 1);
        final boolean sourceSharing = params.getBoolean("sourceSharing", false);
        final boolean useKafka = params.has("useKafka");
        final String kafkaServers = params.get("kafkaServers", "35.242.227.178:9092");
        LOG.info("Arguments: {}", params);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        env.setParallelism(16);
        env.getConfig().enableObjectReuse();
        env.setMaxParallelism(maxParallelism);
        env.getConfig().setLatencyTrackingInterval(latencyTrackingInterval);

        env.getConfig().enableForceKryo();
        env.getConfig().registerTypeWithKryoSerializer(YSBRecord.class, YSBRecord.YSBRecordSerializer.class);
        env.getConfig().registerTypeWithKryoSerializer(YSBRecord.YSBFinalRecord.class, YSBRecord.YSBFinalRecordSerializer.class);
        env.getConfig().addDefaultKryoSerializer(YSBRecord.class, YSBRecord.YSBRecordSerializer.class);
        env.getConfig().addDefaultKryoSerializer(YSBRecord.YSBFinalRecord.class, YSBRecord.YSBFinalRecordSerializer.class);
        env.getConfig().registerKryoType(YSBRecord.class);
        env.getConfig().registerKryoType(YSBRecord.YSBFinalRecord.class);

        Properties baseCfg = new Properties();

        baseCfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServers);
        //   baseCfg.setProperty(ConsumerConfig.RECEIVE_BUFFER_CONFIG, "" + (4 * 1024 * 1024));
        //  baseCfg.setProperty(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "32768");
        baseCfg.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "im-job");
        // baseCfg.setProperty("offsets.commit.timeout.ms", "" + (3 * 60 * 1000));
        // baseCfg.setProperty(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, "" + (10 * 1024 * 1024));
        //baseCfg.setProperty(ConsumerConfig.CHECK_CRCS_CONFIG, "false");
//
        KafkaSource<YSBRecord[]> kafkaSource = KafkaSource.<YSBRecord[]>builder()
                .setBootstrapServers(kafkaServers)
                .setTopics("nesKafka")
                .setGroupId("flink")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new DeserializationSchema<YSBRecord[]>() {

                    long counter = 0;

                    @Override
                    public void open(InitializationContext context) throws Exception {
                        DeserializationSchema.super.open(context);
                    }

                    private boolean isPartitionConsumed = false;

                    private final static int YSB_RECORD_SIZE = 78;
                    private final TypeInformation<YSBRecord[]> FLINK_INTERNAL_TYPE = TypeInformation.of(new TypeHint<YSBRecord[]>() {
                    });

                    @Override
                    public YSBRecord[] deserialize(byte[] buffer) throws IOException {
                        YSBRecord[] data = new YSBRecord[1680];
                        ByteBuffer mbuff = ByteBuffer.wrap(buffer);
                        for (int i = 0; i < 1680; i++) {
                            YSBRecord ysb = new YSBRecord(
                                    mbuff.getLong(),
                                    mbuff.getLong(),
                                    mbuff.getLong(),
                                    mbuff.getLong(),
                                    mbuff.getLong(),
                                    mbuff.getLong(),
                                    mbuff.getLong(),
                                    mbuff.getInt(),
                                    mbuff.getShort()
                            );
                            data[i] = ysb;
                        }
                        counter = counter + 1;
                        return data;
                    }

                    @Override
                    public boolean isEndOfStream(YSBRecord[] ysbRecord) {
                        return counter >= 10000;
                    }

                    @Override
                    public TypeInformation<YSBRecord[]> getProducedType() {
                        return FLINK_INTERNAL_TYPE;
                    }
                })
                .build();

        if (sourceSharing) {

            SingleOutputStreamOperator<YSBRecord> source = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                    //.setParallelism(parallelism)
                    .flatMap(new FlatMapFunction<YSBRecord[], YSBRecord>() {
                        @Override
                        public void flatMap(YSBRecord[] ysbRecords, Collector<YSBRecord> collector) throws Exception {
                            for (YSBRecord r : ysbRecords) {
                                collector.collect(r);
                            }
                        }
                    });

            source.flatMap(new ThroughputLogger<YSBRecord>(YSBSource.RECORD_SIZE_IN_BYTE, 10_000));

            for (int i = 0; i < queries; i++) {
                source.flatMap(new Filter())
                        .keyBy((KeySelector<YSBRecord.YSBFinalRecord, Long>) r -> r.campaign_id)
                        .window(TumblingProcessingTimeWindows.of(Time.seconds(1)))
                        .aggregate(new WindowingLogic())
                        .name("WindowOperator")
                        .addSink(new SinkFunction<Long>() {
                            @Override
                            public void invoke(Long value) throws Exception {

                            }
                        });
            }
        } else {
            for (int i = 0; i < queries; i++) {

                SingleOutputStreamOperator<YSBRecord> source = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                        //.setParallelism(parallelism)
                        .flatMap(new FlatMapFunction<YSBRecord[], YSBRecord>() {
                            @Override
                            public void flatMap(YSBRecord[] ysbRecords, Collector<YSBRecord> collector) throws Exception {
                                for (YSBRecord r : ysbRecords) {
                                    collector.collect(r);
                                }
                            }
                        });
                source.flatMap(new ThroughputLogger<YSBRecord>(YSBSource.RECORD_SIZE_IN_BYTE, 1_000, i));

                source.flatMap(new Filter())
                        .keyBy((KeySelector<YSBRecord.YSBFinalRecord, Long>) r -> r.campaign_id)
                        .window(TumblingProcessingTimeWindows.of(Time.seconds(1)))
                        .aggregate(new WindowingLogic())
                        .name("WindowOperator")
                        .addSink(new SinkFunction<Long>() {
                            @Override
                            public void invoke(Long value) throws Exception {

                            }
                        });
            }
        }
        env.execute("YSB");

    }


    private static class WindowingLogic implements AggregateFunction<YSBRecord.YSBFinalRecord, Long, Long> {
        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(YSBRecord.YSBFinalRecord value, Long acc) {
            return acc + value.value;
        }

        @Override
        public Long getResult(Long acc) {
            return acc;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    private static class Filter implements FlatMapFunction<YSBRecord, YSBRecord.YSBFinalRecord> {

        @Override
        public void flatMap(YSBRecord in, Collector<YSBRecord.YSBFinalRecord> out) throws Exception {
            if (in.event_type == 2) { // wish for simd
                out.collect(new YSBRecord.YSBFinalRecord(in.campaign_id, (int) in.user_id));
            }
        }
    }

}
