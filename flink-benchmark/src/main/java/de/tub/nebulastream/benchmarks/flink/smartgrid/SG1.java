package de.tub.nebulastream.benchmarks.flink.smartgrid;

import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import de.tub.nebulastream.benchmarks.flink.ysb.YSB;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SG1 {

    private static final Logger LOG = LoggerFactory.getLogger(YSB.class);

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        final long latencyTrackingInterval = params.getLong("latencyTrackingInterval", 0);
        final int parallelism = params.getInt("parallelism", 1);
        final int maxParallelism = params.getInt("maxParallelism", 16);
        final int numOfRecords = params.getInt("numOfRecords", 10_000);
        final int runtime = params.getInt("runtime", 10);

        LOG.info("Arguments: {}", params);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        env.setParallelism(parallelism);
        env.getConfig().enableObjectReuse();
        env.setMaxParallelism(maxParallelism);
        env.getConfig().setLatencyTrackingInterval(latencyTrackingInterval);

        DataStreamSource<SGRecord> source = env.addSource(new SGSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        source.flatMap(new ThroughputLogger<SGRecord>(SGSource.RECORD_SIZE_IN_BYTE, 10_000));

        source

                .windowAll(SlidingProcessingTimeWindows.of(Time.seconds(3600), Time.seconds(1)))
                .aggregate(new AggregateFunction<SGRecord, Tuple2<Long, Double>, Double>() {
                    @Override
                    public Tuple2<Long, Double> createAccumulator() {
                        return new Tuple2<>(0L, 0.0);
                    }

                    @Override
                    public Tuple2<Long, Double> add(SGRecord value, Tuple2<Long, Double> accumulator) {
                        return new Tuple2<>(accumulator.f0 + 1, accumulator.f1 + value.value);
                    }

                    @Override
                    public Double getResult(Tuple2<Long, Double> accumulator) {
                        return accumulator.f1 / accumulator.f0;
                    }

                    @Override
                    public Tuple2<Long, Double> merge(Tuple2<Long, Double> a, Tuple2<Long, Double> b) {
                        return new Tuple2<>(a.f0 + b.f0, a.f1 + b.f1);
                    }
                })
                .name("WindowOperator")
                .addSink(new SinkFunction<Double>() {
                    @Override
                    public void invoke(Double value) throws Exception {

                    }
                });

        env.execute("SG1");

    }
}
