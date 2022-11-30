package de.tub.nebulastream.benchmarks.flink.ysb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.tub.nebulastream.benchmarks.flink.utils.AnalyzeTool;
import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;
import org.apache.flink.util.Preconditions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class YSB {

    private static final Logger LOG = LoggerFactory.getLogger(YSB.class);

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        final int sourceParallelism = params.getInt("sourceParallelism", 1);
        final long latencyTrackingInterval = params.getLong("latencyTrackingInterval", 0);
        final int parallelism = params.getInt("parallelism", 1);
        final int maxParallelism = params.getInt("maxParallelism", 16);
        final int numOfRecords = params.getInt("numOfRecords", 1_000_000);
        final int runtime = params.getInt("runtime", 10);
        final boolean useKafka = params.has("useKafka");
        final String kafkaServers = params.get("kafkaServers", "34.107.58.147:9092");
        LOG.info("Arguments: {}", params);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(parallelism);
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

        DataStream<YSBRecord> source = null;
        if (true) {

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
                    .setTopics("nesKafka2")
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

                            /*Preconditions.checkArgument(buffer.length == 8192);

                            ByteBuffer wrapper = ByteBuffer.wrap(buffer);
                            int checksum = wrapper.getInt();
                            int itemsInThisBuffer = wrapper.getInt();
                            long newBacklog = wrapper.getLong();

                            Preconditions.checkArgument(checksum == 0x30011991);
                            Preconditions.checkArgument(((8192 - 16) / YSB_RECORD_SIZE) >= itemsInThisBuffer);

                            YSBRecord[] data = new YSBRecord[itemsInThisBuffer];
                            long ingestionTimestamp = System.currentTimeMillis();

                            for (int i = 0; i < data.length; i++) {
                                long dummy1 = wrapper.getLong();
                                long dummy2 = wrapper.getLong();
                                long campaign_id = wrapper.getLong();
                                long dummy3 = wrapper.getLong();
                                long eventType = wrapper.getLong();
                                long timestamp = wrapper.getLong();
                                long ip = wrapper.getLong();
                                long dummy4 = wrapper.getLong();
                                long dummy5 = wrapper.getLong();
                                int dummy6 = wrapper.getInt();
                                short dummy7 = wrapper.getShort();

                                data[i] = new YSBRecord(dummy1, dummy2, campaign_id, eventType, ip, dummy4, dummy5, dummy6, dummy7);
                            }
                            isPartitionConsumed = newBacklog <= itemsInThisBuffer;
                            return data;

                             */
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
            source = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                    .setParallelism(parallelism)
                    .flatMap(new FlatMapFunction<YSBRecord[], YSBRecord>() {
                        @Override
                        public void flatMap(YSBRecord[] ysbRecords, Collector<YSBRecord> collector) throws Exception {
                            for (YSBRecord r : ysbRecords) {
                                collector.collect(r);
                            }
                        }
                    }).setParallelism(parallelism);
        } else {
            source = env.addSource(new YSBSource(runtime, numOfRecords)).setParallelism(parallelism);

        }


        source.flatMap(new ThroughputLogger<YSBRecord>(YSBSource.RECORD_SIZE_IN_BYTE, 1_000_000));

        source.flatMap(new Filter())
                .setParallelism(parallelism)
                .keyBy((KeySelector<YSBRecord.YSBFinalRecord, Long>) r -> r.campaign_id)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(30)))
                .aggregate(new WindowingLogic())
                .setMaxParallelism(maxParallelism)
                .name("WindowOperator")
                .addSink(new SinkFunction<Long>() {
                    @Override
                    public void invoke(Long value) throws Exception {

                    }
                });

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
            if (in.event_type < 1) { // wish for simd
                out.collect(new YSBRecord.YSBFinalRecord(in.campaign_id, (int) in.user_id));
            }
        }
    }

}
