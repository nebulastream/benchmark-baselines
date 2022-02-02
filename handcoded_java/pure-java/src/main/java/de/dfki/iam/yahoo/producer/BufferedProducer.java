package de.dfki.iam.yahoo.producer;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.record.IntermediateTuple;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferedProducer implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(BufferedProducer.class);

	private final int id, inputSize;
	private final ByteBuffer inBuffer;

	private final LinkedBlockingQueue<IntermediateTuple[]>[] queues;

	private final AtomicInteger keepRunningConsumers;
	private final CyclicBarrier latch;

	private final int bufferSize;

	private final AbstractHardwareSampler hwSampler;

	public BufferedProducer(int id, int bufferSize, ByteBuffer in, CyclicBarrier controller, int inputSize,
							LinkedBlockingQueue<IntermediateTuple[]>[] queues, AtomicInteger keepRunning,
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

	private UUID readUUID(final ByteBuffer bb) {
		final long in1 = bb.getLong();
		final long in2 = bb.getLong();
		return new UUID(in1, in2);
	}

	private  String readChar(final ByteBuffer bb) {
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

		final int parallelism = queues.length;

		PAPIHardwareSampler hwPAPI = null;
		IntermediateTuple[][] currentBuffers = new IntermediateTuple[parallelism][bufferSize];
		int[] currentBufferIndex = new int[parallelism];

		int read = 0;
		int qual = 0;
		try {

			if (hwSampler != null) {
				hwSampler.startSampling();
			}

			for (int i = 0; i < inputSize; i++) {
				read++;

				UUID dummy0 = readUUID(inBuffer);
				UUID dummy1 = readUUID(inBuffer);
				UUID campaignId = readUUID(inBuffer);
				String banner78 = readChar(inBuffer);
				String eventType = readChar(inBuffer);
				long timestamp = inBuffer.getLong();
				int ip = inBuffer.getInt();


				if (eventType.startsWith("view")) {
					qual++;
					int pid = Math.abs(campaignId.hashCode() % parallelism);
					currentBuffers[pid][currentBufferIndex[pid]++] = new IntermediateTuple(campaignId, System.currentTimeMillis());
					if (currentBufferIndex[pid] == bufferSize) {
						queues[pid].add(currentBuffers[pid]);
						currentBuffers[pid] = new IntermediateTuple[bufferSize];
						currentBufferIndex[pid] = 0;
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
				queues[pid].add(new IntermediateTuple[] { IntermediateTuple.POISONED_TUPLE });
			}
			LOG.info("producer " + id + " read=" + read + " qual=" + qual +" done");

			try {
				latch.await();
			} catch (Exception ex2) {
				LOG.error(ex2.getMessage());
			}
		}






	}
}
