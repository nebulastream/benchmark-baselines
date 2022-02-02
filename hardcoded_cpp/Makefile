make all:

#		g++ -std=c++14 -g -O0 PCMSampler.cpp linearRoad/lrb_noqueue.cpp papi_sampler.cpp -fopenmp -lpapi -lpthread -L./ -lpcm -o lrbNoQueueDbg
#		g++ -std=c++14 -g -O0 linearRoad/lrb_flink.cpp -fopenmp -lpthread  -ltbb -o lrbFlinkDbg
#		g++ -std=c++14 -g -O0 PCMSampler.cpp  linearRoad/lrb_flink_buffer.cpp papi_sampler.cpp -fopenmp -lpthread -lpapi -ltbb -L./ -lpcm -o lrbFlinkBufferDbg
#		g++ -std=c++14 -g -O0 PCMSampler.cpp  linearRoad/lrb_flink_buffer_part.cpp papi_sampler.cpp -fopenmp -lpthread  -ltbb -lpapi -L./ -lpcm -o lrbFlinkBufferPartDbg

		#g++ -std=c++14 -g -O0 -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" tupleSizePtr.cpp -fopenmp -lpthread -ltbb -lpapi -o ptrBuffTest
		#g++ -std=c++14 -g -O0 -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" tupleSizeTBBBufferSizeT.cpp -fopenmp -lpthread -ltbb -lpapi -o tupBuffTest

##		
#		g++ -std=c++0x  -g -I -O0 PCMSampler.cpp queuedApproach.cpp papi_sampler.cpp  -fopenmp -lpthread -lpapi -L./ -lpcm -o queuedApproachDGB

#		g++ -std=c++14 -g -O3 -march=native -mtune=native PCMSampler.cpp linearRoad/lrb_noqueue.cpp papi_sampler.cpp -fopenmp -lpthread -lpapi -L./ -lpcm -o lrbNoQueueRel
##		g++ -std=c++14 -g -O3 -march=native -mtune=native linearRoad/lrb_flink.cpp -fopenmp -lpthread  -ltbb -o lrbFlinkRel
#		g++ -std=c++14 -g -O3 -march=native -mtune=native PCMSampler.cpp linearRoad/lrb_flink_buffer.cpp papi_sampler.cpp -fopenmp -lpthread -lpapi -ltbb -L./ -lpcm -o lrbFlinkBufferRel
#		g++ -std=c++14 -g -O3 -march=native -mtune=native PCMSampler.cpp linearRoad/lrb_flink_buffer_part.cpp papi_sampler.cpp -fopenmp -lpapi -lpthread -L./ -lpcm -ltbb -lpapi -o lrbFlinkBufferPartRel
##	
#		
#		g++ -std=c++14 -g -O0 PCMSampler.cpp maxPhysicalTest.cpp -fopenmp -lpthread -L./ -lpcm -o pyhsicalBenchmark

#		g++ -std=c++14 -g -O0 hw_buff_lrb.cpp -fopenmp -lpthread -o hw_buff_lrb

		#g++ -std=c++14 -g -O0 maxPhysicalTest.cpp -march=native -mtune=native  -fopenmp -lpthread -o pyhsicalBenchmarkRel

		#gcc -c -O0 buffer.c -fopenmp -lpthread -o buffer

		#g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" MPMCBenchmark.cpp buffer.c -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o MPMCBenchmarkFullTuple

		#g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" MPMCBenchmarkPtr.cpp buffer.c -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o MPMCBenchmarkPtr

		#g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" MPMCBenchmarkBuffer.cpp buffer.c -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o MPMCBenchmarkBuffer

#		g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" MPMCBenchmarkBufferPtr.cpp buffer.c -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o MPMCBenchmarkBufferPtr

		#g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" SPSCBenchmark.cpp MCRingbuffer.cpp -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o SPSCBenchmark

		#g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" SPSCBenchmarkBuffer.cpp -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o SPSCBenchmarkBuffer

		#g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" SPSCBenchmarkPtr.cpp MCRingbuffer.cpp -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o SPSCBenchmarkPtr

#		g++ -std=c++14 -O0 -g -I"/home/zeuchste/git/Streaming-Hackathon-2017/postDocMagic/" SPSCBenchmarkBufferPtr.cpp MCRingbuffer.cpp -lpthread -lboost_system -lboost_thread -ltbb -lglog -lfolly -o SPSCBenchmarkBufferPtr

		#g++ -std=c++14 -O0 -g componentTest.cpp -fopenmp -lpthread -o componentTest

#		g++ -std=c++11 -I ./pcm/ -L ./pcm/cpucounters.o ./pcm/pci.o ./pcm/msr.o ./pcm/client_bw.o ./pcm/cpucounters.o -lpthread  my_code.cpp
		#g++ -std=c++14 -g -O0 queuedApproach.cpp papi_sampler.cpp -fopenmp -lpthread -lpapi -o queuedApproachDGB
		
		
#		g++ -std=c++0x  -g -I -O0 PCMSampler.cpp queuedApproach.cpp papi_sampler.cpp  -fopenmp -lpthread -lpapi -L./ -lpcm -o queuedApproachDGB
		g++ -std=c++0x  -g -O3 -march=native -mtune=native noQueueGlobal.cpp papi_sampler.cpp  -L./ -fopenmp -lpthread -lpapi -o queuedApproachGlobalRel
		g++ -std=c++0x  -g -O3 -march=native -mtune=native noQueueLocal.cpp papi_sampler.cpp  -L./  -fopenmp -lpthread -lpapi -o queuedApproachLocalRel
		g++ -std=c++0x  -g -O3 -march=native -mtune=native flink_approach.cpp papi_sampler.cpp -L./ -fopenmp -lpthread -ltbb -lpapi -o flinkApproachRel

#		g++ -std=c++0x  -g -I -O3 -march=native -mtune=native PCMSampler.cpp queuedApproach.cpp papi_sampler.cpp  -fopenmp -lpthread -lpapi -L./ -lpcm -o queuedApproachGlobalRel
#		g++ -std=c++0x  -g -I -O3 -march=native -mtune=native PCMSampler.cpp noQueueLocal.cpp papi_sampler.cpp  -fopenmp -lpthread -lpapi -L./ -lpcm -o queuedApproachLocalRel
#		g++ -std=c++0x  -g -I -O3 -march=native -mtune=native queuedApproach.cpp papi_sampler.cpp  -fopenmp -lpthread -lpapi -o queuedApproachRel

#		g++ -std=c++0x  -g -O0 PCMSampler.cpp flink_approach.cpp papi_sampler.cpp -fopenmp -lpthread -ltbb -lpapi -lpapi  -L./ -lpcm -o flinkApproachDBG
#		g++ -std=c++0x  -g -O3 -march=native -mtune=native PCMSampler.cpp flink_approach.cpp papi_sampler.cpp -fopenmp -lpthread -ltbb -lpapi  -L./ -lpcm -o flinkApproachRel
		#g++ -std=c++0x  -g -O3 -march=native -mtune=native flink_approach.cpp papi_sampler.cpp -fopenmp -lpthread -ltbb -lpapi -o flinkApproachRel		
		#g++ -std=c++14 -g -O0 flink_approachPtr.cpp -fopenmp -lpthread -ltbb -o flinkApproachPtrDBG 
		#g++ -std=c++14 -g -O0 flink_approachBuff.cpp -L/usr/local/lib -L/usr/lib -fopenmp -lpthread -ltbb -ltcmalloc_minimal -o flinkApproachBuffDBG
		#g++ -std=c++14 -g  -O3 -march=native -mtune=native flink_approachBuff.cpp  -L/usr/local/lib -L/usr/lib -fopenmp -lpthread -ltbb -ltcmalloc_minimal -o flinkApproachBuffRel		

		#g++ -std=c++14 -g  -O3 -march=native -mtune=native flink_approach.cpp -fopenmp -lpthread -ltbb -o flinkApproachTupRel

		#g++ -std=c++14 -g  -O3 -march=native -mtune=native flink_approachPtr.cpp -fopenmp -lpthread -ltbb -o flinkApproachPtrRel
		#g++ -std=c++14 -g  -O3 -march=native -mtune=native flink_approachBuff.cpp -fopenmp -lpthread -ltbb -o flinkApproachBuffRel
		#g++ -std=c++14 -g  -O3 -march=native -mtune=native flink_approachBuff.cpp -fopenmp -lpthread -ltbb -o flinkApproachBuffRel
		
		#g++ -c DataFeeder.cpp -m32  -g
		#g++ -o datafeeder DataFeeder.o -g -lpthread -pthread -m32  libLRDataProvider.a 
	

#		g++ -std=c++14 -g -O0 shuffleGenerator.cpp -fopenmp -lpthread -o shuffleGen
