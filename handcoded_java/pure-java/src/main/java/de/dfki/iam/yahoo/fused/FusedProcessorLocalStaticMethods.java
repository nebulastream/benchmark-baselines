package de.dfki.iam.yahoo.fused;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class FusedProcessorLocalStaticMethods implements Runnable {

	private final static Logger LOG = LoggerFactory.getLogger(FusedProcessorLocalStaticMethods.class);

	private final int id, inputSize;
	private final ByteBuffer inBuffer;
	private final CyclicBarrier latch;

	private final Function<AtomicLong[], Void> sink;
	private final int window_size;

	private final AtomicLong[][] windows;

	private final AbstractHardwareSampler hwSampler;

	private static final AtomicInteger currentWindow = new AtomicInteger(0);

	public FusedProcessorLocalStaticMethods(int id, int window_size, ByteBuffer in, CyclicBarrier controller, int inputSize,
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



	final private UUID readUUID(final ByteBuffer bb) {
		final long in1 = bb.getLong();
		final long in2 = bb.getLong();
		return new UUID(in1, in2);
	}

	final private String readChar(final ByteBuffer bb) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 9; i++) {
			byte next = bb.get();
			if (next == ' ' || next == '\0') {
				continue;
			}
			builder.append((char)next);
		}
		return builder.toString();
	}


	@Override
	public void run() {

		PAPIHardwareSampler hwPAPI = null;
		long counterIn = 0;
		long counterview = 0;
		try {

			if (hwSampler != null) {
				hwSampler.startSampling();
			}

			int target_window = 0;
			for (int i = 0; i < inputSize; i++) {

				counterIn++;
				UUID dummy0 = readUUID(inBuffer);
				UUID dummy1 = readUUID(inBuffer);
				UUID campaignId = readUUID(inBuffer);
				String banner78 = readChar(inBuffer);
				String eventType = readChar(inBuffer);
				long timestamp = inBuffer.getLong();
				int ip = inBuffer.getInt();


				if (eventType.startsWith("view")) {
					counterview++;
					int hashV = Math.abs(campaignId.hashCode() % 10000);
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

