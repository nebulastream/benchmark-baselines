package de.tub.nebulastream.benchmarks.flink.smartgrid;

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

public class SGSource extends RichParallelSourceFunction<SGRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(SGRecord.class);
    public static final int RECORD_SIZE_IN_BYTE = 20;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    private transient ByteBuffer mbuff;

    public SGSource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        mbuff = ByteBuffer.allocate(RECORD_SIZE_IN_BYTE * numOfRecords);

        String fileName = "./src/main/resources/datasets/smartgrid/smartgrid-data.txt";
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
            ArrayList<String> words = lines.get(currentLineIndex);

            // creationTS
            mbuff.putLong(Long.parseLong(words.get(0)));
            // value
            mbuff.putFloat(Float.parseFloat(words.get(1)));
            // property
            mbuff.putShort(Short.parseShort(words.get(2)));
            // plug
            mbuff.putShort(Short.parseShort(words.get(3)));
            // household
            mbuff.putShort(Short.parseShort(words.get(4)));
            // house
            mbuff.putShort(Short.parseShort(words.get(5)));

            currentLineIndex++;
        }

    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<SGRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime*1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();
            mbuff.position(0);
            for (int i = 0; i < numOfRecords; i++) {
                SGRecord ysb = new SGRecord(
                        mbuff.getLong(),
                        mbuff.getFloat(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort()
                );
                ctx.collect(ysb); // filtering is possible also here but it d not be idiomatic
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