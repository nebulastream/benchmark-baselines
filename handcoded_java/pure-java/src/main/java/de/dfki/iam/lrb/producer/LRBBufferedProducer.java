package de.dfki.iam.lrb.producer;

import de.dfki.iam.lrb.record.InputRecord;
import de.dfki.iam.lrb.record.LRBIntermediateRecord;
import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class LRBBufferedProducer implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(LRBBufferedProducer.class);

    private final int id, inputSize;
    private final BufferedReader reader;

    private final LinkedBlockingQueue<LRBIntermediateRecord[]>[] queues;

    private final AtomicInteger keepRunningConsumers;
    private final CountDownLatch latch;

    private final int bufferSize;

    private final AbstractHardwareSampler hwSampler;

    public LRBBufferedProducer(int id, int bufferSize, BufferedReader in, CountDownLatch controller, int inputSize,
                               LinkedBlockingQueue<LRBIntermediateRecord[]>[] queues, AtomicInteger keepRunning,
                               AbstractHardwareSampler hwSampler) {
        this.id = id;
        this.inputSize = inputSize;
        this.reader = in;
        this.queues = queues;
        this.keepRunningConsumers = keepRunning;
        this.latch = controller;
        this.bufferSize = bufferSize;
        this.hwSampler = hwSampler;
    }


    @Override
    public void run() {

        final int parallelism = queues.length;

        LRBIntermediateRecord[][] currentBuffers = new LRBIntermediateRecord[parallelism][bufferSize];
        int[] currentBufferIndex = new int[parallelism];

        try {


            if (hwSampler != null) {
                hwSampler.startSampling();
            }

            for (int i = 0; i < inputSize; i++) {

                InputRecord in = new InputRecord(reader.readLine());


                if (in.getM_iType() == 0) {

                    LRBIntermediateRecord intermediate = new LRBIntermediateRecord(
                            in.getM_iVid(), in.getM_iSeg(),
                            in.getM_iSpeed(), in.getM_iPos(),
                            in.getM_iTime(), in.getM_iXway());

                    int pid = Math.abs(Integer.hashCode(in.getM_iSeg()) % parallelism);
                    currentBuffers[pid][currentBufferIndex[pid]++] = intermediate;
                    if (currentBufferIndex[pid] == bufferSize) {
                        queues[pid].add(currentBuffers[pid]);
                        currentBuffers[pid] = new LRBIntermediateRecord[bufferSize];
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
                queues[pid].add(new LRBIntermediateRecord[] { LRBIntermediateRecord.POISONED_TUPLE });
            }


            latch.countDown();
        }



    }
}
