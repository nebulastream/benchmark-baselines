
package de.tub.nebulastream.benchmarks.flink.nextmark;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

public class NextmarkPersonSource extends RichParallelSourceFunction<NEPersonRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(NextmarkPersonSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 40;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    private transient ByteBuffer mbuff;

    private long minAuctionId;
    private long minPersonId;

    public NextmarkPersonSource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        minAuctionId = NexmarkCommon.START_ID_AUCTION[getRuntimeContext().getIndexOfThisSubtask()];
        minPersonId = NexmarkCommon.START_ID_PERSON[getRuntimeContext().getIndexOfThisSubtask()];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        mbuff = ByteBuffer.allocate(8 * numOfRecords);
        int currentLineIndex = 0;
        for (int eventId = 0; eventId < numOfRecords; eventId++) {
            long epoch = eventId / NexmarkCommon.TOTAL_EVENT_RATIO;
            long offset = eventId % NexmarkCommon.TOTAL_EVENT_RATIO;
            if (offset >= NexmarkCommon.PERSON_EVENT_RATIO) {
                offset = NexmarkCommon.PERSON_EVENT_RATIO - 1;
            }
            long personId = minPersonId + epoch * NexmarkCommon.PERSON_EVENT_RATIO + offset;

            mbuff.putLong(Math.abs(personId));

        }

    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<NEPersonRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime * 1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();
            mbuff.position(0);
            for (int i = 0; i < numOfRecords; i++) {
                NEPersonRecord cm = new NEPersonRecord(
                        mbuff.getLong(),
                        new char[16],
                        new char[16],
                        new char[16],
                        new char[16],
                        new char[16]
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