
package de.tub.nebulastream.benchmarks.flink.nextmark;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;

public class NextmarkAuctionSource extends RichParallelSourceFunction<NEAuctionRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(NextmarkAuctionSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 36;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    String fileName = "./nes-datasets/auction_modified_a4a6f973820d43ca27c8d92fc58e1091.csv";

    public NextmarkAuctionSource(int runtime, int numOfRecords) {
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
    public void run(SourceContext<NEAuctionRecord> ctx) throws Exception {
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
                        NEAuctionRecord auction = new NEAuctionRecord(
                            // timestamp
                            Long.parseLong(words[0].trim()),
                            // id
                            Integer.parseInt(words[1].trim()),
                            // initialBid
                            Integer.parseInt(words[2].trim()),
                            // reserve
                            Integer.parseInt(words[3].trim()),
                            // expires
                            Long.parseLong(words[4].trim()),
                            // seller
                            Integer.parseInt(words[5].trim()),
                            // category
                            Integer.parseInt(words[6].trim())
                        );
                        ctx.collect(auction);
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