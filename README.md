# Bechmark Baselines

This repository, contains reference implementation of different stream processing workloads across different systems.

### System Matrix

:heavy_check_mark: done
:x: not possible

| Queries  | Flink | Spark Structured Streaming | Kafka Streams | RedPander | Timly Dataflow | Light Saber | Google Dataflow | Microsoft Stream Insighes / Trill | 
| ------------- | ------------- |------------- |------------- |------------- |------------- |------------- |------------- |------------- |
| YSB  | :heavy_check_mark: | | | | | | |
| Nextmark Q4  |  :heavy_check_mark: | | | | | | |
| Nextmark Q5  | :heavy_check_mark:  | | | | | | |
| Nextmark Q7  | :heavy_check_mark:  | | | | | | |
| Nextmark Q8  | :heavy_check_mark:  | | | | | | |
| Nextmark Q11  | :heavy_check_mark:  | | | | | | |
| ClusterMonitoring (LS)  | :heavy_check_mark:  | | | | | | |
| SmartGrid (LS)  |   | | | | | | |
| LinearRoadBenchmark (LS)  |   | | | | | | |

### Hardware:

Node-55 - Intel   
Fatnode - AMD Rayzen   
Cloud 48 - ARM   
RaspeeryPi 2 ARM

### How to run Flink's code

1. Build Java code using maven.
2. Ensure you have a properly configured Flink cluster
3. To run a query execute:

```
/path/to/flink/bin/flink run /path/to/your/jar/im-job-vanilla-benchmarks_2.11-0.1-SNAPSHOT.jar -queryName -sourceParallelism SOURCE PARALLELISM -windowParallelism WINDOW OPERATOR PARALLELISM
```

SOURCE PARALLELISM: number of threads that will execute the source operator
WINDOW OPERATOR PARALLELISM: number of threads that will execute the window operator 


#### Query Mapping

Please, have a look here: https://github.com/VenturaDelMonte/nexmark-vanilla-flink/blob/865db7ec8c3fb84883809616c9e8dad99e0f95b9/src/main/java/io/ventura/nexmark/kernel/Main.java


### How to configure Flink's cluster


```
jobmanager.rpc.address: cloud-40 ##coordinator hostame. 
taskmanager.compute.numa: true
env.java.opts: -XX:+TieredCompilation -server -XX:+UseG1GC -Dorg.apache.flink.shaded.netty4.io.netty.buffer.bytebuf.checkAccessible=false -Dorg.apache.flink.shaded.netty4.io.netty.leakDetection.level=DISABLED -Dorg.apache.flink.shaded.netty4.io.netty.allocator.tinyCacheSize=4096 -Dorg.apache.flink.shaded.netty4.io.netty.allocator.normalCacheSize=1024
taskmanager.network.memory.fraction: 0.3
taskmanager.network.memory.min: 536870912
taskmanager.network.memory.max: 8589934592
taskmanager.memory.fraction: 0.5
jobmanager.rpc.port: 6123
jobmanager.heap.size: 8gb
taskmanager.heap.size: 64g
taskmanager.numberOfTaskSlots: 10 ## change this with the number of physical cores for the worker
parallelism.default: 1
```

Please make sure you deploy the coordinator on a dedicated node and each task manager runs on a dedicated node as well.
