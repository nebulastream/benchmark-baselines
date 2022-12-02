package serialization;

public class YSBRecord {
    private final static int YSB_RECORD_SIZE = 78;
    private final static int YSB_RECORD_INGESTION_SIZE = 78; // todo move somewhere else

    public long user_id;
    public long page_id;
    public long campaign_id;
    public long ad_type;
    public long event_type;
    public long current_ms;
    public long ip;
    public long d1;
    public long d2;
    public int d3;
    public short d4;
//    public long d3;
//    public long d4;

    public YSBRecord(long user_id, long page_id, long campaign_id, long ad_type, long event_type, long current_ms, long ip, long d1, long d2, int d3, short d4) {
        this.user_id = user_id;
        this.page_id = page_id;
        this.campaign_id = campaign_id;
        this.ad_type = ad_type;
        this.event_type = event_type;
        this.current_ms = current_ms;
        this.ip = ip;
        this.d1 = d1;
        this.d2 = d2;
        this.d3 = d3;
        this.d4 = d4;
    }
    public YSBRecord(long campaign_id) {
        this.user_id = 1;
        this.page_id = 0;
        this.campaign_id = campaign_id;
        this.ad_type = 0;
        this.event_type = 0;
        this.current_ms = 5000;
        this.ip = 192168002;
        this.d1 = 1;
        this.d2 = 1;
        this.d3 = 1;
        this.d4 = 1;
    }
    public static int getSize() {
        return YSB_RECORD_SIZE;
    }
    public static int getIngestionSize() {
        return YSB_RECORD_INGESTION_SIZE;
    }

    public String toString() {
        return String.format("user_id: %d, page_id: %d, campaign_id: %d, ad_type: %d, event_type: %d, current_ms: %d, ip: %d, d1: %d, d2: %d, d3: %d, d4: %d",
            user_id,
            page_id,
            campaign_id,
            ad_type,
            event_type,
            current_ms,
            ip,
            d1,
            d2,
            d3,
            d4);
    }
}
