package de.dfki.iam.lrb;

import de.dfki.iam.lrb.consumer.LRBBufferedConsumer;
import de.dfki.iam.lrb.consumer.LRBConsumer;
import de.dfki.iam.lrb.fused.LRBFusedProcessorGM;
import de.dfki.iam.lrb.fused.LRBFusedProcessorLM;
import de.dfki.iam.lrb.producer.LRBBufferedProducer;
import de.dfki.iam.lrb.producer.LRBProducer;
import de.dfki.iam.lrb.record.Accident;
import de.dfki.iam.lrb.record.AvgSpeed;
import de.dfki.iam.lrb.record.LRBIntermediateRecord;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import de.dfki.iam.yahoo.hardware.pcm.PCMHardwareSampling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class LinearRoadBenchmark {

	private static final Logger LOG = LoggerFactory.getLogger(LinearRoadBenchmark.class);



	public static void main(String[] args) throws Exception {

		int executedFused = Integer.parseInt(args[0]); // 0: partitioned, 1: gm, 2: lm
		int enableSampling = Integer.parseInt(args[1]); // 0: no sampling, 1: papi, 2: pcm
		int bufferSize = Integer.parseInt(args[2]);
		int parallelism = Integer.parseInt(args[3]);
		int numRuns = Integer.parseInt(args[4]);

		if (bufferSize > 0 && executedFused > 0) {
			LOG.info("cant use buffer when fusing");
			System.exit(1);
		}

		PCMHardwareSampling globalSampler = null;

		if (enableSampling == 2) {
			globalSampler = new PCMHardwareSampling(null);
		}


		if (globalSampler != null) {
			globalSampler.startSampling();
		}

		for (int r = 0; r < numRuns; ++r) {

			int totalLines = 0;

			if (executedFused == 1) {

				Function<ConcurrentHashMap<Integer, Double>, Void> sink = o -> null;

				LRBFusedProcessorGM[] processors = new LRBFusedProcessorGM[parallelism];

				CountDownLatch controller = new CountDownLatch(parallelism);
				ExecutorService pool = Executors.newFixedThreadPool(parallelism);

				ConcurrentHashMap<Integer, Double> primary = new ConcurrentHashMap<>();
				ConcurrentHashMap<Integer, Double> secondary = new ConcurrentHashMap<>();

				AtomicReference<ConcurrentHashMap<Integer, Double>> primaryRef =
						new AtomicReference<>(primary);
				AtomicReference<ConcurrentHashMap<Integer, Double>> secondaryRef =
						new AtomicReference<>(secondary);

				AtomicInteger pendingWindows = new AtomicInteger(parallelism);

				for (int i = 5, j = 0; i < args.length && j < parallelism; i += 2, j++) {
					FileInputStream fis = null;
					BufferedReader bis = null;
					try {
						fis = new FileInputStream(args[i]);
						bis = new BufferedReader(new InputStreamReader(fis));

						totalLines += Integer.parseInt(args[i + 1]);

						processors[j] = new LRBFusedProcessorGM(j, bis,
								controller, primaryRef, secondaryRef,
								pendingWindows, Integer.parseInt(args[i + 1]), sink, parallelism,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);

						LOG.info("FusedProcessor {} configured with file {}", j, args[i]);

					} catch (Exception ex) {
						LOG.error(ex.getMessage());
						System.exit(-1);
					} finally {

					}

				}

				long start = System.currentTimeMillis();
				for (LRBFusedProcessorGM fp : processors) {
					pool.execute(fp);
				}

				controller.await();

				long end = System.currentTimeMillis();
				if (globalSampler != null) {
					globalSampler.stopSampling("Fused GM approach " + parallelism);
				}


				double diff = end - start;
				LOG.info("Runtime {} exp throughput {} records/sec parallism {}",
						diff, totalLines * 1000.0 / diff, parallelism);

				LOG.info("GREPTHIS :: {} {} {} {} {} GM", parallelism, totalLines, diff, totalLines * 1000.0 / diff, bufferSize);

				pool.shutdown();

			} else if (executedFused == 2) {
				Function<HashMap<Integer, Double>, Void> sink = o -> null;

				LRBFusedProcessorLM[] processors = new LRBFusedProcessorLM[parallelism];

				CountDownLatch controller = new CountDownLatch(parallelism);
				ExecutorService pool = Executors.newFixedThreadPool(parallelism);

				HashMap<Integer, Double> mainMap = new HashMap<>();

				for (int i = 5, j = 0; i < args.length && j < parallelism; i += 2, j++) {
					FileInputStream fis = null;
					BufferedReader bis = null;
					try {
						fis = new FileInputStream(args[i]);
						bis = new BufferedReader(new InputStreamReader(fis));

						totalLines += Integer.parseInt(args[i + 1]);

						processors[j] = new LRBFusedProcessorLM(j, bis,
								controller, mainMap, Integer.parseInt(args[i + 1]), sink,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);

						LOG.info("FusedProcessorLM {} configured with file {}", j, args[i]);

					} catch (Exception ex) {
						LOG.error(ex.getMessage());
						System.exit(-1);
					} finally {

					}

				}

				long start = System.currentTimeMillis();
				for (LRBFusedProcessorLM fp : processors) {
					pool.execute(fp);
				}

				controller.await();

				long end = System.currentTimeMillis();
				if (globalSampler != null) {
					globalSampler.stopSampling("Fused LM approach " + parallelism);
				}


				double diff = end - start;
				LOG.info("Runtime {} exp throughput {} records/sec parallism {}",
						diff, totalLines * 1000.0 / diff, parallelism);

				LOG.info("GREPTHIS :: {} {} {} {} {} LM", parallelism, totalLines, diff, totalLines * 1000.0 / diff, bufferSize);

				pool.shutdown();
			} else {

				CountDownLatch controller = new CountDownLatch(2 * parallelism);
				ExecutorService pool = Executors.newFixedThreadPool(2 * parallelism);
				AtomicInteger keepRunning = new AtomicInteger(parallelism);
				Function<String, Void> sink = o -> null;

				Runnable[] consumers = new Runnable[parallelism];
				Runnable[] producers = new Runnable[parallelism];

				ConcurrentHashMap<Integer, Accident> acc = new ConcurrentHashMap<>();
				ConcurrentHashMap<Integer, AvgSpeed> avgSpeed = new ConcurrentHashMap<>();


				LinkedBlockingQueue[] queues = (bufferSize == 0)
						? (LinkedBlockingQueue<LRBIntermediateRecord>[]) new LinkedBlockingQueue[parallelism]
						: (LinkedBlockingQueue<LRBIntermediateRecord[]>[]) new LinkedBlockingQueue[parallelism];


				for (int i = 5, j = 0; i < args.length && j < parallelism; i += 2, j++) {

					queues[j] = new LinkedBlockingQueue<>();

					consumers[j] = (bufferSize > 0)
							? new LRBBufferedConsumer(j, bufferSize,
							queues[j], controller, keepRunning, sink,
							(enableSampling == 1)
									? new PAPIHardwareSampler(args[args.length - 1])
									: null, acc, avgSpeed)
							: new LRBConsumer(j, queues[j],
							controller, keepRunning, sink,
							(enableSampling == 1)
									? new PAPIHardwareSampler(args[args.length - 1])
									: null, acc, avgSpeed);

					FileInputStream fis = null;
					BufferedReader bis = null;
					try {
						fis = new FileInputStream(args[i]);
						bis = new BufferedReader(new InputStreamReader(fis));

						totalLines += Integer.parseInt(args[i + 1]);

						producers[j] = (bufferSize > 0)
								? new LRBBufferedProducer(j, bufferSize, bis,
								controller, Integer.parseInt(args[i + 1]), queues, keepRunning,
								(enableSampling == 1)
										? new PAPIHardwareSampler(args[args.length - 1])
										: null)
								: new LRBProducer(j, bis, controller,
								Integer.parseInt(args[i + 1]), queues, keepRunning,
								(enableSampling == 1)
										? new PAPIHardwareSampler(args[args.length - 1])
										: null);

						LOG.info("Producer {} configured with file {}", j, args[i]);

					} catch (Exception ex) {
						ex.printStackTrace();
						LOG.error(ex.getMessage());
						System.exit(-1);
					} finally {

					}

				}

				long start = System.currentTimeMillis();

				for (int j = 0; j < parallelism; j++) {
					pool.execute(consumers[j]);
					pool.execute(producers[j]);
				}

				controller.await();
				long end = System.currentTimeMillis();
				if (globalSampler != null) {
					globalSampler.stopSampling("Queued approach " + parallelism);
				}


				double diff = end - start;
				LOG.info("Runtime {} exp throughput {} records/sec parallism {}",
						diff, totalLines * 1000.0 / diff, parallelism);

				LOG.info("GREPTHIS :: {} {} {} {} {} UP", parallelism, totalLines, diff, totalLines * 1000.0 / diff, bufferSize);


				pool.shutdown();


			}

		}




	}



}
