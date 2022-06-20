#!/bin/bash

# prepare
mvn package
cp flink-conf.yaml ./flink-1.15.0/conf
rm -rf /flink-1.15.0/log/*

for i in 10
do
  ./flink-1.15.0/bin/start-cluster.sh
  ./flink-1.15.0/bin/flink  run --class=de.tub.nebulastream.benchmarks.flink.multiquery.ysb.YSB ./target/yahoo-bench-flink_2.11-0.1-SNAPSHOT.jar --queries "$i"
  ./flink-1.15.0/bin/stop-cluster.sh
  java -cp ./target/yahoo-bench-flink_2.11-0.1-SNAPSHOT.jar de.tub.nebulastream.benchmarks.flink.utils.MultiQueryAnalyzeTool \
  "./flink-1.15.0/log/flink-zeuchste-ldap -taskexecutor-0-sr630-wn-a-55.log" \
  multiquery_ysb \
  "$i"
done