package serialization;

public class NexmarkBidRecord {
    private final static int BID_RECORD_SIZE = 8 * 6;
    private final static int BID_RECORD_INGESTION_SIZE = 8 * 4;

    public long ingestionTimestamp;
    public long timestamp;
    public long auctionId;
    public long personId;
    public long bidId;
    public double bid;


    public NexmarkBidRecord(long ingestionTimestamp, long timestamp, long auctionId, long personId, long bidId, double bid) {
        this.ingestionTimestamp = ingestionTimestamp;
        this.timestamp = timestamp;
        this.auctionId = auctionId;
        this.personId = personId;
        this.bidId = bidId;
        this.bid = bid;
    }
    public NexmarkBidRecord(long auctionId) {
        this.ingestionTimestamp = -1;
        this.timestamp = -1;
        this.auctionId = auctionId;
        this.personId = -1;
        this.bidId = -1;
        this.bid = -1;
    }
    public static int getSize() {
        return BID_RECORD_SIZE;
    }
    public static int getIngestionSize() {
        return BID_RECORD_INGESTION_SIZE;
    }
}
