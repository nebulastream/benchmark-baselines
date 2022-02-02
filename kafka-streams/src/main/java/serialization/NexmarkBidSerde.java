package serialization;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class NexmarkBidSerde implements Serde<NexmarkBidRecord> {
    private static final Logger logger = LoggerFactory.getLogger(NexmarkBidSerde.class);

    static public List<NexmarkBidRecord> deserializeBidBuffer(byte[] arr) {
        assert (arr.length == 8192); // todo assert not working

        ByteBuffer buffer = ByteBuffer.wrap(arr);
        int checksum = buffer.getInt();
        int itemsInThisBuffer = buffer.getInt();
        long newBacklog = buffer.getLong();

        if ((checksum != 0xdeedbeaf) || ((8192 - 16) / NexmarkBidRecord.getIngestionSize()) >= itemsInThisBuffer) {
            logger.error("Invalid buffer received."); // todo terminate?
        }

        List<NexmarkBidRecord> result = new LinkedList<>();

        long ingestionTimestamp = System.currentTimeMillis();

        logger.info("checksum: {}, itemsInThisBuffer: {}, newBacklog: {}",
                checksum, itemsInThisBuffer, newBacklog);

        for (int i = 0; i < itemsInThisBuffer; i++) {

            long bidderId = buffer.getLong();
            long auctionId = buffer.getLong();
            double price = buffer.getDouble();
            long timestamp = buffer.getLong();

            result.add(new NexmarkBidRecord(ingestionTimestamp, timestamp, auctionId, bidderId, -1, price));
        }

        return result;
    }

    static public class BidDeserializer implements Deserializer<NexmarkBidRecord> {
        @Override
        public void configure(Map<String, ?> map, boolean bln) {
        }

        @Override
        public void close() {
        }

        @Override
        public NexmarkBidRecord deserialize(String string, byte[] bytes) {
            System.out.println("DES: Bytes size: " + bytes.length);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            long ingestionTimestamp = buffer.getLong();
            long timestamp = buffer.getLong();
            long auctionId = buffer.getLong();
            long personId = buffer.getLong();
            long bidId = buffer.getLong();
            double bid = buffer.getDouble();

            return new NexmarkBidRecord(ingestionTimestamp, timestamp, auctionId, personId, bidId, bid);
        }
    }

    static public class BidSerializer implements Serializer<NexmarkBidRecord> {

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            Serializer.super.configure(configs, isKey);
        }

        @Override
        public byte[] serialize(String topic, NexmarkBidRecord data) {
            System.out.println("SER called!");
            ByteBuffer buffer = ByteBuffer.allocate(NexmarkBidRecord.getSize());

            buffer.putLong(data.ingestionTimestamp);
            buffer.putLong(data.timestamp);
            buffer.putLong(data.auctionId);
            buffer.putLong(data.personId);
            buffer.putLong(data.bidId);
            buffer.putDouble(data.bid);

            byte[] bytes = buffer.array();
            System.out.println("SER: Bytes size: " + bytes.length);
            return bytes;
        }

        @Override
        public byte[] serialize(String topic, Headers headers, NexmarkBidRecord data) {
            return Serializer.super.serialize(topic, headers, data);
        }

        @Override
        public void close() {
            Serializer.super.close();
        }
    }

    @Override
    public Serializer<NexmarkBidRecord> serializer() {
        return new BidSerializer();
    }

    @Override
    public Deserializer<NexmarkBidRecord> deserializer() {
        return new BidDeserializer();
    }

}

