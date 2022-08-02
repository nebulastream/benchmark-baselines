package de.tub.nebulastream.benchmarks.flink.ysb;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class YSBSource extends RichParallelSourceFunction<YSBRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(YSBSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 78;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    private transient ByteBuffer mbuff;

    public YSBSource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }



    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        mbuff = ByteBuffer.allocate(RECORD_SIZE_IN_BYTE * numOfRecords);

        for (int i = 0; i < numOfRecords; i++) {
            mbuff.putLong(1);
            mbuff.putLong(0);
            int campaign_id = ThreadLocalRandom.current().nextInt(0, 1000 + 1);
            mbuff.putLong(campaign_id);
            int event_type = ThreadLocalRandom.current().nextInt(0, 3);
            mbuff.putLong(event_type);
            mbuff.putLong(0x01020304);
            mbuff.putLong(1);
            mbuff.putLong(1);
            mbuff.putInt(1);
            mbuff.putShort((short) 1);
        }

    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<YSBRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime*1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();
            mbuff.position(0);
            for (int i = 0; i < numOfRecords; i++) {
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