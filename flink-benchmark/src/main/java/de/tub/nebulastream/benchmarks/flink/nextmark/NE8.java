package de.tub.nebulastream.benchmarks.flink.nextmark;

import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatJoinFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NE8 {

    private static final Logger LOG = LoggerFactory.getLogger(NE8.class);

    /**
     * SELECT Rstream(P.id, P.name, A.reserve)
     * FROM Person [RANGE 12 HOUR] P, Auction [RANGE 12 HOUR] A
     * WHERE P.id = A.seller;
     */
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

        DataStreamSource<NEAuctionRecord> auctions = env.addSource(new NextmarkAuctionSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        auctions.flatMap(new ThroughputLogger<NEAuctionRecord>(NextmarkAuctionSource.RECORD_SIZE_IN_BYTE, 10_000));

        DataStreamSource<NEBidRecord> persons = env.addSource(new NextmarkBidSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        persons.flatMap(new ThroughputLogger<NEBidRecord>(NextmarkBidSource.RECORD_SIZE_IN_BYTE, 10_000));


        auctions.join(persons).where(new KeySelector<NEAuctionRecord, Integer>() {
                    @Override
                    public Integer getKey(NEAuctionRecord value) throws Exception {
                        return value.seller;
                    }
                }).equalTo(new KeySelector<NEBidRecord, Integer>() {
                    @Override
                    public Integer getKey(NEBidRecord value) throws Exception {
                        return value.auctionId;
                    }
                }).window(TumblingProcessingTimeWindows.of(Time.seconds(10))).apply(new FlatJoinFunction<NEAuctionRecord, NEBidRecord, Tuple2<Integer, Integer>>() {
                    @Override
                    public void join(NEAuctionRecord first, NEBidRecord second, Collector<Tuple2<Integer, Integer>> out) throws Exception {
                        out.collect(new Tuple2<>(first.id, second.auctionId));
                    }
                })
                .addSink(new SinkFunction<Tuple2<Integer, Integer>>() {
                    @Override
                    public void invoke(Tuple2<Integer, Integer> value, Context context) throws Exception {

                    }
                });


        env.execute("NE8");

    }
}
