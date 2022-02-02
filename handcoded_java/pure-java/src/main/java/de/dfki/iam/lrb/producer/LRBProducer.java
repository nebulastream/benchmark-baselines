package de.dfki.iam.lrb.producer;

import de.dfki.iam.lrb.record.InputRecord;
import de.dfki.iam.lrb.record.LRBIntermediateRecord;
import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.record.IntermediateTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class LRBProducer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(LRBProducer.class);

    private final int id, inputSize;
    private final BufferedReader reader;

    private final LinkedBlockingQueue<LRBIntermediateRecord>[] queues;

    private final AtomicInteger keepRunningConsumers;
    private final CountDownLatch latch;

    private final AbstractHardwareSampler hwSampler;

    public LRBProducer(int id, BufferedReader in, CountDownLatch controller, int inputSize,
                       LinkedBlockingQueue<LRBIntermediateRecord>[] queues, AtomicInteger keepRunning,
                       AbstractHardwareSampler hwSampler) {
        this.id = id;
        this.inputSize = inputSize;
        this.reader = in;
        this.queues = queues;
        this.keepRunningConsumers = keepRunning;
        this.latch = controller;
        this.hwSampler = hwSampler;
    }


    @Override
    public void run() {

        final int parallelism = queues.length;
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

                    int pid = Math.abs(Integer.hashCode(in.getM_iSeg()) * 31 + Integer.hashCode(in.getM_iXway())) % parallelism;
                    //int pid = Math.abs(Integer.hashCode(in.getM_iSeg()) % parallelism);
                    queues[pid].add(intermediate);
                }

            }


            if (hwSampler != null) {
                hwSampler.stopSampling("Producer " + id);
            }


        } catch (Exception ex) {
            LOG.error(ex.getMessage());
            ex.printStackTrace();
        } finally {
            keepRunningConsumers.getAndDecrement();

            for (LinkedBlockingQueue<LRBIntermediateRecord> q : queues) {
                q.add(LRBIntermediateRecord.POISONED_TUPLE);
            }

            latch.countDown();
        }

    }


}
