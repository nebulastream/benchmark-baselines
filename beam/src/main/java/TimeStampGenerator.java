import org.apache.beam.sdk.transforms.SerializableFunction;
import org.joda.time.Duration;
import org.joda.time.Instant;

import java.util.concurrent.ThreadLocalRandom;

public class TimeStampGenerator implements SerializableFunction<Integer, Instant> {
    private Instant eventTime;
    private double outOfOrderProbability;
    private long sessionPeriod;
    private long minLateness;
    private long maxLateness;
    private long minGap;
    private long maxGap;
    private long lastSecond;
    private Instant lastGap;

    public TimeStampGenerator() {
        this.eventTime = new Instant(0);
        this.lastGap = new Instant(0);
        this.outOfOrderProbability = 0;
        this.minLateness = 0;
        this.maxLateness = 0;
        this.sessionPeriod = 0;
        this.minGap = 0;
        this.maxGap = 0;
    }

    @Override
    public Instant apply(Integer input) {
        var ts = System.currentTimeMillis();
        return new Instant(ts);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TimeStampGenerator)) {
            return false;
        }
        TimeStampGenerator that = (TimeStampGenerator) other;
        return that.lastGap.equals(that.lastGap);
    }
}