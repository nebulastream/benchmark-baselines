#include <iostream>

#include "NBQ5.cpp"

int main(int argc, const char **argv) {
  std::unique_ptr<BenchmarkQuery> benchmarkQuery {};

  SystemConf::getInstance().QUERY_NUM = 1;
  BenchmarkQuery::parseCommandLineArguments(argc, argv);

  if (SystemConf::getInstance().QUERY_NUM == 1) {
    benchmarkQuery = std::make_unique<NBQ5>();
  } else {
    throw std::runtime_error("error: invalid benchmark query id");
  }

  return benchmarkQuery->runBenchmark();
}