package de.tub.nebulastream.benchmarks.flink.ysb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.Serializable;
import java.util.UUID;

public class YSBRecord implements Serializable {
    public  long user_id;
    public  long page_id;
    public  long campaign_id;
    public  long ad_type;
    public  long event_type;
    public  long current_ms;
    public  long ip;
    public  long d1;
    public  long d2;
    public  int d3;
    public  int d4;

    public YSBRecord() {}
    public YSBRecord(long user_id, long page_id, long campaign_id, long ad_type, long event_type, long current_ms, long ip, long d1, long d2, int d3, int d4) {
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

    public static class YSBFinalRecord implements Serializable {

        public  long campaign_id;
        public  int value;

        public YSBFinalRecord() {}
        public YSBFinalRecord(long campaign_id, int v) {
            this.campaign_id = campaign_id;
            this.value = v;
        }

    }

    public static class YSBRecordSerializer extends com.esotericsoftware.kryo.Serializer<YSBRecord> {

        @Override
        public void write(Kryo kryo, Output output, YSBRecord object) {
            output.writeLong(object.user_id);
            output.writeLong(object.page_id);
            output.writeLong(object.campaign_id);
            output.writeLong(object.ad_type);
            output.writeLong(object.event_type);
            output.writeLong(object.current_ms);
            output.writeLong(object.ip);
            output.writeLong(object.d1);
            output.writeLong(object.d2);
            output.writeInt(object.d3);
            output.writeInt(object.d4);
        }

        private static byte[] readBytes(Input input, int count) {
            byte[] b = new byte[count];
            input.read(b);
            return b;
        }

        @Override
        public YSBRecord read(Kryo kryo, Input input, Class<YSBRecord> type) {
            return new YSBRecord(
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    input.readLong(),
                    input.readInt(),
                    input.readInt());
        }
    }

    public static class YSBFinalRecordSerializer extends com.esotericsoftware.kryo.Serializer<YSBFinalRecord> {

        @Override
        public void write(Kryo kryo, Output output, YSBFinalRecord object) {
            output.writeLong(object.campaign_id);
            output.writeInt(object.value);
        }

        private static byte[] readBytes(Input input, int count) {
            byte[] b = new byte[count];
            input.read(b);
            return b;
        }

        @Override
        public YSBFinalRecord read(Kryo kryo, Input input, Class<YSBFinalRecord> type) {
            return new YSBFinalRecord(
                    input.readLong(),
                    input.readInt());
        }
    }

}



