import java.io.Serializable;

public record YSBRecord(long user_id,
                        long page_id,
                        long campaign_id,
                        long ad_type,
                        long event_type,
                        long current_ms,
                        long ip,
                        long d1,
                        long d2,
                        long d3,
                        long d4)  implements Serializable {
}
