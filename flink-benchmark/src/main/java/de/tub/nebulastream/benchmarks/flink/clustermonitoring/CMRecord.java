package de.tub.nebulastream.benchmarks.flink.clustermonitoring;

import java.io.Serializable;

public class CMRecord implements Serializable {

    public long creationTS;
    public long jobId;
    public long taskId;
    public long machineId;
    public short eventType;
    public short userId;
    public short category;
    public short priority;
    public float cpu;
    public float ram;
    public float disk;
    public short constraints;

    public CMRecord() {
    }

    public CMRecord(long creationTS, long jobId, long taskId, long machineId, short eventType, short userId, short category, short priority, float cpu, float ram, float disk, short constraints) {
        this.creationTS = creationTS;
        this.jobId = jobId;
        this.taskId = taskId;
        this.machineId = machineId;
        this.eventType = eventType;
        this.userId = userId;
        this.category = category;
        this.priority = priority;
        this.cpu = cpu;
        this.ram = ram;
        this.disk = disk;
        this.constraints = constraints;
    }
}



