package de.tub.nebulastream.benchmarks.flink.nextmark;

public class NEAuctionRecord {

    public long id;
    public char[] itemName;
    public char[] description;
    public double initialBit;
    public long reserve;
    public long dateTime;
    public long seller;
    public long expires;
    public long category;

    public NEAuctionRecord() {
    }

    public NEAuctionRecord(long id, char[] itemName, char[] description, double initialBit, long reserve, long dateTime, long seller, long expires, long category) {
        this.id = id;
        this.itemName = itemName;
        this.description = description;
        this.initialBit = initialBit;
        this.reserve = reserve;
        this.dateTime = dateTime;
        this.seller = seller;
        this.expires = expires;
        this.category = category;
    }
}
