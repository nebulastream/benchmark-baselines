package de.dfki.iam.yahoo;

import de.dfki.iam.yahoo.consumer.BufferedConsumer;
import de.dfki.iam.yahoo.consumer.OffheapConsumer;
import de.dfki.iam.yahoo.consumer.Consumer;
import de.dfki.iam.yahoo.fused.*;
import de.dfki.iam.yahoo.hardware.papi.PAPIHardwareSampler;
import de.dfki.iam.yahoo.hardware.pcm.PCMHardwareSampling;
import de.dfki.iam.yahoo.producer.BufferedProducer;
import de.dfki.iam.yahoo.producer.OffheapProducer;
import de.dfki.iam.yahoo.producer.Producer;
import de.dfki.iam.yahoo.record.IntermediateTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class YahooStreamingBenchmark {


	private static final Logger LOG = LoggerFactory.getLogger(YahooStreamingBenchmark.class);

	enum OptimizationMode {
		none,
		staticMethods,
		offheap
	}

	public static void main(String[] args) throws Exception {

		int inputSize = Integer.parseInt(args[0]);
		boolean executedFusedGlobal = args[1].startsWith("1");
		boolean executedFusedLocal = args[1].startsWith("2");

		int enableSampling = Integer.parseInt(args[2]); // 0: no sampling, 1: papi, 2: pcm
		int bufferSize = Integer.parseInt(args[3]);
		int parallelism = Integer.parseInt(args[4]);
		int numRuns = Integer.parseInt(args[5]);
		OptimizationMode optimizationMode = OptimizationMode.none;

		if (args[6].matches("-no-opt")) {
			optimizationMode = OptimizationMode.none;
		} else if (args[6].matches("-static-methods")) {
			optimizationMode = OptimizationMode.staticMethods;
		} else if (args[6].matches("-offheap")) {
			optimizationMode = OptimizationMode.offheap;
		}

		ByteBuffer[] cachedBuffer = new ByteBuffer[parallelism];

		int bsize = 78 * inputSize;

		if (bufferSize > 0 && executedFusedGlobal) {
			LOG.info("cant use buffer when fusing");
			System.exit(1);
		}

		PCMHardwareSampling globalSampler = null;

		if (enableSampling == 2) {
			globalSampler = new PCMHardwareSampling(null);
		}

		if (executedFusedGlobal) {

			Function<AtomicLong[], Void> sink = o -> null;

			Runnable[] processors = new Runnable[parallelism];

			CyclicBarrier controller = new CyclicBarrier(parallelism + 1);
			ExecutorService pool = Executors.newFixedThreadPool(parallelism);

			AtomicLong[][] windows = new AtomicLong[10][10000];

			for (int i = 0; i < 10; i++) {
				for (int j = 0; j < 10000; j++) {
					windows[i][j] = new AtomicLong(0L);
				}
			}

			for (int i = 7, j = 0; i < args.length && j < parallelism; i++, j++) {
				byte[] buffer = new byte[bsize];
				FileInputStream fis = null;
				BufferedInputStream bis = null;
				try {
					fis = new FileInputStream(args[i]);
					bis = new BufferedInputStream(fis, bsize);
					bis.read(buffer, 0, bsize);

					cachedBuffer[j] = ByteBuffer.wrap(buffer);
					cachedBuffer[j].order(ByteOrder.LITTLE_ENDIAN);

					switch(optimizationMode) {
					case none:
						processors[j] = new FusedProcessorGlobal(j, 1000, cachedBuffer[j],
								controller, inputSize, sink, windows,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);
						break;
					case staticMethods:
						processors[j] = new FusedProcessorGlobalStaticMethods(j, 1000, cachedBuffer[j],
								controller, inputSize, sink, windows,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);
						break;
					case offheap:
						processors[j] = new FusedProcessorGlobalOffheapHashMap(j, 1000, cachedBuffer[j],
								controller, inputSize, sink, windows,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);
						break;
					}

					LOG.info("FusedProcessorGlobal {} configured with file {}", j, args[i]);

				} catch (Exception ex) {
					LOG.error(ex.getMessage());
					System.exit(-1);
				} finally {
					if (bis != null) {
						bis.close();
					}
					if (fis != null) {
						fis.close();
					}
				}

			}
			//			System.out.println("Press a key");
			//			System.in.read();

			for (int r = 0; r < numRuns; ++r) {

				for (int i = 0; i < 10; i++) {
					for (int j = 0; j < 10000; j++) {
						windows[i][j].set(0);
					}
				}

				for (ByteBuffer b : cachedBuffer) {
					b.rewind();
				}

				long start = System.currentTimeMillis();
				if (globalSampler != null) {
					globalSampler.startSampling();
				}
				for (Runnable fp : processors) {
					pool.execute(fp);
				}

				controller.await();

				long end = System.currentTimeMillis();
				if (globalSampler != null) {
					globalSampler.stopSampling("FusedProcessorGlobal approach " + parallelism);
				}

				double diff = end - start;
				LOG.info("Runtime {} exp throughput {} records/sec parallism {}",
						diff, inputSize * parallelism * 1000.0 / diff, parallelism);

			}

			pool.shutdown();


		}
		else if (executedFusedLocal) {

			Function<AtomicLong[], Void> sink = o -> null;

			Runnable[] processors = new Runnable[parallelism];

			CyclicBarrier controller = new CyclicBarrier(parallelism + 1);
			ExecutorService pool = Executors.newFixedThreadPool(parallelism);

			AtomicLong[][] windows = new AtomicLong[20][10000];

			for (int i = 0; i < 20; i++) {
				for (int j = 0; j < 10000; j++) {
					windows[i][j] = new AtomicLong(0L);
				}
			}

			for (int i = 7, j = 0; i < args.length && j < parallelism; i++, j++) {
				byte[] buffer = new byte[bsize];
				FileInputStream fis = null;
				BufferedInputStream bis = null;
				try {
					fis = new FileInputStream(args[i]);
					bis = new BufferedInputStream(fis, bsize);
					bis.read(buffer, 0, bsize);

					cachedBuffer[j] = ByteBuffer.wrap(buffer);
					cachedBuffer[j].order(ByteOrder.LITTLE_ENDIAN);

					switch(optimizationMode) {
					case none:
						processors[j] = new FusedProcessorLocal(j, 1000, cachedBuffer[j],
								controller, inputSize, sink, windows,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);
						break;
					case staticMethods:
						processors[j] = new FusedProcessorLocalStaticMethods(j, 1000, cachedBuffer[j],
								controller, inputSize, sink, windows,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);
						break;
					case offheap:
						processors[j] = new FusedProcessorLocalOffheap(j, 1000, cachedBuffer[j],
								controller, inputSize, sink, windows,
								(enableSampling == 1) ? new PAPIHardwareSampler(args[args.length - 1]) : null);
						break;
					}

					LOG.info("FusedProcessorLocal {} configured with file {}", j, args[i]);

				} catch (Exception ex) {
					LOG.error(ex.getMessage());
					System.exit(-1);
				} finally {
					if (bis != null) {
						bis.close();
					}
					if (fis != null) {
						fis.close();
					}
				}

			}
			//			System.out.println("Press a key");
			//			System.in.read();

			for (int r = 0; r < numRuns; ++r) {

				for (int i = 0; i < 10; i++) {
					for (int j = 0; j < 10000; j++) {
						windows[i][j].set(0);
					}
				}

				for (ByteBuffer b : cachedBuffer) {
					b.rewind();
				}

				long start = System.currentTimeMillis();
				if (globalSampler != null) {
					globalSampler.startSampling();
				}
				for (Runnable fp : processors) {
					pool.execute(fp);
				}

				controller.await();

				long end = System.currentTimeMillis();
				if (globalSampler != null) {
					globalSampler.stopSampling("FusedProcessorLocal approach " + parallelism);
				}


				double diff = end - start;
				LOG.info("Runtime {} exp throughput {} records/sec parallism {}",
						diff, inputSize * parallelism * 1000.0 / diff, parallelism);

			}

			pool.shutdown();


		}

		else {
			for (int r = 0; r < numRuns; ++r) {

				CyclicBarrier controller = new CyclicBarrier(2 * parallelism + 1);
				ExecutorService pool = Executors.newFixedThreadPool(2 * parallelism);
				AtomicInteger keepRunning = new AtomicInteger(parallelism);
				Function<Map<UUID, Long>, Void> sink = o -> null;
				Function<Map<Long, Long>, Void> offheapSink = o -> null;

				Runnable[] consumers = new Runnable[parallelism];
				Runnable[] producers = new Runnable[parallelism];


				LinkedBlockingQueue[] queues;
				if (optimizationMode == OptimizationMode.offheap) {
					queues = (LinkedBlockingQueue<ByteBuffer>[]) new LinkedBlockingQueue[parallelism];
				} else if (bufferSize == 0) {
					queues = (LinkedBlockingQueue<IntermediateTuple>[]) new LinkedBlockingQueue[parallelism];
				} else {
					queues = (LinkedBlockingQueue<IntermediateTuple[]>[]) new LinkedBlockingQueue[parallelism];
				}

				System.out.println("length=" + args.length);
				for (int i = 7, j = 0; i <= args.length && j < parallelism; i++, j++) {

					queues[j] = new LinkedBlockingQueue<>();

					if (optimizationMode == OptimizationMode.offheap) {
						consumers[j] = new OffheapConsumer(j, 1000, bufferSize,
								queues[j], controller, keepRunning, offheapSink,
								(enableSampling == 1)
								? new PAPIHardwareSampler(args[args.length - 1])
										: null);
					} else {
						consumers[j] = (bufferSize > 0)
								? new BufferedConsumer(j, 1000, bufferSize,
										queues[j], controller, keepRunning, sink,
										(enableSampling == 1)
										? new PAPIHardwareSampler(args[args.length - 1])
												: null)
										: new Consumer(j, 1000, queues[j],
												controller, keepRunning, sink,
												(enableSampling == 1)
												? new PAPIHardwareSampler(args[args.length - 1])
														: null);
					}

					byte[] buffer = new byte[bsize];
					FileInputStream fis = null;
					BufferedInputStream bis = null;
					try {
						fis = new FileInputStream(args[i]);
						bis = new BufferedInputStream(fis, bsize);
						bis.read(buffer, 0, bsize);

						cachedBuffer[j] = ByteBuffer.wrap(buffer);
						cachedBuffer[j].order(ByteOrder.LITTLE_ENDIAN);

						if (optimizationMode == OptimizationMode.offheap) {
							producers[j] = new OffheapProducer(j, (bufferSize == 0) ? 1 : bufferSize, cachedBuffer[j],
									controller, inputSize, queues, keepRunning,
									(enableSampling == 1)
									? new PAPIHardwareSampler(args[args.length - 1])
											: null);
						} else {
							producers[j] = (bufferSize > 0)
									? new BufferedProducer(j, bufferSize, cachedBuffer[j],
											controller, inputSize, queues, keepRunning,
											(enableSampling == 1)
											? new PAPIHardwareSampler(args[args.length - 1])
													: null)
											: new Producer(j, cachedBuffer[j], controller,
													inputSize, queues, keepRunning,
													(enableSampling == 1)
													? new PAPIHardwareSampler(args[args.length - 1])
															: null);
						}

						LOG.info("Producer {} configured with file {}", j, args[i]);

					} catch (Exception ex) {
						LOG.error(ex.getMessage());
						System.exit(-1);
					} finally {
						if (bis != null) {
							bis.close();
						}
						if (fis != null) {
							fis.close();
						}
					}

				}//end of for each thread
				//			System.out.println("Press a key");
				//			System.in.read();

//				for (int r = 0; r < numRuns; ++r) {

					for (ByteBuffer b : cachedBuffer) {
						b.rewind();
					}

					for (LinkedBlockingQueue<Object> q : queues) {
						q.clear();
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
							diff, inputSize * parallelism * 1000.0 / diff, parallelism);
					pool.shutdown();

				}




			}




		}



	}
