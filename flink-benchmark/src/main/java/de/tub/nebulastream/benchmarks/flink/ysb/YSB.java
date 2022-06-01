package de.tub.nebulastream.benchmarks.flink.ysb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.tub.nebulastream.benchmarks.flink.utils.AnalyzeTool;
import de.tub.nebulastream.benchmarks.flink.utils.ThroughputLogger;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
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

        LOG.info("Arguments: {}", params);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

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


        DataStreamSource<YSBRecord> source = env.addSource(new YSBSource(runtime, numOfRecords))
                .setParallelism(parallelism);

        source.flatMap(new ThroughputLogger<YSBRecord>(YSBSource.RECORD_SIZE_IN_BYTE, 1_000_000));

        source.flatMap(new Filter())
                .setParallelism(parallelism)
                .keyBy((KeySelector<YSBRecord.YSBFinalRecord, Long>) r -> r.campaign_id)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(1)))
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
            if (in.event_type == 2) { // wish for simd
                out.collect(new YSBRecord.YSBFinalRecord(in.campaign_id, (int) in.user_id));
            }
        }
    }

}
