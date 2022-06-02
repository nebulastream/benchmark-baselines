package de.tub.nebulastream.benchmarks.flink.nextmark;

public class NEBidRecord {
    public long timestamp;
    public long auctionId;
    public long bidderId;
    public double price;

    public NEBidRecord() {
    }

    public NEBidRecord(long timestamp, long auctionId, long bidderId, double price) {
        this.timestamp = timestamp;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.price = price;
    }
}
