package de.tub.nebulastream.benchmarks.flink.linearroad;

import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import de.tub.nebulastream.benchmarks.flink.ysb.YSB;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LR2 {

    private static final Logger LOG = LoggerFactory.getLogger(YSB.class);

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        final long latencyTrackingInterval = params.getLong("latencyTrackingInterval", 0);
        final int parallelism = params.getInt("parallelism", 1);
        final int maxParallelism = params.getInt("maxParallelism", 16);
        final int numOfRecords = params.getInt("numOfRecords", 100_000);
        final int runtime = params.getInt("runtime", 10);

        LOG.info("Arguments: {}", params);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        env.setParallelism(parallelism);
        env.getConfig().enableObjectReuse();
        env.setMaxParallelism(maxParallelism);
        env.getConfig().setLatencyTrackingInterval(latencyTrackingInterval);

        DataStreamSource<LRRecord> source = env.addSource(new LRSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        source.flatMap(new ThroughputLogger<LRRecord>(LRSource.RECORD_SIZE_IN_BYTE, 1_000));

        source
                .map(new MapFunction<LRRecord, LRRecord>() {
                    @Override
                    public LRRecord map(LRRecord value) throws Exception {
                        value.position = (short) (value.position / 5280);
                        return value;
                    }
                })
                .keyBy(new KeySelector<LRRecord, Tuple4<Short, Short,Short,Short>>() {
                    @Override
                    public Tuple4<Short, Short, Short, Short> getKey(LRRecord value) throws Exception {
                       return new Tuple4<>(value.vehicle, value.highway, value.direction, value.position);
                    }
                })
                .window(SlidingProcessingTimeWindows.of(Time.seconds(30), Time.seconds(1)))
                .aggregate(new AggregateFunction<LRRecord, Long, Long>() {
                    @Override
                    public Long createAccumulator() {
                        return 0L;
                    }

                    @Override
                    public Long add(LRRecord value, Long accumulator) {
                        return accumulator + 1;
                    }

                    @Override
                    public Long getResult(Long accumulator) {
                        return accumulator;
                    }

                    @Override
                    public Long merge(Long a, Long b) {
                        return a + b;
                    }
                })
                .setMaxParallelism(maxParallelism)
                .name("WindowOperator")
                .addSink(new SinkFunction<Long>() {
                    @Override
                    public void invoke(Long value) throws Exception {

                    }
                });

        env.execute("LR2");

    }
}
