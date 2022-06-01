package de.tub.nebulastream.benchmarks.flink.linearroad;

public class LRRecord {


    public long creationTS;
    public short vehicle;
    public float speed;
    public short highway;
    public short lane;
    public short direction;
    public short position;

    public LRRecord(long creationTS, short vehicle, float speed, short highway, short lane, short direction, short position) {
        this.creationTS = creationTS;
        this.vehicle = vehicle;
        this.speed = speed;
        this.highway = highway;
        this.lane = lane;
        this.direction = direction;
        this.position = position;
    }

    public LRRecord() {
    }
}
