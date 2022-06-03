
package de.tub.nebulastream.benchmarks.flink.nextmark;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

public class NextmarkAuctionSource extends RichParallelSourceFunction<NEAuctionRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(NextmarkAuctionSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 40;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    private transient ByteBuffer mbuff;

    private long minAuctionId;
    private long minPersonId;

    public NextmarkAuctionSource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        minAuctionId = NexmarkCommon.START_ID_AUCTION[getRuntimeContext().getIndexOfThisSubtask()];
        minPersonId = NexmarkCommon.START_ID_PERSON[getRuntimeContext().getIndexOfThisSubtask()];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        mbuff = ByteBuffer.allocate(16 * numOfRecords);
        int currentLineIndex = 0;
        for (int eventId = 0; eventId < numOfRecords; eventId++) {
            long epoch = eventId / NexmarkCommon.TOTAL_EVENT_RATIO;
            long offset = eventId % NexmarkCommon.TOTAL_EVENT_RATIO;
            if (offset < NexmarkCommon.PERSON_EVENT_RATIO) {
                epoch--;
                offset = NexmarkCommon.AUCTION_EVENT_RATIO - 1;
            } else {
                offset = NexmarkCommon.AUCTION_EVENT_RATIO - 1;
            }
            long auctionId = minAuctionId + epoch * NexmarkCommon.AUCTION_EVENT_RATIO + offset;//r.nextLong(minAuctionId, maxAuctionId);

            epoch = eventId / NexmarkCommon.TOTAL_EVENT_RATIO;
            offset = eventId % NexmarkCommon.TOTAL_EVENT_RATIO;

            if (offset >= NexmarkCommon.PERSON_EVENT_RATIO) {
                offset = NexmarkCommon.PERSON_EVENT_RATIO - 1;
            }
            long matchingPerson;
            if (r.nextInt(100) > 85) {
                long personId = epoch * NexmarkCommon.PERSON_EVENT_RATIO + offset;
                matchingPerson = minPersonId + (personId / NexmarkCommon.HOT_SELLER_RATIO) * NexmarkCommon.HOT_SELLER_RATIO;
            } else {
                long personId = epoch * NexmarkCommon.PERSON_EVENT_RATIO + offset + 1;
                long activePersons = Math.min(personId, 20_000);
                long n = r.nextLong(activePersons + 100);
                matchingPerson = minPersonId + personId + activePersons - n;
            }
            mbuff.putLong(Math.abs(auctionId));
            mbuff.putLong(Math.abs(matchingPerson));

        }

    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<NEAuctionRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime * 1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();
            mbuff.position(0);
            for (int i = 0; i < numOfRecords; i++) {
                NEAuctionRecord cm = new NEAuctionRecord(
                        mbuff.getLong(),
                        new char[16],
                        new char[16],
                        0,
                        0,
                        0,
                        mbuff.getLong(),
                        0, 0

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