package de.dfki.iam.yahoo.fused;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class FusedProcessorLocalOffheap implements Runnable {

	private final static Logger LOG = LoggerFactory.getLogger(FusedProcessorLocalOffheap.class);

	private final int id, inputSize;
	private final ByteBuffer inBuffer;
	private final CyclicBarrier latch;

	private final Function<AtomicLong[], Void> sink;
	private final int window_size;

	private final AtomicLong[][] windows;

	private final AbstractHardwareSampler hwSampler;

	private static final AtomicInteger currentWindow = new AtomicInteger(0);

	public FusedProcessorLocalOffheap(int id, int window_size, ByteBuffer in, CyclicBarrier controller, int inputSize,
                                      Function<AtomicLong[], Void> sink, AtomicLong[][] windows, AbstractHardwareSampler hwSampler) {
		this.id = id;
		this.window_size = window_size;
		this.inBuffer = in;
		this.latch = controller;
		this.inputSize = inputSize;
		this.sink = sink;
		this.windows = windows;
		this.hwSampler = hwSampler;
	}

	final public int UUIDhashCode(final long mostSigBits, final long leastSigBits) {
		long var1 = mostSigBits ^ leastSigBits;
		return (int)(var1 >> 32) ^ (int)var1;
	}

	final private boolean compareSubarrays(final ByteBuffer a, final ByteBuffer b, int len) {

		int i = 0;
		while (a.hasRemaining() && b.hasRemaining() && i < len) {
			if (a.get() != b.get()) {
				return false;
			}
			++i;
		}

		return true;
	}

	@Override
	public void run() {

		PAPIHardwareSampler hwPAPI = null;
		long counterIn = 0;
		long counterview = 0;
		ByteBuffer viewEvent = ByteBuffer.wrap("view\0\0\0\0\0".getBytes(Charset.forName("US-ASCII")));
		try {

			if (hwSampler != null) {
				hwSampler.startSampling();
			}

			int target_window = 0;
			for (int i = 0; i < inputSize; i++) {

				counterIn++;
				inBuffer.position(inBuffer.position() + 4 * Long.BYTES); // Skip UserID and PageID
				long campaignIdMsb = inBuffer.getLong();
				long campaignIdLsb = inBuffer.getLong();
				inBuffer.position(inBuffer.position() + 9); // Skip field Banner aka ad_type

				// Check if event_type equals "view"
				int afterEventPos = inBuffer.position() + 9;
				boolean isViewEventType = compareSubarrays(inBuffer, viewEvent, 9);
				viewEvent.rewind();
				inBuffer.position(afterEventPos);

				long timestamp = inBuffer.getLong();
				int ip = inBuffer.getInt();

				if (isViewEventType == true) {
					counterview++;
					int hashV = Math.abs(UUIDhashCode(campaignIdMsb, campaignIdLsb) % 10000);
					long realTimestamp = System.currentTimeMillis();
					target_window = (int) (realTimestamp / 1000) % 2;
					target_window += 2*id;
					windows[target_window][hashV].incrementAndGet();
//					if (currentWindow.compareAndSet(target_window - 1, target_window)) {
//						sink.apply(windows[target_window - 1]);
//					}
				}

			}
			System.out.println("Counter in=" + counterIn + " counterView=" + counterview);
			if (hwSampler != null) {
				hwSampler.stopSampling("FusedOperator " + id);
			}
			for(int i = 0; i < 10000; i++)
			{
				windows[9][i] = windows[target_window][i];
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			LOG.error(ex.getMessage());
		} finally {
            try {
                latch.await();
            } catch (Exception ex2) {
                LOG.error(ex2.getMessage());
            }
		}


	}



}

