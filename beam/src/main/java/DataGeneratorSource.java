import com.google.common.collect.ImmutableList;
import org.apache.beam.sdk.coders.*;
import org.apache.beam.sdk.io.UnboundedSource;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.beam.sdk.values.KV;
import org.joda.time.Instant;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class DataGeneratorSource extends UnboundedSource<YSBRecord, Checkpoint> {

    private int throughput;
    public final SerializableFunction<Integer, Instant> timestampFn;

    public DataGeneratorSource(int throughput, SerializableFunction<Integer, Instant> timestampFn) {
        this.throughput = throughput;
        this.timestampFn = timestampFn;
    }

    @Override
    public List<? extends UnboundedSource<YSBRecord, Checkpoint>> split(int desiredNumSplits, PipelineOptions options){
        ImmutableList.Builder<DataGeneratorSource> splits = ImmutableList.builder();
        splits.add(new DataGeneratorSource(throughput, timestampFn));
        return splits.build();
    }

    @Override
    public UnboundedReader createReader(PipelineOptions options, @Nullable Checkpoint mark) throws IOException {
        return new YSBGeneratorReader(throughput, this, mark);
    }

    @Override
    public Coder<Checkpoint> getCheckpointMarkCoder() {
        return AvroCoder.of(Checkpoint.class);

    }

    @Override
    public Coder<YSBRecord> getOutputCoder() {
        return SerializableCoder.of(YSBRecord.class);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DataGeneratorSource)) {
            return false;
        }
        DataGeneratorSource that = (DataGeneratorSource) other;
        return this.timestampFn.equals(that.timestampFn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestampFn);
    }
}
