package de.tub.nebulastream.benchmarks.flink.manufacturingequipment;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

public class MESource extends RichParallelSourceFunction<MERecord> {

    private static final Logger LOG = LoggerFactory.getLogger(MESource.class);
    public static final int RECORD_SIZE_IN_BYTE = 40;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    private transient ByteBuffer mbuff;

    public MESource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        String fileName = "./src/main/resources/datasets/manufacturing_equipment/DEBS2012-small.txt";
        ArrayList<ArrayList<String>> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.forEach(line -> {
                ArrayList<String> words = new ArrayList<>();
                for(String word: line.split("\\t")){
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
            mbuff.putLong(0);
            // messageIndex
            mbuff.putLong(Long.parseLong(words.get(1)));
            // mf01
            mbuff.putShort(Short.parseShort(words.get(2)));
            // mf02
            mbuff.putShort(Short.parseShort(words.get(3)));
            // mf03
            mbuff.putShort(Short.parseShort(words.get(4)));
            // pc13
            mbuff.putShort(Short.parseShort(words.get(5)));
            // pc14
            mbuff.putShort(Short.parseShort(words.get(6)));
            // pc15
            mbuff.putShort(Short.parseShort(words.get(7)));
            // pc25
            mbuff.putShort(Short.parseShort(words.get(8)));
            // pc26
            mbuff.putShort(Short.parseShort(words.get(9)));
            // pc27
            mbuff.putShort(Short.parseShort(words.get(10)));
            // res
            mbuff.putShort(Short.parseShort(words.get(11)));
            // bm05
            mbuff.putShort(Short.parseShort(words.get(12)));
            // bm06
            mbuff.putShort(Short.parseShort(words.get(13)));
            currentLineIndex++;
        }

    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<MERecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime * 1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();
            mbuff.position(0);
            for (int i = 0; i < numOfRecords; i++) {
                MERecord cm = new MERecord(
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
                        mbuff.getShort(),
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