import avro.shaded.com.google.common.collect.ImmutableMap;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.Read;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.MetricNameFilter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.metrics.MetricsFilter;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.state.StateSpec;
import org.apache.beam.sdk.state.StateSpecs;
import org.apache.beam.sdk.state.ValueState;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.POutput;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.ByteBuffer;


public class BeamBenchmarkRunner {

    public static class YSBRecord implements Serializable {
        public long user_id;
        public long page_id;
        public long campaign_id;
        public long event_type;
        public long ip;
        public long d1;
        public long d2;
        public int d4;
        public short d3;

        public YSBRecord() {
        }

        public YSBRecord(long user_id, long page_id, long campaign_id, long event_type, long ip, long d1, long d2, int d4, short d3) {
            this.user_id = user_id;
            this.page_id = page_id;
            this.campaign_id = campaign_id;
            this.event_type = event_type;
            this.ip = ip;
            this.d1 = d1;
            this.d2 = d2;
            this.d4 = d4;
            this.d3 = d3;
        }
    }

    static final long throughputTick = 100000L;

    private static final Logger LOG = LoggerFactory.getLogger(BeamBenchmarkRunner.class);

    public static void main(String[] args) throws InterruptedException {

        PipelineOptions options = PipelineOptionsFactory.fromArgs(args).withValidation().create();

        Pipeline p = Pipeline.create(options);

        System.out.println("Running Pipeline\n " + p.getOptions());
        LOG.error("Start");
        Counter counter = Metrics.counter("namespace", "throughput");

        var data = p.apply(KafkaIO.<Long, byte[]>read()
                .withBootstrapServers("10.156.0.30:9092")
                .withTopic("nesKafka")  // use withTopics(List<String>) to read from multiple topics.
                .withKeyDeserializer(LongDeserializer.class)
                .withValueDeserializer(ByteArrayDeserializer.class)

                .withConsumerConfigUpdates(
                        new ImmutableMap.Builder<String, Object>()
                                .put(ConsumerConfig.GROUP_ID_CONFIG, "my_group")
                                .put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest").build())

                // above four are required configuration. returns PCollection<KafkaRecord<Long, String>>
                // finally, if you don't need Kafka metadata, you can drop it
                .withoutMetadata() // PCollection<KV<Long, String>>

        );
        var mapedData = data.apply(ParDo.of(new DoFn<KV<Long, byte[]>, KV<Long, YSBRecord>>() {
            @ProcessElement
            public void processElement(@Element KV<Long, byte[]> record, OutputReceiver<KV<Long, YSBRecord>> r) {
                var mbuff = ByteBuffer.wrap(record.getValue());
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
                    r.output(KV.of(ysb.campaign_id, ysb));
                }
            }
        }));

        mapedData.apply(ParDo.of(new DoFn<KV<Long, YSBRecord>, KV<Long, YSBRecord>>() {
                    @ProcessElement
                    public void processElement(@Element KV<Long, YSBRecord> record, OutputReceiver<KV<Long, YSBRecord>> r) {
                        if (record.getValue().event_type <1)
                            r.output(record);
                    }
                })).apply(Window.into(FixedWindows.of(Duration.standardSeconds(30))))
                .apply(Count.perKey());

        data.apply(ParDo.of(new DoFn<KV<Long, byte[]>, String>() {
                                @StateId("count")
                                private final StateSpec<ValueState<Integer>> countState = StateSpecs.value();

                                @StateId("ts")
                                private final StateSpec<ValueState<Long>> ts = StateSpecs.value();

                                @ProcessElement
                                public void process(
                                        ProcessContext context,
                                        @StateId("count") ValueState<Integer> countState,
                                        @StateId("ts") ValueState<Long> ts) {


                                    var value = countState.read();
                                    if (value == null) {
                                        value = 0;
                                        ts.write(System.currentTimeMillis());
                                    }

                                    int count = value;
                                    count = count + 1680;

                                    var endTs = System.currentTimeMillis();
                                    var diff = endTs - ts.read();

                                    if (diff >= 1000) {
                                        var tp = (count / ((double) diff)) * 1000.0;
                                        LOG.error("currentTs ,\t" + endTs + ",\tElements,\t" + count + ",\tDiff,\t" + diff + ",\tthroughput,\t" + tp);
                                        //context.output("currentTs ,\t" + endTs + ",\tElements,\t" + count + ",\tDiff,\t" + diff + ",\tthroughput,\t" + tp);
                                        ts.write(System.currentTimeMillis());
                                        countState.write(0);
                                    } else {
                                        countState.write(count);
                                    }

                                }
                            }
        ));
                /*.apply(KafkaIO.<Void, String>write()
                .withBootstrapServers("10.156.0.30:9092")
                .withTopic("results")
                .withValueSerializer(StringSerializer.class)
                .values()
        );*/


/*
        var result = data.apply(Filter.by(input -> {
                    counter.inc();
                    return input.event_type() == 1;
                }))
                .apply(ParDo.of(new DoFn<YSBRecord, KV<Long, Long>>() {
                    @ProcessElement
                    public void processElement(@Element YSBRecord record, OutputReceiver<KV<Long, Long>> out) {
                        var entry = KV.of(record.campaign_id(), record.d1());
                        out.output(entry);
                    }
                }))
                .apply(Window.into(FixedWindows.of(Duration.standardSeconds(1))))
                .apply(Sum.longsPerKey());

        result.apply(ParDo.of(new DoFn<KV<Long, Long>, Long>() {
            @ProcessElement
            public void processElement(@Element KV<Long, Long> record, OutputReceiver<Long> out) {
                System.out.println(record);
            }
        }));

*/
        var metricFilter = MetricsFilter.builder()
                .addNameFilter(MetricNameFilter.named("namespace", "throughput"))
                .build();

        var run = p.run();
        // var metricFilter = MetricsFilter.builder()
        //         .addNameFilter(MetricNameFilter.named("namespace", "counter1"))
        //         .build();
        //for (int i = 0; i < 520; i++) {
        //    Thread.sleep(1000);
        //   System.out.println(run.metrics().queryMetrics(metricFilter).getCounters());
        //}

     //   run.waitUntilFinish(Duration.standardSeconds(600));

    }
}
