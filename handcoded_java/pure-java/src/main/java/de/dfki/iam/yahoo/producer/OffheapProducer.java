package de.dfki.iam.yahoo.producer;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import de.dfki.iam.yahoo.record.IntermediateTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class OffheapProducer implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(OffheapProducer.class);

	private final int id, inputSize;
	private final ByteBuffer inBuffer;

	private final LinkedBlockingQueue<ByteBuffer>[] queues;

	private final AtomicInteger keepRunningConsumers;
	private final CyclicBarrier latch;

	private final int bufferSize;
	private final AbstractHardwareSampler hwSampler;

	public OffheapProducer(int id, int bufferSize, ByteBuffer in, CyclicBarrier controller, int inputSize,
                           LinkedBlockingQueue<ByteBuffer>[] queues, AtomicInteger keepRunning,
                           AbstractHardwareSampler hwSampler) {
		this.id = id;
		this.inputSize = inputSize;
		this.inBuffer = in;
		this.queues = queues;
		this.keepRunningConsumers = keepRunning;
		this.latch = controller;
		this.bufferSize = bufferSize;
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

		final int parallelism = queues.length;

		PAPIHardwareSampler hwPAPI = null;
		ByteBuffer[] currentBuffers = new ByteBuffer[parallelism];

		for (int i = 0; i < parallelism; ++i) {
			currentBuffers[i] = ByteBuffer.allocate(bufferSize * IntermediateTuple.size());
		}

		ByteBuffer viewEvent = ByteBuffer.wrap("view\0\0\0\0\0".getBytes(Charset.forName("US-ASCII")));

		try {

			if (hwSampler != null) {
				hwSampler.startSampling();
			}

			for (int i = 0; i < inputSize; i++) {

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
					int pid = Math.abs(UUIDhashCode(campaignIdMsb, campaignIdLsb) % parallelism);
					currentBuffers[pid].putLong(campaignIdMsb);
					currentBuffers[pid].putLong(campaignIdLsb);
					currentBuffers[pid].putLong(System.currentTimeMillis());
					if (!currentBuffers[pid].hasRemaining()) {
						queues[pid].add(currentBuffers[pid]);
						currentBuffers[pid] = ByteBuffer.allocate(bufferSize * IntermediateTuple.size());
					}
				}
			}

			for (int pid = 0; pid < currentBuffers.length; pid++) {
				queues[pid].add(currentBuffers[pid]);
			}

			if (hwSampler != null) {
				hwSampler.stopSampling("BufferedProducer" + id);
			}

		} catch (Exception ex) {
			LOG.error(ex.getMessage());
		} finally {
			keepRunningConsumers.getAndDecrement();

			for (int pid = 0; pid < currentBuffers.length; pid++) {
				queues[pid].add(ByteBuffer.allocate(0)); // Poison the queue with 0-length buffer
			}

			try {
				latch.await();
			} catch (Exception ex2) {
				LOG.error(ex2.getMessage());
			}
		}






	}
}
