
package de.tub.nebulastream.benchmarks.flink.nexmark;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;

public class NexmarkBidSource extends RichParallelSourceFunction<NEBidRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(NexmarkBidSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 28;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    String fileName = "./nes-datasets/bid_fafb6ed3648772eedd5c7c80acb2ad70.csv";

    public NexmarkBidSource(int runtime, int numOfRecords) {
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
    public void run(SourceContext<NEBidRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime * 1000) > System.currentTimeMillis()) {
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
                        NEBidRecord bid = new NEBidRecord(
                            // timestamp
                            Long.parseLong(words[0].trim()),
                            // auctionId
                            Integer.parseInt(words[1].trim()),
                            // bidder
                            Integer.parseInt(words[2].trim()),
                            // datetime
                            Long.parseLong(words[3].trim()),
                            // price
                            Float.parseFloat(words[4].trim())
                        );
                        ctx.collect(bid);
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