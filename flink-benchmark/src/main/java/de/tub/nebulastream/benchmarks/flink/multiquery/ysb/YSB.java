package de.tub.nebulastream.benchmarks.flink.multiquery.ysb;

import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import de.tub.nebulastream.benchmarks.flink.ysb.YSBRecord;
import de.tub.nebulastream.benchmarks.flink.ysb.YSBSource;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.common.functions.FilterFunction;

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

//        sourceSharing = true;
//        if (sourceSharing) {

            DataStreamSource<YSBRecord> source = env.addSource(new YSBSource(runtime, numOfRecords))
                    .setParallelism(parallelism);

            source.flatMap(new ThroughputLogger<YSBRecord>(YSBSource.RECORD_SIZE_IN_BYTE, 10_000));

            for (int i = 0; i < queries; i++) {
                source.filter(new FilterFunction<YSBRecord>() {
                    @Override
                    public boolean filter(YSBRecord value) throws Exception {
                        return value.event_type > 8;
                    }
                })
                        .addSink(new SinkFunction<YSBRecord>() {
                            @Override
                            public void invoke(YSBRecord value) throws Exception {

                            }
                        });
            }
//        } else {
//            for (int i = 0; i < queries; i++) {
//                DataStreamSource<YSBRecord> source = env.addSource(new YSBSource(runtime, numOfRecords))
//                        .setParallelism(parallelism);
//
//                source.flatMap(new ThroughputLogger<YSBRecord>(YSBSource.RECORD_SIZE_IN_BYTE, 1_000, i));
//
//                source.flatMap(new Filter())
////                        .keyBy((KeySelector<YSBRecord.YSBFinalRecord, Long>) r -> r.campaign_id)
////                        .window(TumblingProcessingTimeWindows.of(Time.seconds(1)))
////                        .aggregate(new WindowingLogic())
////                        .name("WindowOperator")
//                        .addSink(new SinkFunction<Long>() {
//                            @Override
//                            public void invoke(Long value) throws Exception {
//
//                            }
//                        });
//            }
//        }
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
            if (in.event_type > 8) { // wish for simd
                out.collect(new YSBRecord.YSBFinalRecord(in.campaign_id, (int) in.user_id));
            }
        }
    }

}
