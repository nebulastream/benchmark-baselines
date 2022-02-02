#pragma once
#include <iostream>
#include "cpucounters.h"
typedef uint64_t Timestamp;
#include <chrono>
using NanoSeconds = std::chrono::nanoseconds;
using Clock = std::chrono::high_resolution_clock;
class PCMSampler
{
public:
	PCMSampler();
	~PCMSampler();

	void startSampling();

	void stopSampling();
	void printSampling();

	Timestamp getTimestamp()
	{
	  return std::chrono::duration_cast<NanoSeconds>(
				 Clock::now().time_since_epoch())
		  .count();
	}
	void print_pcm_measurements(std::stringstream& out);
private:
	PCM* pcm;
	std::vector<CoreCounterState> cstates1, cstates2;
	std::vector<SocketCounterState> sktstate1, sktstate2;
	SystemCounterState sstate1, sstate2;
	int cpu_model;
	Timestamp begin, end;
};
