package de.tub.nebulastream.benchmarks.flink.nexmark;

public class NEBidRecord {
    public long timestamp;
    public int auctionId;
    public int bidder;
    public long datetime;
    public float price;

    public NEBidRecord() {}


    public NEBidRecord(long timestamp, int auctionId, int bidder, long datetime, float price) {
        this.timestamp = timestamp;
        this.auctionId = auctionId;
        this.bidder = bidder;
        this.datetime = datetime;
        this.price = price;
    }
}
