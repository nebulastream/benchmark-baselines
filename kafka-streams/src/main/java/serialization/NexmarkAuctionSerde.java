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

public class NexmarkAuctionSerde implements Serde<NexmarkAuctionRecord> {
    private static final Logger logger = LoggerFactory.getLogger(NexmarkAuctionSerde.class);

    static public List<NexmarkAuctionRecord> deserializeAuctionBuffer(byte[] arr) {
        assert (arr.length == 8192); // todo assert not working

        ByteBuffer buffer = ByteBuffer.wrap(arr);
        int checksum = buffer.getInt();
        int itemsInThisBuffer = buffer.getInt();
        long newBacklog = buffer.getLong();

        assert (checksum == 0x30061992);
        assert (((8192 - 16) / NexmarkAuctionRecord.getIngestionSize()) >= itemsInThisBuffer);

        List<NexmarkAuctionRecord> result = new LinkedList<>();

        byte[] tmp0 = new byte[20];
        byte[] tmp1 = new byte[200];

        logger.info("checksum: {}, itemsInThisBuffer: {}, newBacklog: {}",
                checksum, itemsInThisBuffer, newBacklog);

        for (int i = 0; i < itemsInThisBuffer; i++) {
            long auctionId = buffer.getLong();
            long personId = buffer.getLong();
            byte c = buffer.get();              // todo what is this value?
            int itemId = buffer.getInt();
            long start = buffer.getLong();
            long end = buffer.getLong();
            int price = buffer.getInt();
            buffer.get(tmp0);
            String name = new String(tmp0);
            buffer.get(tmp1);
            String desc = new String(tmp1);
            long timestamp = buffer.getLong();

            result.add(new NexmarkAuctionRecord(auctionId, personId, c, itemId, start, end, price, name, desc, timestamp));
        }

        return result;
    }
    static public class AuctionDeserializer implements Deserializer<NexmarkAuctionRecord> {
        @Override
        public void configure(Map<String, ?> map, boolean bln) {
        }

        @Override
        public void close() {
        }

        @Override
        public NexmarkAuctionRecord deserialize(String string, byte[] bytes) {
            System.out.println("DES: Bytes size: " + bytes.length);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] tmp0 = new byte[20];
            byte[] tmp1 = new byte[200];

            long auctionId = buffer.getLong();
            long personId = buffer.getLong();
            byte c = buffer.get();
            int itemId = buffer.getInt();
            long start = buffer.getLong();
            long end = buffer.getLong();
            int price = buffer.getInt();
            buffer.get(tmp0);
            String name = new String(tmp0);
            buffer.get(tmp1);
            String desc = new String(tmp1);
            long timestamp = buffer.getLong();
            return new NexmarkAuctionRecord(auctionId, personId, c, itemId, start, end, price, name, desc, timestamp);
        }
    }

    static public class AuctionSerializer implements Serializer<NexmarkAuctionRecord> {

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            Serializer.super.configure(configs, isKey);
        }

        @Override
        public byte[] serialize(String topic, NexmarkAuctionRecord data) {
            System.out.println("SER called!");

            byte[] tmp0 = new byte[20];
            byte[] tmp1 = new byte[200];

            for (int i=0; i<Math.min(20, data.name.length()); i++) {
                tmp0[i] = (byte) data.name.charAt(i);
            }
            for (int i=0; i<Math.min(200, data.desc.length()); i++) {
                tmp1[i] = (byte) data.desc.charAt(i);
            }

            ByteBuffer buffer = ByteBuffer.allocate(NexmarkAuctionRecord.getSize());
            buffer.putLong(data.auctionId);
            buffer.putLong(data.personId);
            buffer.put(data.c);
            buffer.putInt(data.itemId);
            buffer.putLong(data.start);
            buffer.putLong(data.end);
            buffer.putInt(data.price);
            buffer.put(tmp0);
            buffer.put(tmp1);
            buffer.putLong(data.timestamp);

            byte[] bytes = buffer.array();
            System.out.println("SER: Bytes size: " + bytes.length);
            return bytes;
        }

        @Override
        public byte[] serialize(String topic, Headers headers, NexmarkAuctionRecord data) {
            return Serializer.super.serialize(topic, headers, data);
        }

        @Override
        public void close() {
            Serializer.super.close();
        }
    }

    @Override
    public Serializer<NexmarkAuctionRecord> serializer() {
        return new AuctionSerializer();
    }

    @Override
    public Deserializer<NexmarkAuctionRecord> deserializer() {
        return new AuctionDeserializer();
    }

}

