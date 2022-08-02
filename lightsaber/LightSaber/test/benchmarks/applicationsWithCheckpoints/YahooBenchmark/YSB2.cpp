#include "benchmarks/applications/YahooBenchmark/YahooBenchmark.h"
#include "compression/Compressor.h"
#include "cql/expressions/ColumnReference.h"
#include "cql/expressions/IntConstant.h"
#include "cql/operators/Aggregation.h"
#include "cql/operators/AggregationType.h"
#include "cql/operators/codeGeneration/OperatorKernel.h"
#include "cql/predicates/ComparisonPredicate.h"
#include "snappy.h"
#include "utils/Query.h"
#include "utils/QueryOperator.h"
#include "utils/WindowDefinition.h"

class YSB2 : public YahooBenchmark {
 private:
  TupleSchema *createStaticSchema() { return createStaticSchema_128(); }

  TupleSchema *createStaticSchema_128() {
    auto staticSchema = new TupleSchema(2, "Campaigns");
    auto longLongAttr = AttributeType(BasicType::LongLong);

    staticSchema->setAttributeType(0,
                                   longLongAttr); /*       ad_id:  longLong */
    staticSchema->setAttributeType(1,
                                   longLongAttr); /* campaign_id:  longLong */
    return staticSchema;
  }

  void createApplication() override {
    SystemConf::getInstance().SLOTS = 128;
    SystemConf::getInstance().PARTIAL_WINDOWS = 32;
    //    SystemConf::getInstance().HASH_TABLE_SIZE = 10000;
    std::cout << "use campaing=" << SystemConf::getInstance().CAMPAIGNS_NUM
              << " and hash size " << SystemConf::getInstance().HASH_TABLE_SIZE
              << std::endl;
    auto adsNum =
        Utils::getPowerOfTwo(SystemConf::getInstance().CAMPAIGNS_NUM * 10);
    // SystemConf::getInstance().CHECKPOINT_INTERVAL = 1000L;
    // SystemConf::getInstance().CHECKPOINT_ON =
    //     SystemConf::getInstance().CHECKPOINT_INTERVAL > 0;

    bool useParallelMerge = SystemConf::getInstance().PARALLEL_MERGE_ON;
    bool persistInput = SystemConf::getInstance().PERSIST_INPUT;

    auto window = new WindowDefinition(RANGE_BASED, 10000, 10000);

    // Configure selection predicate
    auto predicate = new ComparisonPredicate(EQUAL_OP, new ColumnReference(5),
                                             new IntConstant(0));
    Selection *selection = new Selection(predicate);

    // Configure projection
    //    std::vector<Expression *> expressions(2);
    //    // Always project the timestamp
    //    expressions[0] = new ColumnReference(0);
    //    expressions[1] = new ColumnReference(3 + incr);
    //    Projection *projection = new Projection(expressions, true);

    // Configure static hashjoin
    //    auto staticSchema = createStaticSchema();
    //    auto joinPredicate = new ComparisonPredicate(EQUAL_OP, new
    //    ColumnReference(1), new ColumnReference(0)); StaticHashJoin
    //    *staticJoin = new StaticHashJoin(joinPredicate,
    //                                                    projection->getOutputSchema(),
    //                                                    *staticSchema,
    //                                                    getStaticData(),
    //                                                    getStaticInitialization(),
    //                                                    getStaticHashTable(adsNum),
    //                                                    getStaticComputation(window));

    // Configure aggregation
    std::vector<AggregationType> aggregationTypes(1);
    aggregationTypes[0] = AggregationTypes::fromString("avg");
    //    aggregationTypes[1] = AggregationTypes::fromString("max");

    std::vector<ColumnReference *> aggregationAttributes(1);
    aggregationAttributes[0] = new ColumnReference(1, BasicType::Float);
    //    aggregationAttributes[1] = new ColumnReference(0, BasicType::Float);

    std::vector<Expression *> groupByAttributes(1);
    groupByAttributes[0] = new ColumnReference(4, BasicType::LongLong);

    Aggregation *aggregation = new Aggregation(
        *window, aggregationTypes, aggregationAttributes, groupByAttributes);

#if defined(TCP_INPUT)
    bool replayTimestamps = false;
#elif defined(RDMA_INPUT)
    bool replayTimestamps = false;
#else
    bool replayTimestamps = window->isRangeBased();
#endif
    OperatorCode *cpuCode;
    // Set up code-generated operator
    OperatorKernel *genCode =
        new OperatorKernel(true, true, useParallelMerge, true);
    genCode->setInputSchema(getSchema());
    genCode->setSelection(selection);
    //    genCode->setProjection(projection);
    //    genCode->setStaticHashJoin(staticJoin);
    genCode->setAggregation(aggregation);
    genCode->setQueryId(0);
    genCode->setup();
    cpuCode = genCode;
    std::cout << " gen code=" << genCode->toSExpr() << std::endl;

    // Print operator
    std::cout << cpuCode->toSExpr() << std::endl;

    // Define an ft-operator
    auto queryOperator = new QueryOperator(*cpuCode, true);
    std::vector<QueryOperator *> operators;
    operators.push_back(queryOperator);

    // this is used for latency measurements
    m_timestampReference =
        std::chrono::system_clock::now().time_since_epoch().count();

    std::vector<std::shared_ptr<Query>> queries(1);
    queries[0] = std::make_shared<Query>(
        0, operators, *window, m_schema, m_timestampReference, true,
        replayTimestamps, !replayTimestamps, useParallelMerge, 0, persistInput,
        nullptr, !SystemConf::getInstance().RECOVER);

#if defined(RDMA_INPUT)
    // queries[0]->getBuffer()->setFilterFP(YSBCompress::filterInput);
#endif

    if (SystemConf::getInstance().CHECKPOINT_COMPRESSION) {
      queries[0]->getBuffer()->setCompressionFP(YSBCompress::compressInput);
      queries[0]->getBuffer()->setDecompressionFP(YSBCompress::decompressInput);
    }

    m_application =
        new QueryApplication(queries, SystemConf::getInstance().CHECKPOINT_ON,
                             !SystemConf::getInstance().RECOVER);
    m_application->setup();
    if (SystemConf::getInstance().CHECKPOINT_COMPRESSION &&
        (SystemConf::getInstance().CHECKPOINT_ON || persistInput)) {
      YSBCompress::dcomp = new std::vector<std::unique_ptr<DictionaryCompressor<
          YSBCompress::Key, uint16_t, YSBCompress::hash, YSBCompress::Eq>>>();
      for (int w = 0; w < SystemConf::getInstance().WORKER_THREADS; ++w) {
        YSBCompress::dcomp->emplace_back(
            std::make_unique<
                DictionaryCompressor<YSBCompress::Key, uint16_t,
                                     YSBCompress::hash, YSBCompress::Eq>>(
                adsNum));
      }
    }
    /*if (SystemConf::getInstance().CHECKPOINT_ON &&
    SystemConf::getInstance().CHECKPOINT_COMPRESSION) {
      m_application->getCheckpointCoordinator()->setCompressionFP(0,
    YSBCompress::compress);
    }*/
  }

 public:
  YSB2(bool inMemory = true) {
//    SystemConf::getInstance().CAMPAIGNS_NUM = 100;
    SystemConf::getInstance().HASH_TABLE_SIZE =
        pow(2, ceil(log(SystemConf::getInstance().CAMPAIGNS_NUM) / log(2)));
    m_name = "YSB2";
    createSchema();
    if (inMemory) loadInMemoryData();
    createApplication();
  }
};