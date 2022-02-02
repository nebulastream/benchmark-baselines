package serialization;

public class NexmarkPersonRecord {
    private final static int PERSON_RECORD_SIZE = 206;
    private final static int PERSON_RECORD_INGESTION_SIZE = 206; // todo

    public long timestamp;
    public long personId;
    public String name;
    public String email;
    public String city;
    public String country;
    public String province;
    public String zipcode;
    public String homepage;
    public String creditcard;
    public long ingestionTimestamp;


    public NexmarkPersonRecord(long timestamp,
                           long personId,
                           String name,
                           String email,
                           String city,
                           String country,
                           String province,
                           String zipcode,
                           String homepage,
                           String creditcard,
                           long ingestionTimestamp) {
        this.timestamp = timestamp;
        this.personId = personId;
        this.email = email;
        this.creditcard = creditcard;
        this.city = city;
        this.name = name;
        this.country = country;
        this.province = province;
        this.zipcode = zipcode;
        this.homepage = homepage;
        this.ingestionTimestamp = ingestionTimestamp;
    }
    public NexmarkPersonRecord(long personId) {
        this.timestamp = -1;
        this.personId = personId;
        this.email = "default email";
        this.creditcard = "default creditcard";
        this.city = "default city";
        this.name = "default name";
        this.country = "default country";
        this.province = "default province";
        this.zipcode = "default zipcode";
        this.homepage = "default homepage";
        this.ingestionTimestamp = -1;
    }
    public static int getSize() {
        return PERSON_RECORD_SIZE;
    }
    public static int getIngestionSize() {
        return PERSON_RECORD_INGESTION_SIZE;
    }
}
