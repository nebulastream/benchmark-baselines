package serialization;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class YSBSerde implements Serde<YSBRecord> {
    private static final Logger logger = LoggerFactory.getLogger(YSBSerde.class);
    private static final int ITEMS_PER_BUFFER = 92;

    private static long numReceivedBuffers = 0;
    private static long startMS;

    static public List<YSBRecord> deserializeYSBBuffer(byte[] arr) {
        assert (arr.length == 8192); // todo assert not working

        ByteBuffer buffer = ByteBuffer.wrap(arr);
        int checksum = buffer.getInt();
        int itemsInThisBuffer = buffer.getInt();
        long newBacklog = buffer.getLong();

        assert (checksum == 0xdeedf000);
        assert (((8192 - 16) / YSBRecord.getIngestionSize()) >= itemsInThisBuffer);
        assert (itemsInThisBuffer == ITEMS_PER_BUFFER);

        List<YSBRecord> result = new LinkedList<>();

        // here we record the (ingested) throughput
        // we might want to move this to another class, but here is a convenient place to count buffers for now.
        numReceivedBuffers++;
        if (numReceivedBuffers == 1) {
            // start benchmark timing
            startMS = System.currentTimeMillis();
            String timeOfDay = new SimpleDateFormat("hh:mm:ss").format(new Date(startMS));
            logger.info("Starting benchmark. First buffer received at: {}", timeOfDay);
        } else if (numReceivedBuffers % 128 == 0){
            long diff = (System.currentTimeMillis() - startMS);
            logger.info("{} buffers ingested after {}s. Avg throughput: {}k tuples/s",
                    numReceivedBuffers,
                    diff / 1000,
                    numReceivedBuffers * ITEMS_PER_BUFFER / diff);
        }

        for (int i = 0; i < itemsInThisBuffer; i++) {
            long user_id = buffer.getLong();
            long page_id = buffer.getLong();
            long campaign_id = buffer.getLong();
            long ad_type = buffer.getLong();
            long event_type = buffer.getLong();
            long current_ms = buffer.getLong();
            long ip = buffer.getLong();
            long d1 = buffer.getLong();
            long d2 = buffer.getLong();
            long d3 = buffer.getLong();
            long d4 = buffer.getLong();

            YSBRecord rec = new YSBRecord(user_id, page_id, campaign_id, ad_type, event_type, current_ms, ip, d1, d2, d3, d4);
            result.add(rec);
        }

        return result;
    }
    static public class AuctionDeserializer implements Deserializer<YSBRecord> {
        @Override
        public void configure(Map<String, ?> map, boolean bln) {
        }

        @Override
        public void close() {
        }

        @Override
        public YSBRecord deserialize(String string, byte[] bytes) {
//            System.out.println("YSB DES: Bytes size: " + bytes.length);
            // todo assert size
            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            long user_id = buffer.getLong();
            long page_id = buffer.getLong();
            long campaign_id = buffer.getLong();
            long ad_type = buffer.getLong();
            long event_type = buffer.getLong();
            long current_ms = buffer.getLong();
            long ip = buffer.getLong();
            long d1 = buffer.getLong();
            long d2 = buffer.getLong();
            long d3 = buffer.getLong();
            long d4 = buffer.getLong();

            return new YSBRecord(user_id, page_id, campaign_id, ad_type, event_type, current_ms, ip, d1, d2, d3, d4);
        }
    }

    static public class AuctionSerializer implements Serializer<YSBRecord> {

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            Serializer.super.configure(configs, isKey);
        }

        @Override
        public byte[] serialize(String topic, YSBRecord data) {
//            System.out.println("YSB SER called!");

            ByteBuffer buffer = ByteBuffer.allocate(YSBRecord.getSize());

            buffer.putLong(data.user_id);
            buffer.putLong(data.page_id);
            buffer.putLong(data.campaign_id);
            buffer.putLong(data.ad_type);
            buffer.putLong(data.event_type);
            buffer.putLong(data.current_ms);
            buffer.putLong(data.ip);
            buffer.putLong(data.d1);
            buffer.putLong(data.d2);
            buffer.putLong(data.d3);
            buffer.putLong(data.d4);

            byte[] bytes = buffer.array();
//            System.out.println("YSB SER: Bytes size: " + bytes.length);
            return bytes;
        }

        @Override
        public byte[] serialize(String topic, Headers headers, YSBRecord data) {
            return Serializer.super.serialize(topic, headers, data);
        }

        @Override
        public void close() {
            Serializer.super.close();
        }
    }

    @Override
    public Serializer<YSBRecord> serializer() {
        return new AuctionSerializer();
    }

    @Override
    public Deserializer<YSBRecord> deserializer() {
        return new AuctionDeserializer();
    }

}

