
package de.tub.nebulastream.benchmarks.flink.nextmark;

import de.tub.nebulastream.benchmarks.flink.manufacturingequipment.MERecord;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class NextmarkBidSource extends RichParallelSourceFunction<NEBidRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(NextmarkBidSource.class);
    public static final int RECORD_SIZE_IN_BYTE = 40;

    private volatile boolean running = true;

    private final int numOfRecords;
    private final int runtime;

    private transient ByteBuffer mbuff;

    private long minAuctionId;
    private long minPersonId;

    public NextmarkBidSource(int runtime, int numOfRecords) {
        this.numOfRecords = numOfRecords;
        this.runtime = runtime;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        minAuctionId = NexmarkCommon.START_ID_AUCTION[getRuntimeContext().getIndexOfThisSubtask()];
        minPersonId = NexmarkCommon.START_ID_PERSON[getRuntimeContext().getIndexOfThisSubtask()];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        mbuff = ByteBuffer.allocate(RECORD_SIZE_IN_BYTE * numOfRecords);
        int currentLineIndex = 0;
        for (int eventId = 0; eventId < numOfRecords; eventId++) {
            long auction, bidder;

            long epoch = eventId / NexmarkCommon.TOTAL_EVENT_RATIO;
            long offset = eventId % NexmarkCommon.TOTAL_EVENT_RATIO;

            if (r.nextInt(100) > NexmarkCommon.HOT_AUCTIONS_PROB) {
                auction = minAuctionId + (((epoch * NexmarkCommon.AUCTION_EVENT_RATIO + NexmarkCommon.AUCTION_EVENT_RATIO - 1) / NexmarkCommon.HOT_AUCTION_RATIO) * NexmarkCommon.HOT_AUCTION_RATIO);
            } else {
                long a = Math.max(0, epoch * NexmarkCommon.AUCTION_EVENT_RATIO + NexmarkCommon.AUCTION_EVENT_RATIO - 1 - 20_000);
                long b = epoch * NexmarkCommon.AUCTION_EVENT_RATIO + NexmarkCommon.AUCTION_EVENT_RATIO - 1;
                auction = minAuctionId + a + r.nextLong(b - a + 1 + 100);
            }

            if (r.nextInt(100) > 85) {
                long personId = epoch * NexmarkCommon.PERSON_EVENT_RATIO + NexmarkCommon.PERSON_EVENT_RATIO - 1;
                bidder = minPersonId + (personId / NexmarkCommon.HOT_SELLER_RATIO) * NexmarkCommon.HOT_SELLER_RATIO;
            } else {
                long personId = epoch * NexmarkCommon.PERSON_EVENT_RATIO + NexmarkCommon.PERSON_EVENT_RATIO - 1;
                long activePersons = Math.min(personId, 60_000);
                long n = r.nextLong(activePersons + 100);
                bidder = minPersonId + personId + activePersons - n;
            }

            long ts = System.currentTimeMillis();
            mbuff.putLong(ts);
            mbuff.putLong(ts);
            mbuff.putLong(Math.abs(auction));
            mbuff.putLong(Math.abs(bidder));
            mbuff.putLong(-1);
            mbuff.putDouble(r.nextDouble(10_000_000));
        }

    }


    @Override
    public void close() throws Exception {
    }

    @Override
    public void run(SourceContext<NEBidRecord> ctx) throws Exception {
        long sourceStartTs = System.currentTimeMillis();
        while (sourceStartTs + (runtime * 1000) > System.currentTimeMillis()) {
            long emitStartTime = System.currentTimeMillis();
            mbuff.position(0);
            for (int i = 0; i < numOfRecords; i++) {
                NEBidRecord cm = new NEBidRecord(
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getLong(),
                        mbuff.getDouble()

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