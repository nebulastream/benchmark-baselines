package de.tub.nebulastream.benchmarks.flink.nexmark;

import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import org.apache.flink.api.common.functions.FlatJoinFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NE5 {

    private static final Logger LOG = LoggerFactory.getLogger(NE5.class);

    /**
     * SELECT start, end, start, end, auctionId, num, start, end, max_tmp
        FROM (SELECT auctionId, COUNT(auctionId) AS num, start, end
            FROM bid
            GROUP BY auctionId
            WINDOW SLIDING(timestamp, SIZE 10 SEC, ADVANCE BY 2 SEC))
        INNER JOIN (SELECT auctionId, MAX(num_ids) AS max_tmp, start, end
                    FROM
                            (SELECT auctionId, COUNT(auctionId) AS num_ids, start
                            FROM bid
                            GROUP BY auctionId
                            WINDOW SLIDING(timestamp, SIZE 10 SEC, ADVANCE BY 2 SEC))
                    WINDOW TUMBLING(start, SIZE 2 SEC))
        ON num >= max_tmp
        WINDOW TUMBLING(start, SIZE 2 SEC)
        INTO CHECKSUM;
     */
    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        final long latencyTrackingInterval = params.getLong("latencyTrackingInterval", 0);
        final int parallelism = params.getInt("parallelism", 1);
        final int maxParallelism = params.getInt("maxParallelism", 16);
        final int numOfRecords = params.getInt("numOfRecords", 10_000_000);
        final int runtime = params.getInt("runtime", 10);

        LOG.info("Arguments: {}", params);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        env.setParallelism(parallelism);
        env.getConfig().enableObjectReuse();
        env.setMaxParallelism(maxParallelism);
        env.getConfig().setLatencyTrackingInterval(latencyTrackingInterval);

        DataStreamSource<NEBidRecord> source = env.addSource(new NexmarkBidSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        source.flatMap(new ThroughputLogger<NEBidRecord>(NexmarkBidSource.RECORD_SIZE_IN_BYTE, 1_000_000));

        // auctionId, num, start, end
        DataStream<Tuple4<Integer, Integer, Long, Long>> countStream =
            source.keyBy(new KeySelector<NEBidRecord, Integer>() {
                @Override
                public Integer getKey(NEBidRecord rec) throws Exception {
                    return rec.auctionId;
                }
            })
            .window(SlidingProcessingTimeWindows.of(Time.seconds(10), Time.seconds(2))) // WINDOW SLIDING(timestamp, SIZE 10 SEC, ADVANCE BY 2 SEC)
            .process(new ProcessWindowFunction<NEBidRecord, Tuple4<Integer, Integer, Long, Long>, Integer, TimeWindow>() {
                @Override
                public void process(Integer key, Context context, Iterable<NEBidRecord> recs, Collector<Tuple4<Integer, Integer, Long, Long>> out) {
                    int count = 0;
                    for (NEBidRecord bid : recs) {
                        count++;
                    }
                    out.collect(new Tuple4<>(key, count, context.window().getStart(), context.window().getEnd()));
                }
            });

            
        // auctionId, max_tmp, start, end
        DataStream<Tuple4<Integer, Integer, Long, Long>> maxStream =
            countStream.keyBy(new KeySelector<Tuple4<Integer, Integer, Long, Long>, Integer>() {
                @Override
                public Integer getKey(Tuple4<Integer, Integer, Long, Long> countRec) throws Exception {
                    return countRec.f0; // auctionId
                }
            })
            .window(TumblingProcessingTimeWindows.of(Time.seconds(2))) // WINDOW TUMBLING(start, SIZE 2 SEC)
            .process(new ProcessWindowFunction<Tuple4<Integer, Integer, Long, Long>, Tuple4<Integer, Integer, Long, Long>, Integer, TimeWindow>() {
                @Override
                public void process(Integer key, Context context, Iterable<Tuple4<Integer, Integer, Long, Long>> recs, Collector<Tuple4<Integer, Integer, Long, Long>> out) {
                    // Find max count
                    int maxCount = 0;
                    for (Tuple4<Integer, Integer, Long, Long> countRec : recs) {
                        if (countRec.f2 == context.window().getStart() && countRec.f1 >= maxCount) {
                            maxCount = countRec.f1; // num
                        }
                    }
                    out.collect(new Tuple4<>(key, maxCount, context.window().getStart(), context.window().getEnd()));
                }
            });

        countStream.join(maxStream)
            .where(new KeySelector<Tuple4<Integer, Integer, Long, Long>, Integer>() {
                @Override
                public Integer getKey(Tuple4<Integer, Integer, Long, Long> countRec) throws Exception {
                    return countRec.f0;
                }
            })
            .equalTo(new KeySelector<Tuple4<Integer, Integer, Long, Long>, Integer>() {
                @Override
                public Integer getKey(Tuple4<Integer, Integer, Long, Long> maxRec) throws Exception {
                    return maxRec.f0;
                }
            }) // join on auctionId
            .window(TumblingProcessingTimeWindows.of(Time.seconds(2)))
            .apply(new FlatJoinFunction<Tuple4<Integer, Integer, Long, Long>, Tuple4<Integer, Integer, Long, Long>, Tuple5<Integer, Integer, Long, Long, Integer>>() {
                @Override
                public void join(Tuple4<Integer, Integer, Long, Long> countRec, Tuple4<Integer, Integer, Long, Long> maxRec, Collector<Tuple5<Integer, Integer, Long, Long, Integer>> out) throws Exception {
                    if (countRec.f2 == maxRec.f2 && countRec.f1 >= maxRec.f1) { // num >= max_tmp
                        out.collect(new Tuple5<>(countRec.f0, countRec.f1, countRec.f2, countRec.f3, maxRec.f1)); // auctionId, num, start, end, max_tmp
                    }
                }
            })
            .addSink(new SinkFunction<Tuple5<Integer, Integer, Long, Long, Integer>>() {
                @Override
                public void invoke(Tuple5<Integer, Integer, Long, Long, Integer> value, Context context) throws Exception {

                }
            });

        env.execute("NE5");

    }
}
