package de.dfki.iam.yahoo.producer;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.record.IntermediateTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Producer implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(Producer.class);

	private final int id, inputSize;
	private final ByteBuffer inBuffer;

	private final LinkedBlockingQueue<IntermediateTuple>[] queues;

	private final AtomicInteger keepRunningConsumers;
	private final CyclicBarrier latch;

	private final AbstractHardwareSampler hwSampler;

	public Producer(int id, ByteBuffer in, CyclicBarrier controller, int inputSize,
					LinkedBlockingQueue<IntermediateTuple>[] queues, AtomicInteger keepRunning,
					AbstractHardwareSampler hwSampler) {
		this.id = id;
		this.inputSize = inputSize;
		this.inBuffer = in;
		this.queues = queues;
		this.keepRunningConsumers = keepRunning;
		this.latch = controller;
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
		try {

			if (hwSampler != null) {
				hwSampler.startSampling();
			}

			for (int i = 0; i < inputSize; i++) {


				UUID dummy0 = readUUID(inBuffer);
				UUID dummy1 = readUUID(inBuffer);
				UUID campaignId = readUUID(inBuffer);
				String banner78 = readChar(inBuffer);
				String eventType = readChar(inBuffer);
				long timestamp = inBuffer.getLong();
				int ip = inBuffer.getInt();


				if (eventType.startsWith("view")) {
					int pid = campaignId.hashCode() % parallelism;
					queues[pid].add(new IntermediateTuple(campaignId, System.currentTimeMillis()));
				}
			}

			if (hwSampler != null) {
				hwSampler.stopSampling("Producer " + id);
			}


		} catch (Exception ex) {
			LOG.error(ex.getMessage());
		} finally {
			keepRunningConsumers.getAndDecrement();


			for (LinkedBlockingQueue<IntermediateTuple> q : queues) {
				q.add(IntermediateTuple.POISONED_TUPLE);
			}

			try {
				latch.await();
			} catch (Exception ex2) {
				LOG.error(ex2.getMessage());
			}
		}



	}
}
