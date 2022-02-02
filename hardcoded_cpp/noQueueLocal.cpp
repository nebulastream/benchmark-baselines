#include <iostream>
#include <cstring>
#include <omp.h>
#include <chrono>
#include <random>
#include <bitset>
#include <algorithm>
#include <atomic>
#include <array>
#include <assert.h>
#include <sstream>
#include <fstream>
#include "papi_sampler.hpp"
#include "PCMSampler.hpp"

using namespace std;
typedef uint64_t Timestamp;
using NanoSeconds = std::chrono::nanoseconds;
using Clock = std::chrono::high_resolution_clock;
const int READ_FILE_BUFFERSIZE = 4056;//52 tupes
const int REC_PER_BUFFER = 52;//0,8KB
//#define PCMACTIVE
//#define fastExecution

struct __attribute__((packed)) record {
    uint8_t user_id[16];
    uint8_t page_id[16];
    uint8_t campaign_id[16];
    char event_type[9];
    char ad_type[9];
    int64_t current_ms;
    uint32_t ip;

    record(){
    	event_type[0] = '-';//invalid record
    	current_ms = 0;
    	ip = 0;
    }

    record(const record& rhs)
    {
    	memcpy(&user_id, &rhs.user_id, 16);
    	memcpy(&page_id, &rhs.page_id, 16);
    	memcpy(&campaign_id, &rhs.campaign_id, 16);
    	memcpy(&event_type, &rhs.event_type, 9);
    	memcpy(&ad_type, &rhs.ad_type, 9);
    	current_ms = rhs.current_ms;
    	ip = rhs.current_ms;
    }


};//size 78 bytes

union tempHash
{
	uint64_t value;
	char buffer[8];
};

struct Tuple
{
	Tuple()
	{
		campaign_id[0] = '-';//empty campain
		//campaign_id = 0;
		timeStamp = std::numeric_limits<std::size_t>::max();
	}
	Tuple(uint8_t* pCampaign_id, size_t pTimeStamp)
	{
		memcpy(campaign_id, pCampaign_id, 16);
		//memcpy(&campaign_id, pCampaign_id, sizeof(long));
		timeStamp = pTimeStamp;
	}
	//size_t campaign_id;
	uint8_t campaign_id[16];

	size_t timeStamp;
};//size 16 Byte


inline size_t hashToPos(unsigned char *str, size_t size, size_t modulo)
{
	size_t h = 0;
	size_t cnt = 0;
	while (cnt < size)
	{
	   h = h << 1 ^ *str++;
	   cnt++;
	}
	return h % modulo;
}

size_t hashToPosFast(const unsigned char *str, size_t size, size_t modulo)
{
	hash<string> hasher;
	string s(reinterpret_cast<const char*>(str));

	size_t hash = hasher(s);
//
//	size_t sum = 0;
//	for(size_t i = 0; i < size; i++)
//	{
//		sum += str[i];
//	}
//
//	str[7f] = 0;
	cout << "sum=" << hash % modulo << " char=" << str << endl;
}

void shuffle(record* array, size_t n)
{
    if (n > 1)
    {
        size_t i;
        for (i = 0; i < n - 1; i++)
        {
          size_t j = i + rand() / (RAND_MAX / (n - i) + 1);
          record t = array[j];
          array[j] = array[i];
          array[i] = t;
        }
    }
}

static const std::string events[] = {"view", "click", "purchase"};
void generate(record& data, size_t campaingOffset, uint64_t campaign_lsb, uint64_t campaign_msb, size_t event_id)
{
  event_id = event_id % 3;
  /**
   * struct __attribute__((packed)) record {
      uint8_t user_id[16]; //random
      uint8_t page_id[16]; // random
      uint8_t campaign_id[16]; OK
      char event_type[9]; circular: view, clieck, purchase
      char ad_type[9]; Always banner78
      int64_t current_ms; current time
      uint32_t ip; 0x01020304
  };//size 78 bytes
   */

//  record data;
//  auto uid = ugenerator();
//  memcpy(data.user_id, &uid, 16);
//
//  auto pid = ugenerator();
//  memcpy(data.page_id, &pid, 16);

  memcpy(data.campaign_id, &campaign_msb, 8);

  uint64_t campaign_lsbr = campaign_lsb + campaingOffset;
  memcpy(&data.campaign_id[8], &campaign_lsbr, 8);

//  std::bitset<8> ls(data.campaign_id[8]);
//  std::bitset<8> ms(data.campaign_id[0]);
//  std::cout << " ms2=" << ms << " ls2=" << ls << endl;

//  cout << "generatred campid=" << data.campaign_id << endl;

  const char* str = events[event_id].c_str();
  //data.ad_type = "banner78";
  strcpy(&data.ad_type[0], "banner78");
  strcpy(&data.event_type[0], str);

  auto ts = std::chrono::system_clock::now().time_since_epoch();
  data.current_ms = std::chrono::duration_cast<std::chrono::milliseconds>(ts).count();

  data.ip = 0x01020304;
}

Timestamp getTimestamp()
{
  return std::chrono::duration_cast<NanoSeconds>(
			 Clock::now().time_since_epoch())
	  .count();
}


size_t process_window_mem_local_read_only(size_t processCnt, size_t randomCnt, size_t* randomNumbers,
		 uint64_t campaign_lsb, uint64_t campaign_msb ,size_t campaingCnt, std::atomic<size_t>** hashTable,
		 record* records, size_t windowSizeInSec, size_t htNo, std::atomic<size_t>* finalResultHT)
{
	size_t sum = 0;
	for(size_t i = 0; i < processCnt; i++)
	{
		uint8_t value = *((uint8_t*) records[i].user_id);
		sum += value;
		uint8_t value2 = *((uint8_t*) records[i].ad_type);
		sum += value2;
	}
	return sum;
}
size_t process_window_mem_local(size_t processCnt, size_t randomCnt, size_t* randomNumbers,
		 uint64_t campaign_lsb, uint64_t campaign_msb ,size_t campaingCnt, std::atomic<size_t>** hashTable,
		 record* records, size_t windowSizeInSec, size_t htNo, std::atomic<size_t>* finalResultHT)
{
#ifndef fastExecution
	size_t generated = 0;
	size_t processed = 0;
	size_t disQTuple = 0;
	size_t qualTuple = 0;
	size_t windowSwitchCnt = 0;
	size_t htReset = 0;
#endif
	size_t lastTimeStamp = time(NULL);
	size_t current_window = 0;

	for(size_t i = 0; i < processCnt; i++)
	{
		uint32_t value = *((uint32_t*) records[i].event_type);

//		if(value != 2003134838)
		if(strcmp(records[i].event_type, "view") != 0)
		{
#ifndef fastExecution
			processed++;
			disQTuple++;
#endif
			continue;
		}
#ifndef fastExecution

		qualTuple++;
		processed++;
#endif
		size_t timeStamp = time(NULL);//seconds elapsed since 00:00 hours, Jan 1, 1970 UTC

		//TODO: add watermark if new window
		if(lastTimeStamp != timeStamp && timeStamp % windowSizeInSec == 0)
		{
			//increment to new window
			if(current_window == 0)
				current_window = 1;
			else
				current_window = 0;

#ifndef fastExecution
			windowSwitchCnt++;
#endif
			// the reset has only take place once because we use a shared hash table
			// we put the timestamp of the last reset at the last+1 position in the hash
			//reset hash table
			//TODO: sync this because of shared hash table
			if(hashTable[htNo+current_window][campaingCnt] != timeStamp)
			{
#ifndef fastExecution
				htReset++;
#endif
				hashTable[htNo+current_window][campaingCnt] = timeStamp;
				std::fill(hashTable[htNo+current_window], hashTable[htNo+current_window]+campaingCnt, 0);
				//memset(myarray, 0, N*sizeof(*myarray)); // TODO: is it faster?
			}

//			cout << "switch window" << endl;
			//TODO: add output result
			lastTimeStamp = timeStamp;
		}

		//consume one tuple
		tempHash hashValue;
		hashValue.value = *(((uint64_t*) records[i].campaign_id) + 1);
		uint64_t bucketPos = (hashValue.value * 789 + 321)% campaingCnt;

		hashTable[htNo+current_window][bucketPos] += 1;

	}//end of for each tuple
#ifndef fastExecution
	stringstream ss;
	ss << "Thread=" << omp_get_thread_num() << " generated="  << generated << " processed=" << processed
			<< " disQTuple=" << disQTuple << " qualTuple=" << qualTuple
			<< " windowSwitchCnt=" << windowSwitchCnt
			<< " htreset=" << htReset
			<< " input array=" << &records << endl;
	cout << ss.str() << endl;
#endif
	for(size_t i = 0; i < campaingCnt; i++)
	{
		finalResultHT[i] += hashTable[htNo+current_window][i];
	}
	cout << "done merging"<< endl;
    return processed;
}


int main(int argc, char *argv[])
{
//	cout << "usage processCnt threadCnt preGen/noPreGen batch/window filepath/mem joint_input/individual_input papiFile papi seq" << endl;
	cout << "usage processCnt threadCnt PAPI|PCM [papiFile papi seq]" << endl;

	//initialze
	const size_t campaingCnt = 10000;

	std::random_device rd;  //Will be used to obtain a seed for the random number engine
	std::mt19937 gen(rd()); //Standard mersenne_twister_engine seeded with rd()
	std::uniform_int_distribution<size_t> diss(0, SIZE_MAX);

	size_t processCnt = 100;
	size_t threadCnt = 2;
	bool usePreGenerated = true;
	bool batching = false;
	size_t windowSizeInSeconds = 5;
	char* filePath;
	bool memLoad = true;
	bool jointInput = false;
	string papiFile;
	string papiSeq;
	bool papiSampling = false;
	bool pcmSampling = false;
	char papiStr[] = "PAPI";
	char pcmStr[] = "PCM";

	if(argc != 1)
		{
			processCnt = atoi(argv[1]);
			threadCnt = atoi(argv[2]);
			if(argv[3] != 0)
			{
				if(strcmp(argv[3],papiStr) == 0)
				{
					papiSampling = true;
					papiFile = argv[4];
					papiSeq = argv[5];
					cout << "papiFile=" << papiFile << " papi seq= " << papiSeq << endl;
				}
				else if(strcmp(argv[3],pcmStr) == 0)
				{
					pcmSampling = true;
				}
			}

		}


//Generator Code
	size_t randomCnt = processCnt/10;
	size_t* randomNumbers = new size_t[randomCnt];
	std::uniform_int_distribution<size_t> disi(0, campaingCnt);
	for(size_t i = 0; i < randomCnt; i++)
		randomNumbers[i] = disi(gen);

	record** recs;
	uint64_t campaign_lsb, campaign_msb;
	auto uuid = diss(gen);
	uint8_t* uuid_ptr = reinterpret_cast<uint8_t*>(&uuid);
	memcpy(&campaign_msb, uuid_ptr, 8);
	memcpy(&campaign_lsb, uuid_ptr + 8, 8);
	campaign_lsb &= 0xffffffff00000000;
//	std::bitset<8> ls(campaign_lsb);
//	std::bitset<8> ms(campaign_msb);

	if(usePreGenerated && memLoad)
	{
		if(!jointInput)
		{
			recs = new record*[threadCnt];
			for(size_t thr = 0; thr < threadCnt; thr++)
			{
				recs[thr] = new record[processCnt];

				for(size_t i = 0; i < processCnt; i++)
				{
					generate(recs[thr][i], /**campaingOffset*/ randomNumbers[i%randomCnt], campaign_lsb, campaign_msb, /**eventID*/ i);
				}
//				cout << "first before=" << recs[thr][0].event_type << "," << recs[thr][1].event_type << "," << recs[thr][2].event_type << "," << recs[thr][3].event_type << "," <<
//						recs[thr][4].event_type << "," << recs[thr][6].event_type << "," << endl;
				//shufle
				shuffle(recs[thr], processCnt);
//				cout << "after before=" << recs[thr][0].event_type << "," << recs[thr][1].event_type << "," << recs[thr][2].event_type << "," << recs[thr][3].event_type << "," <<
//										recs[thr][4].event_type << "," << recs[thr][6].event_type << "," << endl;
			}
		}
		else
		{
			recs = new record*[1];
			recs[0] = new record[processCnt];
			for(size_t i = 0; i < processCnt; i++)
			{
				generate(recs[0][i], /**campaingOffset*/ randomNumbers[i%randomCnt], campaign_lsb, campaign_msb, /**eventID*/ i);
			}
		}

	}

	std::vector<string> fileNames;
	if(!memLoad && !jointInput)//file load from individual files
	{
		//requirement file format: finale_id.bin, id 0...threadCnt
		for(size_t i = 0; i < threadCnt; i++)
		{
			std::string path(filePath);
			path = path.substr(0,path.find("."));
			path.append(to_string(i));
			path.append(".bin");
			fileNames.push_back(path);
			cout << "fileName " << i << "=" << path << endl;
		}
	}

//create hash table
	std::atomic<size_t>** hashTable = new std::atomic<size_t>*[2*threadCnt];
	//hashTable[0] = new std::atomic<size_t>[campaingCnt+1];
	for(size_t u = 0; u < 2*threadCnt; u++)
	{
		hashTable[u] = new std::atomic<size_t>[campaingCnt+1];
		for(size_t i = 0; i < campaingCnt+1; i++)
		{
		  std::atomic_init(&hashTable[u][i],std::size_t(0));
		}
	}
	std::atomic<size_t>* finalResultHT = new std::atomic<size_t>[campaingCnt];

	PapiSampler* papiSampler;
	if(papiSampling)
	{
		papiSampler = new PapiSampler(papiSeq, papiFile);
		papiSampler->init(threadCnt);
	}
#ifdef PCMACTIVE
	PCMSampler* pcmSampler;
	if(pcmSampling)
	{
		pcmSampler = new PCMSampler();
		pcmSampler->startSampling();
	}
#endif
	Timestamp begin = getTimestamp();
//	do
//	 {
//	   cout << '\n' << "Press a key to continue...";
//	 } while (cin.get() != '\n');
	size_t checksum = 0;
	#pragma omp parallel num_threads(threadCnt) reduction(+:checksum)
	{
		#pragma omp for
		for(size_t i = 0; i < threadCnt; i++)
		{
			if(!batching && memLoad && !jointInput)//windowing && mem && separat input array
			{
				if(papiSampling)
					papiSampler->startSampling();

                checksum += process_window_mem_local(processCnt, randomCnt, randomNumbers, campaign_lsb, campaign_msb, campaingCnt, hashTable, recs[i], windowSizeInSeconds, 2*i, finalResultHT);
//				checksum += process_window_mem_local_read_only(processCnt, randomCnt, randomNumbers, campaign_lsb, campaign_msb, campaingCnt, hashTable, recs[i], windowSizeInSeconds, 2*i, finalResultHT);

				if(papiSampling)
					papiSampler->stopSampling(i);
			}
//			else if (!batching && !memLoad && !jointInput)// windowing && file && separat input files
//			{
//				process_window_file(processCnt, randomCnt, randomNumbers, campaign_lsb, campaign_msb, campaingCnt, hashTable, fileNames[i].c_str(), windowSizeInSeconds);
//			}
			else
				assert(0);
		}
	}
	cout << "checksum=" << checksum << endl;
	//Merge
//#pragma omp parallel for
//	{
//		for(size_t ht = 0; ht < campaingCnt; ht++)
//		{
//			for(size_t th = 0; th < threadCnt; th++)
//			{
//				finalResultHT[ht] += hashTable[th][];
//			}
//
//		}
	Timestamp end = getTimestamp();
#ifdef PCMACTIVE
	if(pcmSampling)
		pcmSampler->stopSampling();
#endif
	double elapsed_time = double(end - begin) / (1024 * 1024 * 1024);
    cout << fixed;
    cout << "time=" << elapsed_time << " rec/sec=" << threadCnt*processCnt/elapsed_time << endl;

	if(papiSampling)
	{
		papiSampler->printSampling();
	}
#ifdef PCMACTIVE
	if(pcmSampling)
	{
		pcmSampler->printSampling();
	}
#endif
}

