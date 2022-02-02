package de.dfki.iam.yahoo.consumer;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import de.dfki.iam.yahoo.record.IntermediateTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class OffheapConsumer implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

	private final int id;
	private final LinkedBlockingQueue<ByteBuffer> inputQueue;

	private AtomicInteger keepConsuming;

	private HashMap<Long, HashMap<Long, Long>> windows;

	private int window_size;

	private final Function<Map<Long, Long>, Void> sink;

	private final CyclicBarrier latch;

	private final int bufferSize;

	private final AbstractHardwareSampler hwSampler;


	public OffheapConsumer(int id, int window_size, int bufferSize,
                           LinkedBlockingQueue<ByteBuffer> queue, CyclicBarrier controller, AtomicInteger keepConsuming,
                           Function<Map<Long, Long>, Void> sinkOp, AbstractHardwareSampler hwSampler) {
		this.id = id;
		this.window_size = window_size;
		this.inputQueue = queue;
		this.windows = new HashMap<>();
		this.keepConsuming = keepConsuming;
		this.sink = sinkOp;
		this.latch = controller;
		this.bufferSize = bufferSize;
		this.hwSampler = hwSampler;
	}

	@Override
	public void run() {

		try {
			PAPIHardwareSampler hwPAPI = null;
			if (hwSampler != null) {
				hwSampler.startSampling();
			}

			while (keepConsuming.get() > 0) {
				while (inputQueue.size() > 0) {

					ByteBuffer tuples = inputQueue.poll();
					boolean queuePoisoned = false;

					if (tuples.capacity() == 0) {
						queuePoisoned = true; // queue is poisoned
					}

					int endPosition = tuples.position();
					tuples.rewind();

					while (tuples.position() < endPosition) {
						long campaignIdMsb = tuples.getLong();
						long campaignIdLsb = tuples.getLong();
						long timestamp = tuples.getLong();
						long target_window = timestamp % window_size;
						long key = campaignIdMsb ^ campaignIdLsb;

						HashMap<Long, Long> w;

						if (!windows.containsKey(target_window)) {

							for (Long oldKey : windows.keySet()) {
								sink.apply(windows.get(oldKey));
							}

							windows.clear();

							w = new HashMap<>();
							windows.put(target_window, w);
						} else {
							w = windows.get(target_window);
						}

						if (w.containsKey(key)) {
							w.put(key, w.get(key) + 1L);
						} else {
							w.put(key, 1L);
						}
					}
					if (queuePoisoned) {
						break; // break again
					}
				}
			}
			for (Long k : windows.keySet()) {
				sink.apply(windows.get(k));
			}
			windows.clear();
			if (hwSampler != null) {
				hwSampler.stopSampling("BufferedConsumer " + id);
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage());
			System.exit(-1);
		} finally {

			try {
				latch.await();
			} catch (Exception ex2) {
				LOG.error(ex2.getMessage());
			}

			LOG.info("consumer " + id + " done");


		}



	}



}
