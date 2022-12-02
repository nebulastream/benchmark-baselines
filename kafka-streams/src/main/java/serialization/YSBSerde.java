package serialization;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KeyValue;

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
//    private static final int ITEMS_PER_BUFFER = 92;
    private static final int ITEMS_PER_BUFFER = 1600;

    private static long numReceivedBuffers = 0;
    private static long startMS;
    private static long countOfLogs = 0;

    static public List<YSBRecord> deserializeYSBBuffer(byte[] arr) {
//        assert (arr.length == 8192); // todo assert not working

        ByteBuffer buffer = ByteBuffer.wrap(arr);
//        int checksum = buffer.getInt();
//        int itemsInThisBuffer = buffer.getInt();
//        long newBacklog = buffer.getLong();
//        int itemsInThisBuffer =
//        if (checksum != 0xdeedf000
//            || ((8192 - 16) / YSBRecord.getIngestionSize()) < itemsInThisBuffer
//            || itemsInThisBuffer != ITEMS_PER_BUFFER) {
//            logger.error("Invalid buffer! checksum: {} itemsInThisBuffer: {}", checksum, itemsInThisBuffer);
//            assert (false); // todo not working
//        }

        List<YSBRecord> result = new LinkedList<>();

        // here we record the (ingested) throughput
        // we might want to move this to another class, but here is a convenient place to count buffers for now.
//        long log_every = 8_192 * 8;
        long log_every = 1000;
        if (numReceivedBuffers % log_every == 0) {
            if (startMS != 0) {
                long diff = (System.currentTimeMillis() - startMS);
                long throughputTupKperSec = numReceivedBuffers * ITEMS_PER_BUFFER / diff; // 1000 tup per sec
                long throughputMBperSec = throughputTupKperSec * 1000           // now in tup per sec
                                            * YSBRecord.getIngestionSize()      // now in bytes per sec
                                            / (1024*1024);                      // now in MB per sec
                logger.info("Log #{}: Last {} buffers ingested within {}s. Avg throughput: {}k tuples/s = {}MB/s",
                        countOfLogs,
                        numReceivedBuffers,
                        diff / 1000,
                        throughputTupKperSec,
                        throughputMBperSec);
            }
            startMS = System.currentTimeMillis();
            numReceivedBuffers = 0;
            countOfLogs++;
        }
        numReceivedBuffers++;

        for (int i = 0; i < ITEMS_PER_BUFFER; i++) {
            long user_id = buffer.getLong();
            long page_id = buffer.getLong();
            long campaign_id = buffer.getLong();
            long ad_type = buffer.getLong();
            long event_type = buffer.getLong();
            long current_ms = buffer.getLong();
            long ip = buffer.getLong();
            long d1 = buffer.getLong();
            long d2 = buffer.getLong();
            int d3 = buffer.getInt();
            short d4 = buffer.getShort();

            YSBRecord rec = new YSBRecord(user_id, page_id, campaign_id, ad_type, event_type, current_ms, ip, d1, d2, d3, d4);
//            result.add(new KeyValue<>(campaign_id, rec));
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
            int d3 = buffer.getInt();
            short d4 = buffer.getShort();

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
            buffer.putInt(data.d3);
            buffer.putShort(data.d4);

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

