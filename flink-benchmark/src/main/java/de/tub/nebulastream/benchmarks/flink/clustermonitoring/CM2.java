package de.tub.nebulastream.benchmarks.flink.clustermonitoring;

import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import de.tub.nebulastream.benchmarks.flink.ysb.YSB;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CM2 {

    private static final Logger LOG = LoggerFactory.getLogger(YSB.class);

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        final long latencyTrackingInterval = params.getLong("latencyTrackingInterval", 0);
        final int parallelism = params.getInt("parallelism", 1);
        final int maxParallelism = params.getInt("maxParallelism", 16);
        final int numOfRecords = params.getInt("numOfRecords", 1_000_000);
        final int runtime = params.getInt("runtime", 10);
        LOG.info("Arguments: {}", params);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        env.setParallelism(parallelism);
        env.getConfig().enableObjectReuse();
        env.setMaxParallelism(maxParallelism);
        env.getConfig().setLatencyTrackingInterval(latencyTrackingInterval);

        DataStreamSource<CMRecord> source = env.addSource(new CMSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        source.flatMap(new ThroughputLogger<CMRecord>(CMSource.RECORD_SIZE_IN_BYTE, 1_000_000));

        source.filter(new FilterFunction<CMRecord>() {
                    @Override
                    public boolean filter(CMRecord cmRecord) throws Exception {
                        return cmRecord.eventType == 3;
                    }
                })
                .keyBy(new KeySelector<CMRecord, Long>() {
                    @Override
                    public Long getKey(CMRecord cmRecord) throws Exception {
                        return cmRecord.jobId;
                    }
                })
                .window(SlidingProcessingTimeWindows.of(Time.seconds(60), Time.seconds(1)))
                .aggregate(new AggregateFunction<CMRecord, Double, Double>() {
                    @Override
                    public Double createAccumulator() {
                        return 0.0;
                    }

                    @Override
                    public Double add(CMRecord cmRecord, Double aLong) {
                        return aLong + cmRecord.cpu;
                    }

                    @Override
                    public Double getResult(Double aLong) {
                        return aLong;
                    }

                    @Override
                    public Double merge(Double aLong, Double acc1) {
                        return aLong + acc1;
                    }
                })
                .setMaxParallelism(maxParallelism)
                .name("WindowOperator")
                .addSink(new SinkFunction<Double>() {
                    @Override
                    public void invoke(Double value) throws Exception {

                    }
                });

        env.execute("CM2");

    }
}
