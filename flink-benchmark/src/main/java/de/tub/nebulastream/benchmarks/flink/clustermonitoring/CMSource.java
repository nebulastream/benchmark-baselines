package de.tub.nebulastream.benchmarks.flink.clustermonitoring;

import de.tub.nebulastream.benchmarks.flink.ysb.YSBRecord;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class CMSource extends RichParallelSourceFunction<CMRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(CMSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 52;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    private transient ByteBuffer mbuff;

    public CMSource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        String fileName = "./src/main/resources/datasets/google-cluster-data/google-cluster-data.txt";
        ArrayList<ArrayList<String>> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.forEach(line -> {
                ArrayList<String> words = new ArrayList<>();
                for(String word: line.split("\\W+")){
                    words.add(word.trim());
                }
                lines.add(words);
            });
        }

        mbuff = ByteBuffer.allocate(RECORD_SIZE_IN_BYTE * numOfRecords);
        int currentLineIndex = 0;
        for (int i = 0; i < numOfRecords; i++) {
            // check if we reached the end of the file and start from the beginning
            if (currentLineIndex >= lines.size()) {
                currentLineIndex = 0;
            }
            ArrayList<String> words = lines.get(0);
            System.out.println(words);
            // creationTS
            mbuff.putLong(Long.parseLong(words.get(0)));
            // jobId
            mbuff.putLong(Long.parseLong(words.get(1)));
            // taskId
            mbuff.putLong(Long.parseLong(words.get(2)));
            // machineId
            mbuff.putLong(Long.parseLong(words.get(3)));
            // eventType
            mbuff.putShort(Short.parseShort(words.get(4)));
            // category
            mbuff.putShort(Short.parseShort(words.get(5)));
            // priority
            mbuff.putShort(Short.parseShort(words.get(6)));
            // cpu
            mbuff.putFloat(Float.parseFloat(words.get(7)));
            // ram
            mbuff.putFloat(Float.parseFloat(words.get(8)));
            // disk
            mbuff.putFloat(Float.parseFloat(words.get(9)));
            // constraints
            mbuff.putShort(Short.parseShort(words.get(10)));
            currentLineIndex++;
        }

    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<CMRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime * 1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();
            mbuff.position(0);
            for (int i = 0; i < numOfRecords; i++) {
                CMRecord cm = new CMRecord(
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getFloat(),
                        mbuff.getFloat(),
                        mbuff.getFloat(),
                        mbuff.getShort()
                );
                ctx.collect(cm); // filtering is possible also here but it d not be idiomatic
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