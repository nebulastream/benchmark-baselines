package de.tub.nebulastream.benchmarks.flink.manufacturingequipment;

import java.io.Serializable;

public class MERecord implements Serializable {

    public long creationTS;
    public long messageIndex;
    public short mf01;
    public short mf02;
    public short mf03;
    public short pc13;
    public short pc14;
    public short pc15;
    public short pc25;
    public short pc26;
    public short pc27;
    public short res;
    public short bm05;
    public short bm06;

    public MERecord() {
    }

    public MERecord(long creationTS, long messageIndex, short mf01, short mf02, short mf03, short pc13, short pc14, short pc15, short pc25, short pc26, short pc27, short res, short bm05, short bm06) {
        this.creationTS = creationTS;
        this.messageIndex = messageIndex;
        this.mf01 = mf01;
        this.mf02 = mf02;
        this.mf03 = mf03;
        this.pc13 = pc13;
        this.pc14 = pc14;
        this.pc15 = pc15;
        this.pc25 = pc25;
        this.pc26 = pc26;
        this.pc27 = pc27;
        this.res = res;
        this.bm05 = bm05;
        this.bm06 = bm06;
    }
}



