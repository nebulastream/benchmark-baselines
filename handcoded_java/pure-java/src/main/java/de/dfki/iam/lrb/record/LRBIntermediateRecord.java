package de.dfki.iam.lrb.record;

import java.io.Serializable;

public class LRBIntermediateRecord implements Serializable {

    public int vid, seg, speed, pos, time, xway;

    private boolean poisoned = false;

    public static final LRBIntermediateRecord POISONED_TUPLE = new LRBIntermediateRecord();


    public LRBIntermediateRecord(int vid, int seg, int speed, int pos, int time, int xway) {

        this.pos = pos;
        this.vid = vid;
        this.seg = seg;
        this.speed = speed;
        this.time = time;
        this.xway = xway;

    }


    private LRBIntermediateRecord() {
        poisoned = true;
    }


    public boolean equals(Object oth) {

        if (oth instanceof LRBIntermediateRecord) {

            LRBIntermediateRecord that = (LRBIntermediateRecord) oth;

            if (that.poisoned && poisoned) {
                return true;
            }

            return pos == that.pos &&
                    vid == that.vid &&
                    seg == that.seg &&
                    speed == that.speed &&
                    time == that.time &&
                    xway == that.xway;

        }

        return false;

    }



}
