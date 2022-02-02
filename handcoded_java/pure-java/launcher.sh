#! /bin/bash

NUM_ITEMS=1000
FILES=""
NUM_RUNS=1


for ((p=0; p<$NUM_RUNS; p++))
do  
	#FILES="$FILES /tmp/yahoo_query$p.bin"
	#FILES="$FILES /local-ssd/zeuchste/flink/data/ysb$p.bin"
	FILES="$FILES /home/zeuchste/git/Streaming-Hackathon-2017/yahoo_benchmark/generator/ysb$p.bin" 
	echo "Running DOP=$p queue-based"
	echo " run= $NUM_ITEMS 0 0 0 `expr $p + 1` $FILES"
	java -Xms28672m -Xmx28672m -jar target/yahoo-query-0.1.0.jar $NUM_ITEMS 0 0 0 `expr $p + 1` p $FILES
	echo "Running DOP=$p queue-less"
	java -Xms28672m -Xmx28672m -jar target/yahoo-query-0.1.0.jar $NUM_ITEMS 1 0 0 `expr $p + 1` p $FILES

	echo "Running DOP=$p queue-based with buffer size 1000"
	    java -Xms28672m -Xmx28672m -jar target/yahoo-query-0.1.0.jar $NUM_ITEMS 0 0 1000 `expr $p + 1` p $FILES

    # for buff_size in 10 100 1000 10000 100000
    # do
    #     echo "Running DOP=$p queue-based with buffer size $buff_size"
	   #  java -Xms28672m -Xmx28672m -jar target/yahoo-query-0.1.0.jar $NUM_ITEMS 0 0 1000 `expr $p + 1` $FILES
    # done

done


#inputSize 1000000
#executedFusedGlobal || executedFusedLocal
#enableSampling
#bufferSize
#parallelism
#numRuns
#optimizationMode -no-opt -static-methods -offheap
#files


java -Xms28672m -Xmx28672m -jar target/yahoo-query-0.1.0.jar 1000000 0 0 0 8 p $FILES
