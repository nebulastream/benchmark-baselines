package de.tub.nebulastream.benchmarks.flink.nextmark;

public class NEAuctionRecord {

    public long timestamp;
    public int id;
    public double initialBid;
    public int reserve;
    public long expires;
    public int seller;
    public int category;

    public NEAuctionRecord() {
    }

    public NEAuctionRecord(long timestamp, int id, int initialBid, int reserve, long expires, int seller, int category) {
        this.timestamp = timestamp;
        this.id = id;
        this.initialBid = initialBid;
        this.reserve = reserve;
        this.expires = expires;
        this.seller = seller;
        this.category = category;
    }
}
