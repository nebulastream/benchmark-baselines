package de.dfki.iam.lrb.fused;

import de.dfki.iam.lrb.record.*;
import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public class LRBFusedProcessorGM implements Runnable {

	private final static Logger LOG = LoggerFactory.getLogger(LRBFusedProcessorGM.class);

	private final int id, inputSize;
	private final BufferedReader reader;
	private final CountDownLatch latch;

	private final Function<ConcurrentHashMap<Integer, Double>, Void> sink;

	private final AtomicReference<ConcurrentHashMap<Integer, Double>> toolsMapG, cachedToolsMapG;

	private ConcurrentHashMap<Integer, Double> toolsMap, cachedToolsMap;

	private final AbstractHardwareSampler hwSampler;

	private final AtomicInteger pendingWindows;

	private final int workersCount;

	public LRBFusedProcessorGM(int id,
								BufferedReader in,
								CountDownLatch controller,
								AtomicReference<ConcurrentHashMap<Integer, Double>> toolsMapG,
								AtomicReference<ConcurrentHashMap<Integer, Double>> auxToolsMapG,
								AtomicInteger pendingWindows,
								int inputSize,
								Function<ConcurrentHashMap<Integer, Double>, Void> sink,
								int workers,
								AbstractHardwareSampler hwSampler) {
		this.id = id;
		this.reader = in;
		this.latch = controller;
		this.inputSize = inputSize;
		this.sink = sink;
		this.hwSampler = hwSampler;
		this.toolsMapG = toolsMapG;
		this.cachedToolsMapG = auxToolsMapG;
		this.toolsMap = toolsMapG.get();
		this.cachedToolsMap = cachedToolsMapG.get();
		this.pendingWindows = pendingWindows;
		this.workersCount = workers;
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

			int segMin = Integer.MAX_VALUE;
			int segMax = Integer.MIN_VALUE;
			int xwayMin = Integer.MAX_VALUE;
			int xwayMax = Integer.MIN_VALUE;
			int timeOfLastTool = -1;

			HashMap<Integer, Accident> accidents = new HashMap<>();
			HashMap<Integer, AvgSpeed> avgSpeed = new HashMap<>();
			HashMap<Integer, StopTuple> stopMap = new HashMap<>();

			for (int i = 0; i < inputSize; i++) {

				InputRecord in = new InputRecord(reader.readLine());

				if (in.getM_iType() == 0) {

					LRBIntermediateRecord tuple = new LRBIntermediateRecord(
							in.getM_iVid(), in.getM_iSeg(),
							in.getM_iSpeed(), in.getM_iPos(),
							in.getM_iTime(), in.getM_iXway());

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

						toolsMap = toolsMapG.get();
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
									if (!accidents.containsKey(ks)) {
										tollAmount = (2 * avg.count) ^ 2;
									}
									toolsMap.put(tuple.xway * seg, tollAmount);
								   // sink.apply("tool is " + tollAmount + " for seg "+ seg + " and xway " + tuple.xway);
									//sink.apply("average speed is " + averageSpeed + " for seg "+ seg + " and xway " + tuple.xway);
								}
							}

						}


						pendingWindows.decrementAndGet();
						if (pendingWindows.compareAndSet(0, workersCount)) {
							sink.apply(toolsMap);
							cachedToolsMapG.compareAndSet(cachedToolsMap, toolsMap);
							toolsMapG.compareAndSet(toolsMap, cachedToolsMap);
						} else {
							cachedToolsMap = cachedToolsMapG.get();
						}

						timeOfLastTool = tuple.time;
					}



				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			LOG.error(ex.getMessage());
		} finally {
			latch.countDown();
		}


	}
}
