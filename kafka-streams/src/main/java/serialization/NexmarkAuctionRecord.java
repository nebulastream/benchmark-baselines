package serialization;

public class NexmarkAuctionRecord {
    private final static int AUCTION_RECORD_SIZE = 269;
    private final static int AUCTION_RECORD_INGESTION_SIZE = 269; // todo

    public long auctionId;
    public long personId;
    public byte c; // todo what is this value?
    public int itemId;
    public long start;
    public long end;
    public int price;
    public String name;
    public String desc;
    public long timestamp;


    public NexmarkAuctionRecord(long auctionId, long personId, byte c, int itemId, long start, long end, int price, String name, String desc, long timestamp) {
        this.auctionId = auctionId;
        this.personId = personId;
        this.c = c;
        this.itemId = itemId;
        this.start = start;
        this.end = end;
        this.price = price;
        this.name = name;
        this.desc = desc;
        this.timestamp = timestamp;
    }
    public NexmarkAuctionRecord(long auctionId) {
        this.auctionId = auctionId;
        this.personId = -1;
        this.c = 0;
        this.itemId = -1;
        this.start = -1;
        this.end = -1;
        this.price = -1;
        this.name = "default name";
        this.desc = "default desc";
        this.timestamp = -1;
    }
    public static int getSize() {
        return AUCTION_RECORD_SIZE;
    }
    public static int getIngestionSize() {
        return AUCTION_RECORD_INGESTION_SIZE;
    }
}
