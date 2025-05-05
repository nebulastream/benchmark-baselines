package de.tub.nebulastream.benchmarks.flink.ysb;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;

public class YSBSource extends RichParallelSourceFunction<YSBRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(YSBSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 80;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    String fileName = "./nes-datasets/ysb_10k_data_4e56e866e3d7ae6ed3ba43ef7c9450c8.csv";
    // String fileName = "./nes-datasets/ysb_1k_data_5efe90254a4107665cb57224e9965787.csv";

    public YSBSource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }



    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<YSBRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime*1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();

            int totalLines = 0;
            // Read until numOfRecords is reached
            while (totalLines < numOfRecords) {
                // Read directly from the file, one line at a time
                int bufferSize = RECORD_SIZE_IN_BYTE + 10;
                try (BufferedReader reader = new BufferedReader(new FileReader(fileName), bufferSize)) {
                    String line;
                    while ((line = reader.readLine()) != null && totalLines < numOfRecords) {
                        String[] words = line.split("\\W+");
                        YSBRecord ysb = new YSBRecord(
                            // user_id
                            Long.parseLong(words[0].trim()),
                            // page_id
                            Long.parseLong(words[1].trim()),
                            // campaign_id
                            Long.parseLong(words[2].trim()),
                            // ad_type
                            Long.parseLong(words[3].trim()),
                            // event_type
                            Long.parseLong(words[4].trim()),
                            // current_ms
                            Long.parseLong(words[5].trim()),
                            // ip
                            Long.parseLong(words[6].trim()),
                            // d1
                            Long.parseUnsignedLong(words[7].trim()),
                            // d2
                            Long.parseUnsignedLong(words[8].trim()),
                            // d3
                            Integer.parseUnsignedInt(words[9].trim()),
                            // d4
                            Integer.parseInt(words[10].trim())
                        );
                        ctx.collect(ysb);
                        totalLines++;
                    }
                }
            }

            // Sleep for the rest of timeslice if needed
            long emitTime = System.currentTimeMillis() - emitStartTime;
            if (emitTime < 100) {
                Thread.sleep(100 - emitTime);
            }
        }
        ctx.close();
    }

    /**
     * Given a desired load figure out how many elements to generate in each timeslice
     * before yielding for the rest of that timeslice
     */
    private int loadPerTimeslice() {
        int messagesPerOperator = numOfRecords / getRuntimeContext().getNumberOfParallelSubtasks();
        return messagesPerOperator / (1000 / 100);
    }


    @Override
    public void cancel() {
        running = false;
    }
}