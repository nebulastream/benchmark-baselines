package de.tub.nebulastream.benchmarks.flink.smartgrid;

import java.io.Serializable;

public class SGRecord implements Serializable {

    public long creationTS;
    public float value;
    public short property;
    public short plug;
    public short household;
    public short house;

    public SGRecord() {
    }

    public SGRecord(long creationTS, float value, short property, short plug, short household, short house) {
        this.creationTS = creationTS;
        this.value = value;
        this.property = property;
        this.plug = plug;
        this.household = household;
        this.house = house;
    }
}



