package serialization;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.ByteBuffer;
import java.util.Map;

public class NexmarkPersonSerde implements Serde<NexmarkPersonRecord> {
    static public class PersonDeserializer implements Deserializer<NexmarkPersonRecord> {
        @Override
        public void configure(Map<String, ?> map, boolean bln) {
        }

        @Override
        public void close() {
        }

        @Override
        public NexmarkPersonRecord deserialize(String string, byte[] bytes) {
            System.out.println("NPerson DES: Bytes size: " + bytes.length);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            // TODO
            return new NexmarkPersonRecord(69);
        }
    }

    static public class PersonSerializer implements Serializer<NexmarkPersonRecord> {

        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
            Serializer.super.configure(configs, isKey);
        }

        @Override
        public byte[] serialize(String topic, NexmarkPersonRecord data) {
            System.out.println("NPerson SER called!");

            ByteBuffer buffer = ByteBuffer.allocate(NexmarkPersonRecord.getSize());
            // TODO
            byte[] bytes = buffer.array();
            System.out.println("NPerson SER: Bytes size: " + bytes.length);
            return bytes;
        }

        @Override
        public byte[] serialize(String topic, Headers headers, NexmarkPersonRecord data) {
            return Serializer.super.serialize(topic, headers, data);
        }

        @Override
        public void close() {
            Serializer.super.close();
        }
    }

    @Override
    public Serializer<NexmarkPersonRecord> serializer() {
        return new PersonSerializer();
    }

    @Override
    public Deserializer<NexmarkPersonRecord> deserializer() {
        return new PersonDeserializer();
    }

}

