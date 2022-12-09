#!/bin/bash

# prepare
#mvn package
#cp flink-conf.yaml ./flink-1.15.3/conf
rm -rf /flink-1.15.3/log/*
./flink-1.15.3/bin/stop-cluster.sh
#for i in 10
#do 
#  P=$((2 ** i))
  ./flink-1.15.3/bin/start-cluster.sh
  ./flink-1.15.3/bin/flink  run --class=de.tub.nebulastream.benchmarks.flink.multiquery.ysb.YSB ./target/yahoo-bench-flink_2.11-0.1-SNAPSHOT.jar --queries "$1"
  ./flink-1.15.3/bin/stop-cluster.sh
  timeout 60  java -cp ./target/yahoo-bench-flink_2.11-0.1-SNAPSHOT.jar de.tub.nebulastream.benchmarks.flink.utils.MultiQueryAnalyzeTool \
  "./flink-1.15.3/log/flink-pgrulich-taskexecutor-0-instance-1.log" \
  multiquery_ysb \
  "$1"
#done
