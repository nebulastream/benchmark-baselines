package de.tub.nebulastream.benchmarks.flink.clustermonitoring;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;

public class CMSource extends RichParallelSourceFunction<CMRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(CMSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 54;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    String fileName = "./nes-datasets/google-cluster-data-lightsaber_7fd1cd1f53d49eef5ffe0c12c8c55ae8.csv";
    // String fileName = "./nes-datasets/google-cluster-data-original_1543213c4f95ade501aee5b931d92c44.csv";

    public CMSource(int runtime, int numOfRecords) {
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
    public void run(SourceContext<CMRecord> ctx) throws Exception {
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
                        CMRecord cm = new CMRecord(
                            // creationTS
                            Long.parseLong(words[0].trim()),
                            // jobId
                            Long.parseLong(words[1].trim()),
                            // taskId
                            Long.parseLong(words[2].trim()),
                            // machineId
                            Long.parseLong(words[3].trim()),
                            // eventType
                            Short.parseShort(words[4].trim()),
                            // userId
                            Short.parseShort(words[5].trim()),
                            // category
                            Short.parseShort(words[6].trim()),
                            // priority
                            Short.parseShort(words[7].trim()),
                            // cpu
                            Float.parseFloat(words[8].trim()),
                            // ram
                            Float.parseFloat(words[9].trim()),
                            // disk
                            Float.parseFloat(words[10].trim()),
                            // constraints
                            Short.parseShort(words[11].trim())
                        );
                        ctx.collect(cm);
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