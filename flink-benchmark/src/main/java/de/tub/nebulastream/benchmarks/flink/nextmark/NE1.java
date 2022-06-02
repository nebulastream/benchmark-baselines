package de.tub.nebulastream.benchmarks.flink.nextmark;

import de.tub.nebulastream.benchmarks.flink.manufacturingequipment.MERecord;
import de.tub.nebulastream.benchmarks.flink.manufacturingequipment.MESource;
import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import de.tub.nebulastream.benchmarks.flink.ysb.YSB;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.AllWindowedStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NE1 {

    private static final Logger LOG = LoggerFactory.getLogger(YSB.class);
    /**
     * SELECT itemid, DOLTOEUR(price),
     * bidderId, bidTime
     * FROM bid;
     */
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

        DataStreamSource<NEBidRecord> source = env.addSource(new NextmarkBidSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        source.flatMap(new ThroughputLogger<NEBidRecord>(MESource.RECORD_SIZE_IN_BYTE, 10_000));

        source
                .map(new MapFunction<NEBidRecord, Tuple4<Long, Long, Long, Long>>() {
                    @Override
                    public Tuple4<Long, Long, Long, Long> map(NEBidRecord record) throws Exception {
                        return new Tuple4<>(record.auctionId, (record.price * 89 / 100), record.bidderId, record.auctionId);
                    }
                }).project(0, 2)
                .addSink(new SinkFunction<Tuple>() {
                    @Override
                    public void invoke(Tuple value) throws Exception {

                    }
                });


        env.execute("NE1");

    }
}
