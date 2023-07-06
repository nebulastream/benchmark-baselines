import org.apache.beam.sdk.io.UnboundedSource;
import org.apache.beam.sdk.values.KV;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Random;

public class YSBGeneratorReader extends UnboundedSource.UnboundedReader<YSBRecord> {
    private DataGeneratorSource source;
    private YSBRecord current;
    private Random random = new Random();
    private long lastTime;
    private long now;
    private int counter = 0;
    private int throughputLimit;
    private int key = 1;

    // Initialized on first advance()
    @Nullable
    private Instant currentTimestamp;

    // Initialized in start()
    @Nullable
    private Instant firstStarted;

    public YSBGeneratorReader(int throughputLimit, DataGeneratorSource source, Checkpoint mark) {
        this.source = source;
        this.throughputLimit = throughputLimit;
        if (mark == null) {
            // Because we have not emitted an element yet, and start() calls advance, we need to
            // "un-advance" so that start() produces the correct output.
            this.current = new YSBRecord(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        } else {
            this.current = new YSBRecord(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
            this.firstStarted = mark.getStartTime();
        }
    }

    @Override
    public boolean start() throws IOException {
        System.out.println("Started Reader");
        if (firstStarted == null) {
            this.firstStarted = Instant.now();
            this.lastTime = this.firstStarted.getMillis();
        }
        return true;
    }

    @Override
    public boolean advance() throws IOException {
        //Generate with limit
        if (throughputLimit != 0) {
            now = System.currentTimeMillis();
            if (this.counter < this.throughputLimit && now < this.lastTime + 1000) {
                this.counter++;
                this.current = new YSBRecord(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
                return true;
            } else {
                if (now > this.lastTime + 1000) {
                    lastTime = now;
                    counter = 0;
                }
                return false;
            }
        } else {
            this.counter++;
            return true;
        }
    }

    @Override
    public Instant getWatermark() {
        return new Instant(System.currentTimeMillis());
    }

    @Override
    public Checkpoint getCheckpointMark() {
        return new Checkpoint(1, 2, firstStarted);
    }

    @Override
    public UnboundedSource<YSBRecord, Checkpoint> getCurrentSource() {
        return source;
    }

    @Override
    public YSBRecord getCurrent() throws NoSuchElementException {
        return current;
    }

    @Override
    public Instant getCurrentTimestamp() throws NoSuchElementException {
        return new Instant(System.currentTimeMillis());
    }

    @Override
    public void close() throws IOException {

    }
}