#include <iostream>
#include <ostream>

#include "YSB.cpp"
#include "YSB2.cpp"
// ./yahoo_benchmark_checkpoints --circular-size 16777216 --slots 128
// --batch-size 524288 --bundle-size 524288 --checkpoint-duration 1000 --threads
// 1
int main(int argc, const char **argv) {
  std::unique_ptr<BenchmarkQuery> benchmarkQuery{};

  SystemConf::getInstance().QUERY_NUM = 2;
  BenchmarkQuery::parseCommandLineArguments(argc, argv);

  if (SystemConf::getInstance().QUERY_NUM == 1) {
    std::cout << "run YSB 1" << std::endl;
    benchmarkQuery = std::make_unique<YSB>();
  } else if (SystemConf::getInstance().QUERY_NUM == 2) {
    std::cout << "run YSB 2"
              << " use campaing=" << SystemConf::getInstance().CAMPAIGNS_NUM
              << " and hash size " << SystemConf::getInstance().HASH_TABLE_SIZE
              << std::endl;

    benchmarkQuery = std::make_unique<YSB2>();
  } else {
    throw std::runtime_error("error: invalid benchmark query id");
  }

  return benchmarkQuery->runBenchmark();
}