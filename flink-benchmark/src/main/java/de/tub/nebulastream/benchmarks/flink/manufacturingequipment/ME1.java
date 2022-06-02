package de.tub.nebulastream.benchmarks.flink.manufacturingequipment;

import de.tub.nebulastream.benchmarks.flink.linearroad.LRRecord;
import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import de.tub.nebulastream.benchmarks.flink.ysb.YSB;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.AllWindowedStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ME1 {

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

        DataStreamSource<MERecord> source = env.addSource(new MESource(runtime, numOfRecords))
                .setParallelism(parallelism);

        source.flatMap(new ThroughputLogger<MERecord>(MESource.RECORD_SIZE_IN_BYTE, 10_000));

        AllWindowedStream<MERecord, TimeWindow> windowedStream = source
                .windowAll(SlidingProcessingTimeWindows.of(Time.seconds(60), Time.seconds(1)));

        SingleOutputStreamOperator<Double> avg_mf01 = windowedStream.aggregate(new AggregateFunction<MERecord, Tuple2<Long, Double>, Double>() {
            @Override
            public Tuple2<Long, Double> createAccumulator() {
                return new Tuple2<>(0L, 0.0);
            }

            @Override
            public Tuple2<Long, Double> add(MERecord value, Tuple2<Long, Double> accumulator) {
                return new Tuple2<>(accumulator.f0 + 1, accumulator.f1 + value.mf01);
            }

            @Override
            public Double getResult(Tuple2<Long, Double> accumulator) {
                return accumulator.f1 / accumulator.f0;
            }

            @Override
            public Tuple2<Long, Double> merge(Tuple2<Long, Double> a, Tuple2<Long, Double> b) {
                return new Tuple2<>(a.f0 + b.f0, a.f1 + b.f1);
            }
        });

        SingleOutputStreamOperator<Double> avg_mf02 = windowedStream.aggregate(new AggregateFunction<MERecord, Tuple2<Long, Double>, Double>() {
            @Override
            public Tuple2<Long, Double> createAccumulator() {
                return new Tuple2<>(0L, 0.0);
            }

            @Override
            public Tuple2<Long, Double> add(MERecord value, Tuple2<Long, Double> accumulator) {
                return new Tuple2<>(accumulator.f0 + 1, accumulator.f1 + value.mf02);
            }

            @Override
            public Double getResult(Tuple2<Long, Double> accumulator) {
                return accumulator.f1 / accumulator.f0;
            }

            @Override
            public Tuple2<Long, Double> merge(Tuple2<Long, Double> a, Tuple2<Long, Double> b) {
                return new Tuple2<>(a.f0 + b.f0, a.f1 + b.f1);
            }
        });

        SingleOutputStreamOperator<Double> avg_mf03 = windowedStream.aggregate(new AggregateFunction<MERecord, Tuple2<Long, Double>, Double>() {
            @Override
            public Tuple2<Long, Double> createAccumulator() {
                return new Tuple2<>(0L, 0.0);
            }

            @Override
            public Tuple2<Long, Double> add(MERecord value, Tuple2<Long, Double> accumulator) {
                return new Tuple2<>(accumulator.f0 + 1, accumulator.f1 + value.mf03);
            }

            @Override
            public Double getResult(Tuple2<Long, Double> accumulator) {
                return accumulator.f1 / accumulator.f0;
            }

            @Override
            public Tuple2<Long, Double> merge(Tuple2<Long, Double> a, Tuple2<Long, Double> b) {
                return new Tuple2<>(a.f0 + b.f0, a.f1 + b.f1);
            }
        });

        avg_mf01.union(avg_mf02, avg_mf03)
                .addSink(new SinkFunction<Double>() {
                    @Override
                    public void invoke(Double value) throws Exception {

                    }
                }).setParallelism(parallelism);

        env.execute("ME1");

    }
}
