package de.dfki.iam.lrb.consumer;

import de.dfki.iam.lrb.record.Accident;
import de.dfki.iam.lrb.record.AvgSpeed;
import de.dfki.iam.lrb.record.LRBIntermediateRecord;
import de.dfki.iam.lrb.record.StopTuple;
import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

public class LRBBufferedConsumer implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(LRBBufferedConsumer.class);

    private final int id;
    private final LinkedBlockingQueue<LRBIntermediateRecord[]> inputQueue;

    private AtomicInteger keepConsuming;

    private HashMap<Long, HashMap<UUID, Long>> windows;

    private final Function<String, Void> sink;

    private final CountDownLatch latch;

    private final int bufferSize;

    private final AbstractHardwareSampler hwSampler;

    private final ConcurrentHashMap<Integer, Accident> accidents;
    private final ConcurrentHashMap<Integer, AvgSpeed> avgSpeed;


    public LRBBufferedConsumer(int id, int bufferSize,
                            LinkedBlockingQueue<LRBIntermediateRecord[]> queue,
                            CountDownLatch controller, AtomicInteger keepConsuming,
                            Function<String, Void> sinkOp, AbstractHardwareSampler hwSampler,
                            ConcurrentHashMap<Integer, Accident> acc,
                            ConcurrentHashMap<Integer, AvgSpeed> avgSpeed) {
        this.id = id;
        this.inputQueue = queue;
        this.keepConsuming = keepConsuming;
        this.sink = sinkOp;
        this.latch = controller;
        this.bufferSize = bufferSize;
        this.hwSampler = hwSampler;
        this.accidents = acc;
        this.avgSpeed = avgSpeed;
    }


    @Override
    public void run() {


        PAPIHardwareSampler hwPAPI = null;
        try {
            if (hwSampler != null) {
                hwSampler.startSampling();
            }

            HashMap<Integer, StopTuple> stopMap = new HashMap<>();
            int timeOfLastTool = -1;
            int segMin = Integer.MAX_VALUE;
            int segMax = Integer.MIN_VALUE;
            int xwayMin = Integer.MAX_VALUE;
            int xwayMax = Integer.MIN_VALUE;

            while (keepConsuming.get() > 0) {
                while (inputQueue.size() > 0) {
                    final LRBIntermediateRecord[] tuples = inputQueue.poll();
                    for (LRBIntermediateRecord tuple : tuples) {

                        if (tuple == null || tuple.equals(LRBIntermediateRecord.POISONED_TUPLE)) {
                            break; // queue poisoned
                        }

                        boolean possibleAccident = false;

                        segMin = Math.min(segMin, tuple.seg);
                        segMax = Math.max(segMax, tuple.seg);

                        xwayMin = Math.min(xwayMin, tuple.xway);
                        xwayMax = Math.max(xwayMax, tuple.xway);

                        if (tuple.speed == 0) {
                            if (stopMap.containsKey(tuple.vid)) {
                                StopTuple curr = stopMap.get(tuple.vid);
                                if (curr.pos == tuple.pos) {
                                    curr.count++;
                                    if (curr.count == 4) {
                                        possibleAccident = true;
                                    }
                                } else {
                                    stopMap.put(tuple.vid, new StopTuple(tuple.pos, 1));
                                }
                            }
                        }

                        if (possibleAccident) {
                            // signal accident
                            int k = Integer.hashCode(tuple.xway) * 31 + tuple.pos;
                            accidents.compute(k, new BiFunction<Integer, Accident, Accident>() {
                                @Override
                                public Accident apply(Integer xway, Accident accident) {
                                    if (accident == null) {
                                        return new Accident(tuple.vid, -1, tuple.time);
                                    } else {
                                        if (accident.vid2 == -1) {
                                            accident.vid2 = tuple.vid;
                                        } else if (accident.vid1 == -1) {
                                            accident.vid1 = tuple.vid;
                                        }
                                    }
                                    return accident;
                                }
                            });
                        }

                        if (tuple.speed > 0) {
                            int k = Integer.hashCode(tuple.xway) * 31 + tuple.pos;
                            if (accidents.containsKey(k)) {
                                Accident a = accidents.get(k);
                                if (a.vid1 == tuple.vid) {
                                    a.vid1 = -1;
                                } else if (tuple.vid == a.vid2) {
                                    a.vid2 = -1;
                                }
                            }
                        }

                        int k = Integer.hashCode(tuple.xway) * 31 + tuple.seg;

                        avgSpeed.computeIfPresent(k, new BiFunction<Integer, AvgSpeed, AvgSpeed>() {
                            @Override
                            public AvgSpeed apply(Integer integer, AvgSpeed avgSpeed) {
                                avgSpeed.count++;
                                avgSpeed.speed += tuple.speed;
                                return avgSpeed;
                            }
                        });

                        avgSpeed.putIfAbsent(k, new AvgSpeed(tuple.speed, 1));

                        if (tuple.time % 300 == 0 && tuple.time > 0 && timeOfLastTool != tuple.time) {

                            for (int seg = segMin; seg < segMax; seg++) {

                                int ks = Integer.hashCode(tuple.xway) * 31 + seg;

                                if (avgSpeed.containsKey(ks)) {
                                    AvgSpeed avg = avgSpeed.get(ks);
                                    double averageSpeed = 0;
                                    if (avg.count > 0) {
                                        averageSpeed = avg.speed / avg.count;
                                    }
                                    if (averageSpeed > 40) {
                                        double tollAmount = 0;
                                        if (accidents.containsKey(ks)) {
                                            tollAmount = (2 * avg.count) ^ 2;
                                        }
                                        sink.apply("tool is " + tollAmount + " for seg " + seg + " and xway " + tuple.xway);
                                        sink.apply("average speed is " + averageSpeed + " for seg " + seg + " and xway " + tuple.xway);
                                    }
                                }

                            }

                            timeOfLastTool = tuple.time;
                        }

                    }
                }

            }
            if (hwSampler != null) {
                hwSampler.stopSampling("Consumer " + id);
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage());
            ex.printStackTrace();
        } finally {
            latch.countDown();
            LOG.info("consumer " + id + " done");
        }


    }
}
