#!/bin/bash


# prepare
mvn package
cp flink-conf.yaml ./flink-1.15.0/conf
rm -rf /flink-1.15.0/log/*

./flink-1.15.0/bin/start-cluster.sh
./flink-1.15.0/bin/flink  run  --class=de.tub.nebulastream.benchmarks.flink.linearroad.LR2 ./target/yahoo-bench-flink_2.11-0.1-SNAPSHOT.jar --parallelism $1
./flink-1.15.0/bin/stop-cluster.sh

java -cp ./target/yahoo-bench-flink_2.11-0.1-SNAPSHOT.jar de.tub.nebulastream.benchmarks.flink.utils.AnalyzeTool \
./flink-1.15.0/log/flink-pgrulich-taskexecutor-0-philipp-ubuntu.log \
lr2 \
"$1"
