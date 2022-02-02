#include "PCMSampler.hpp"
#include <vector>
PCMSampler::PCMSampler()
	{
	cpu_model = 0;
		begin = 0;
		end = 0;
		pcm = PCM::getInstance();
		//pcm->cleanup();
		//pcm->cleanupPMU();
		pcm->resetPMU();
		std::cout << "before program" << std::endl;
		PCM::ErrorCode status = pcm->program();
		bool succeded = false;
		do {
			switch (status)
		    {
		    case PCM::Success:
		    	succeded = true;
		        break;
		    case PCM::MSRAccessDenied:
		        std::cerr << "Access to Processor Counter Monitor has denied (no MSR or PCI CFG space access)." << std::endl;
		 		break;
		    case PCM::PMUBusy:
		        std::cerr << "Access to Processor Counter Monitor has denied (Performance Monitoring Unit is occupied by other application). Try to stop the application that uses PMU." << std::endl;
		        std::cerr << "Alternatively you can try running PCM with option -r to reset PMU configuration at your own risk." << std::endl;
				pcm->resetPMU();
				pcm->cleanup();
				pcm = PCM::getInstance();
				status = pcm->program();
		    	break;
		    default:
		        std::cerr << "Access to Processor Counter Monitor has denied (Unknown error)." << std::endl;
		        break;
		    }
		} while (!succeded);

		std::cout << "after program" << std::endl;


	}
PCMSampler::~PCMSampler()
	{
		pcm->cleanup();
//		m->resetPMU();
	}


	void PCMSampler::startSampling()
	{
		pcm = PCM::getInstance();
		cpu_model = pcm->getCPUModel();
		pcm->getAllCounterStates(sstate1, sktstate1, cstates1);
		begin = getTimestamp();
		//sstate1 = getSystemCounterState();
	}

	void PCMSampler::stopSampling()
	{
		pcm->getAllCounterStates(sstate2, sktstate2, cstates2);
		end = getTimestamp();

	}

	std::string temp_format(int32 t) {
	  char buffer[1024];
	  if (t == PCM_INVALID_THERMAL_HEADROOM) return "N/A";

	  sprintf(buffer, "%2d", t);
	  return buffer;
	}
void PCMSampler::print_pcm_measurements(std::stringstream& out) {

	bool show_core_output = true;
	bool show_system_output = true;
	bool show_socket_output = true;


	return;
  out << "n";
  out << " EXEC  : instructions per nominal CPU cycle"
	  << "n";
  out << " IPC   : instructions per CPU cycle"
	  << "n";
  out << " FREQ  : relation to nominal CPU frequency='unhalted clock "
		 "ticks'/'invariant timer ticks' (includes Intel Turbo Boost)"
	  << "n";
  if (cpu_model != PCM::ATOM)
	out << " AFREQ : relation to nominal CPU frequency while in active state "
		   "(not in power-saving C state)='unhalted clock ticks'/'invariant "
		   "timer ticks while in C0-state'  (includes Intel Turbo Boost)"
		<< "n";
  if (cpu_model != PCM::ATOM)
	out << " L3MISS: L3 cache misses "
		<< "n";
  if (cpu_model == PCM::ATOM)
	out << " L2MISS: L2 cache misses "
		<< "n";
  else
	out << " L2MISS: L2 cache misses (including other core's L2 cache *hits*) "
		<< "n";
  if (cpu_model != PCM::ATOM)
	out << " L3HIT : L3 cache hit ratio (0.00-1.00)"
		<< "n";
  out << " L2HIT : L2 cache hit ratio (0.00-1.00)"
	  << "n";
  if (cpu_model != PCM::ATOM)
	out << " L3CLK : ratio of CPU cycles lost due to L3 cache misses "
		   "(0.00-1.00), in some cases could be >1.0 due to a higher memory "
		   "latency"
		<< "n";
  if (cpu_model != PCM::ATOM)
	out << " L2CLK : ratio of CPU cycles lost due to missing L2 cache but "
		   "still hitting L3 cache (0.00-1.00)"
		<< "n";
  if (cpu_model != PCM::ATOM)
	out << " READ  : bytes read from memory controller (in GBytes)"
		<< "n";
  if (cpu_model != PCM::ATOM)
	out << " WRITE : bytes written to memory controller (in GBytes)"
		<< "n";
  if (pcm->memoryIOTrafficMetricAvailable())
	out << " IO    : bytes read/written due to IO requests to memory "
		   "controller (in GBytes); this may be an over estimate due to "
		   "same-cache-line partial requests"
		<< "n";
  out << " TEMP  : Temperature reading in 1 degree Celsius relative to the "
		 "TjMax temperature (thermal headroom): 0 corresponds to the max "
		 "temperature"
	  << "n";
  out << "n";
  out << "n";
  out.precision(2);
  out << std::fixed;
  if (cpu_model == PCM::ATOM)
	out << " Core (SKT) | EXEC | IPC  | FREQ | L2MISS | L2HIT | TEMP"
		<< "n"
		<< "n";
  else {
	if (pcm->memoryIOTrafficMetricAvailable())
	  out << " Core (SKT) | EXEC | IPC  | FREQ  | AFREQ | L3MISS | L2MISS | "
			 "L3HIT | L2HIT | L3CLK | L2CLK  | READ  | WRITE |  IO   | TEMP"
		  << "n"
		  << "n";
	else
	  out << " Core (SKT) | EXEC | IPC  | FREQ  | AFREQ | L3MISS | L2MISS | "
			 "L3HIT | L2HIT | L3CLK | L2CLK  | READ  | WRITE | TEMP"
		  << "n"
		  << "n";
  }

  if (show_core_output) {
	for (uint32 i = 0; i < pcm->getNumCores(); ++i) {
	  if (pcm->isCoreOnline(i) == false) continue;

	  if (cpu_model != PCM::ATOM) {
		out << " " << std::setw(3) << i << "   " << std::setw(2)
			<< pcm->getSocketId(i) << "     "
			<< getExecUsage(cstates1[i], cstates2[i]) << "   "
			<< getIPC(cstates1[i], cstates2[i]) << "   "
			<< getRelativeFrequency(cstates1[i], cstates2[i]) << "    "
			<< getActiveRelativeFrequency(cstates1[i], cstates2[i]) << "    "
			<< unit_format(getL3CacheMisses(cstates1[i], cstates2[i])) << "   "
			<< unit_format(getL2CacheMisses(cstates1[i], cstates2[i])) << "    "
			<< getL3CacheHitRatio(cstates1[i], cstates2[i]) << "    "
			<< getL2CacheHitRatio(cstates1[i], cstates2[i]) << "    "
			<< getCyclesLostDueL3CacheMisses(cstates1[i], cstates2[i]) << "    "
			<< getCyclesLostDueL2CacheMisses(cstates1[i], cstates2[i]);
		if (pcm->memoryIOTrafficMetricAvailable())
		  out << "     N/A     N/A     N/A";
		else
		  out << "     N/A     N/A";
		out << "     " << temp_format(cstates2[i].getThermalHeadroom()) << "n";
	  } else
		out << " " << std::setw(3) << i << "   " << std::setw(2)
			<< pcm->getSocketId(i) << "     "
			<< getExecUsage(cstates1[i], cstates2[i]) << "   "
			<< getIPC(cstates1[i], cstates2[i]) << "   "
			<< getRelativeFrequency(cstates1[i], cstates2[i]) << "   "
			<< unit_format(getL2CacheMisses(cstates1[i], cstates2[i])) << "    "
			<< getL2CacheHitRatio(cstates1[i], cstates2[i]) << "     "
			<< temp_format(cstates2[i].getThermalHeadroom()) << "n";
	}
  }
  if (show_socket_output) {
	if (!(pcm->getNumSockets() == 1 && cpu_model == PCM::ATOM)) {
	  out << "-----------------------------------------------------------------"
			 "------------------------------------------------------------"
		  << "n";
	  for (uint32 i = 0; i < pcm->getNumSockets(); ++i) {
		out << " SKT   " << std::setw(2) << i << "     "
			<< getExecUsage(sktstate1[i], sktstate2[i]) << "   "
			<< getIPC(sktstate1[i], sktstate2[i]) << "   "
			<< getRelativeFrequency(sktstate1[i], sktstate2[i]) << "    "
			<< getActiveRelativeFrequency(sktstate1[i], sktstate2[i]) << "    "
			<< unit_format(getL3CacheMisses(sktstate1[i], sktstate2[i]))
			<< "   "
			<< unit_format(getL2CacheMisses(sktstate1[i], sktstate2[i]))
			<< "    " << getL3CacheHitRatio(sktstate1[i], sktstate2[i])
			<< "    " << getL2CacheHitRatio(sktstate1[i], sktstate2[i])
			<< "    "
			<< getCyclesLostDueL3CacheMisses(sktstate1[i], sktstate2[i])
			<< "    "
			<< getCyclesLostDueL2CacheMisses(sktstate1[i], sktstate2[i]);
		if (pcm->memoryTrafficMetricsAvailable())
		  out << "    "
			  << getBytesReadFromMC(sktstate1[i], sktstate2[i]) /
					 double(1024ULL * 1024ULL * 1024ULL)
			  << "    "
			  << getBytesWrittenToMC(sktstate1[i], sktstate2[i]) /
					 double(1024ULL * 1024ULL * 1024ULL);
		else
		  out << "     N/A     N/A";

		if (pcm->memoryIOTrafficMetricAvailable())
		  out << "    "
			  << getIORequestBytesFromMC(sktstate1[i], sktstate2[i]) /
					 double(1024ULL * 1024ULL * 1024ULL);

		out << "     " << temp_format(sktstate2[i].getThermalHeadroom())
			<< "n";
	  }
	}
  }
  out << "---------------------------------------------------------------------"
		 "--------------------------------------------------------"
	  << "n";

  if (show_system_output) {
	if (cpu_model != PCM::ATOM) {
	  out << " TOTAL  *     " << getExecUsage(sstate1, sstate2) << "   "
		  << getIPC(sstate1, sstate2) << "   "
		  << getRelativeFrequency(sstate1, sstate2) << "    "
		  << getActiveRelativeFrequency(sstate1, sstate2) << "    "
		  << unit_format(getL3CacheMisses(sstate1, sstate2)) << "   "
		  << unit_format(getL2CacheMisses(sstate1, sstate2)) << "    "
		  << getL3CacheHitRatio(sstate1, sstate2) << "    "
		  << getL2CacheHitRatio(sstate1, sstate2) << "    "
		  << getCyclesLostDueL3CacheMisses(sstate1, sstate2) << "    "
		  << getCyclesLostDueL2CacheMisses(sstate1, sstate2);
	  if (pcm->memoryTrafficMetricsAvailable())
		out << "    "
			<< getBytesReadFromMC(sstate1, sstate2) /
				   double(1024ULL * 1024ULL * 1024ULL)
			<< "    "
			<< getBytesWrittenToMC(sstate1, sstate2) /
				   double(1024ULL * 1024ULL * 1024ULL);
	  else
		out << "     N/A     N/A";

	  if (pcm->memoryIOTrafficMetricAvailable())
		out << "    "
			<< getIORequestBytesFromMC(sstate1, sstate2) /
				   double(1024ULL * 1024ULL * 1024ULL);

	  out << "     N/An";
	} else
	  out << " TOTAL  *     " << getExecUsage(sstate1, sstate2) << "   "
		  << getIPC(sstate1, sstate2) << "   "
		  << getRelativeFrequency(sstate1, sstate2) << "   "
		  << unit_format(getL2CacheMisses(sstate1, sstate2)) << "    "
		  << getL2CacheHitRatio(sstate1, sstate2) << "     N/An";
  }

  if (show_system_output) {
	out << "n"
		<< " Instructions retired: "
		<< unit_format(getInstructionsRetired(sstate1, sstate2))
		<< " ; Active cycles: " << unit_format(getCycles(sstate1, sstate2))
		<< " ; Time (TSC): "
		<< unit_format(getInvariantTSC(cstates1[0], cstates2[0]))
		<< "ticks ; C0 (active,non-halted) core residency: "
		<< (getCoreCStateResidency(0, sstate1, sstate2) * 100.) << " %n";
	out << "n";
	for (int s = 1; s <= PCM::MAX_C_STATE; ++s)
	  if (pcm->isCoreCStateResidencySupported(s))
		out << " C" << s << " core residency: "
			<< (getCoreCStateResidency(s, sstate1, sstate2) * 100.) << " %;";
	out << "n";
	for (int s = 0; s <= PCM::MAX_C_STATE; ++s)
	  if (pcm->isPackageCStateResidencySupported(s))
		out << " C" << s << " package residency: "
			<< (getPackageCStateResidency(s, sstate1, sstate2) * 100.) << " %;";
	out << "n";
	if (pcm->getNumCores() == pcm->getNumOnlineCores()) {
	  out << "n"
		  << " PHYSICAL CORE IPC                 : "
		  << getCoreIPC(sstate1, sstate2) << " => corresponds to "
		  << 100. * (getCoreIPC(sstate1, sstate2) / double(pcm->getMaxIPC()))
		  << " % utilization for cores in active state";
	  out << "n"
		  << " Instructions per nominal CPU cycle: "
		  << getTotalExecUsage(sstate1, sstate2) << " => corresponds to "
		  << 100. *
				 (getTotalExecUsage(sstate1, sstate2) / double(pcm->getMaxIPC()))
		  << " % core utilization over time interval"
		  << "n";
	}
  }

  if (show_socket_output) {
	if (pcm->getNumSockets() > 1)  // QPI info only for multi socket systems
	{
	  out << "n"
		  << "Intel(r) QPI data traffic estimation in bytes (data traffic "
			 "coming to CPU/socket through QPI links):"
		  << "n"
		  << "n";

	  const uint32 qpiLinks = (uint32)pcm->getQPILinksPerSocket();

	  out << "              ";
	  for (uint32 i = 0; i < qpiLinks; ++i) out << " QPI" << i << "    ";

	  if (pcm->qpiUtilizationMetricsAvailable()) {
		out << "| ";
		for (uint32 i = 0; i < qpiLinks; ++i) out << " QPI" << i << "  ";
	  }

	  out << "n"
		  << "-----------------------------------------------------------------"
			 "-----------------------------"
		  << "n";

	  for (uint32 i = 0; i < pcm->getNumSockets(); ++i) {
		out << " SKT   " << std::setw(2) << i << "     ";
		for (uint32 l = 0; l < qpiLinks; ++l)
		  out << unit_format(getIncomingQPILinkBytes(i, l, sstate1, sstate2))
			  << "   ";

		if (pcm->qpiUtilizationMetricsAvailable()) {
		  out << "|  ";
		  for (uint32 l = 0; l < qpiLinks; ++l)
			out << std::setw(3) << std::dec
				<< int(100. *
					   getIncomingQPILinkUtilization(i, l, sstate1, sstate2))
				<< "%   ";
		}

		out << "n";
	  }
	}
  }

  if (show_system_output) {
	out << "-------------------------------------------------------------------"
		   "---------------------------"
		<< "n";

	if (pcm->getNumSockets() > 1)  // QPI info only for multi socket systems
	  out << "Total QPI incoming data traffic: "
		  << unit_format(getAllIncomingQPILinkBytes(sstate1, sstate2))
		  << "     QPI data traffic/Memory controller traffic: "
		  << getQPItoMCTrafficRatio(sstate1, sstate2) << "n";
  }

  if (show_socket_output) {
	if (pcm->getNumSockets() > 1 &&
		(pcm->outgoingQPITrafficMetricsAvailable()))  // QPI info only for multi
													// socket systems
	{
	  out << "n"
		  << "Intel(r) QPI traffic estimation in bytes (data and non-data "
			 "traffic outgoing from CPU/socket through QPI links):"
		  << "n"
		  << "n";

	  const uint32 qpiLinks = (uint32)pcm->getQPILinksPerSocket();

	  out << "              ";
	  for (uint32 i = 0; i < qpiLinks; ++i) out << " QPI" << i << "    ";

	  out << "| ";
	  for (uint32 i = 0; i < qpiLinks; ++i) out << " QPI" << i << "  ";

	  out << "n"
		  << "-----------------------------------------------------------------"
			 "-----------------------------"
		  << "n";

	  for (uint32 i = 0; i < pcm->getNumSockets(); ++i) {
		out << " SKT   " << std::setw(2) << i << "     ";
		for (uint32 l = 0; l < qpiLinks; ++l)
		  out << unit_format(getOutgoingQPILinkBytes(i, l, sstate1, sstate2))
			  << "   ";

		out << "|  ";
		for (uint32 l = 0; l < qpiLinks; ++l)
		  out << std::setw(3) << std::dec
			  << int(100. *
					 getOutgoingQPILinkUtilization(i, l, sstate1, sstate2))
			  << "%   ";

		out << "n";
	  }

	  out << "-----------------------------------------------------------------"
			 "-----------------------------"
		  << "n";
	  out << "Total QPI outgoing data and non-data traffic: "
		  << unit_format(getAllOutgoingQPILinkBytes(sstate1, sstate2)) << "n";
	}
  }
  if (show_socket_output) {
	if (pcm->packageEnergyMetricsAvailable()) {
	  out << "n";
	  out << "-----------------------------------------------------------------"
			 "-----------------------------"
		  << "n";
	  for (uint32 i = 0; i < pcm->getNumSockets(); ++i) {
		out << " SKT   " << std::setw(2) << i << " package consumed "
			<< getConsumedJoules(sktstate1[i], sktstate2[i]) << " Joulesn";
	  }
	  out << "-----------------------------------------------------------------"
			 "-----------------------------"
		  << "n";
	  out << " TOTAL:                    "
		  << getConsumedJoules(sstate1, sstate2) << " Joulesn";
	}
	if (pcm->dramEnergyMetricsAvailable()) {
	  out << "n";
	  out << "-----------------------------------------------------------------"
			 "-----------------------------"
		  << "n";
	  for (uint32 i = 0; i < pcm->getNumSockets(); ++i) {
		out << " SKT   " << std::setw(2) << i << " DIMMs consumed "
			<< getDRAMConsumedJoules(sktstate1[i], sktstate2[i]) << " Joulesn";
	  }
	  out << "-----------------------------------------------------------------"
			 "-----------------------------"
		  << "n";
	  out << " TOTAL:                  "
		  << getDRAMConsumedJoules(sstate1, sstate2) << " Joulesn";
	}
  }
}
	void PCMSampler::printSampling()
	{
		std::stringstream stream;
//		print_pcm_measurements(pcm, cstates1, cstates2, sktstate1, sktstate2,
//							 sstate1, sstate2, cpu_model, show_core_output,
//							 show_socket_output, show_system_output, stream);

		std::stringstream outstream;
		outstream << stream.str();
		outstream << " time elapsed in sec=" << (double(end - begin) / (1000 * 1000 * 1000)) << " ";
		outstream << "bytres read from Mem=" << getBytesReadFromMC(sstate1, sstate2)
				<< " bytes write to Mem=" << getBytesWrittenToMC(sstate1, sstate2) << std::endl;

		if (pcm->memoryTrafficMetricsAvailable()) {
			double giga_bytes_read_from_memory_controller =
				getBytesReadFromMC(sstate1, sstate2) /
				double(1024ULL * 1024ULL * 1024ULL);
			double giga_bytes_written_to_memory_controller =
				getBytesWrittenToMC(sstate1, sstate2) /
				double(1024ULL * 1024ULL * 1024ULL);
			outstream << "[PROFILER]: GigaBytes Read from Memory Controller: "
					  << giga_bytes_read_from_memory_controller << std::endl;
			outstream << "[PROFILER]: GigaBytes Written to Memory Controller: "
					  << giga_bytes_written_to_memory_controller << std::endl;
			outstream << "[PROFILER]: Bandwidth: "
					  << (giga_bytes_read_from_memory_controller +
						  giga_bytes_written_to_memory_controller) /
							 (double(end - begin) / (1000 * 1000 * 1000))
					  << "GB/s" << std::endl;
		} else {
			outstream << "[PROFILER]: Memory Traffic Metrics not available!"
					<< std::endl;
			//	   sstate2 = getSystemCounterState();
		}
//		std::cout << outstream.str() << std::endl;
		//print_pcm_measurements(outstream);

//		//core out
//		for (uint32 i = 0; i < pcm->getNumCores(); ++i)
//		{
//		  outstream << "IPC CORE " << i << "=" << getIPC(cstates1[i], cstates2[i]) << std::endl;
//
//		}
		//socket
		for (uint32 i = 0; i < pcm->getNumSockets(); ++i) {
//		 (( out << " SKT   " << std::setw(2) << i << "  "
		  outstream << "IPC Socket " << i << "=" << getIPC(sktstate1[i], sktstate2[i]) << "   " << std::endl;
		  //outstream << "Core Socket " << i << "=" << getCoreIPC(cstates1[], cstates1[0]) << "   " << std::endl;
		  outstream << "Cyc lost due to L3 misses:" << getCyclesLostDueL3CacheMisses(sktstate1[i], sktstate2[i]) << std::endl;
		  outstream<< "Cycy lost due to L3 miss but L3 hit:" << getCyclesLostDueL2CacheMisses(sktstate1[i], sktstate2[i]) << std::endl;
		  outstream << "L2 Hit Ratio:" << getL2CacheHitRatio(sktstate1[i], sktstate2[i]) << std::endl;
		  outstream<< "L3 Hit Ratio:" << getL3CacheHitRatio(sktstate1[i], sktstate2[i]) << std::endl;
		  outstream<< "L3 CM:" << getL3CacheMisses(sktstate1[i], sktstate2[i]) << std::endl;
		  outstream<< "L2 CM:" << getL2CacheMisses(sktstate1[i], sktstate2[i]) << std::endl;
		  outstream << "L2 CH:" << getL2CacheHits(sktstate1[i], sktstate2[i]) << std::endl;
		  outstream << "L3 CH:" << getL3CacheHits(sktstate1[i], sktstate2[i]) << std::endl;

		  std::cout  << "CopyString" << getIPC(sktstate1[i], sktstate2[i]) << "," << getCyclesLostDueL3CacheMisses(sktstate1[i], sktstate2[i]) << ","
				  << getCyclesLostDueL2CacheMisses(sktstate1[i], sktstate2[i]) << "," << getL2CacheHitRatio(sktstate1[i], sktstate2[i]) << ","
				  << getL3CacheHitRatio(sktstate1[i], sktstate2[i])<<  "," << getL3CacheMisses(sktstate1[i], sktstate2[i]) << ","
				 << getL2CacheMisses(sktstate1[i], sktstate2[i]) << "," << getL2CacheHits(sktstate1[i], sktstate2[i]) << ","<<
				 getL3CacheHits(sktstate1[i], sktstate2[i]) << "," << std::endl;
		}

		outstream << "system ipc=" << getIPC(sstate1, sstate2);
		outstream << "CoreIPC:" << getCoreIPC(sstate1,sstate2) << std::endl;
		outstream << "Instructions retired:" << getInstructionsRetired(sstate1,sstate2) << std::endl;
		outstream << "executed core clock cycles:" << getCycles(sstate1,sstate2) << std::endl;
		outstream << "reference clock cycles:" << getRefCycles(sstate1,sstate2) << std::endl;

		std::cout << "Core Instructions per clock:" << getIPC(sstate1,sstate2) << std::endl;

		std::cout << outstream.str();


		return;

		std::cout << "Instructions per clock:" << getIPC(sstate1,sstate2) << std::endl;
		std::cout << "Instructions retired:" << getInstructionsRetired(sstate1,sstate2) << std::endl;
		std::cout << "average number of retired instructions:" << getExecUsage(sstate1,sstate2) << std::endl;
		std::cout << "number of retired instructions:" << getInstructionsRetired(sstate1,sstate2) << std::endl;
		std::cout << "executed core clock cycles:" << getCycles(sstate1,sstate2) << std::endl;
		std::cout << "reference clock cycles:" << getRefCycles(sstate1,sstate2) << std::endl;
		std::cout << "CoreIPC:" << getCoreIPC(sstate1,sstate2) << std::endl;
		std::cout << "average number of retired instructions:" << getTotalExecUsage(sstate1,sstate2) << std::endl;
		std::cout << "average core frequency:" << getAverageFrequency(sstate1,sstate2) << std::endl;

		std::cout << "Cyc lost due to L3 misses:" << getCyclesLostDueL3CacheMisses(sstate1,sstate2) << std::endl;
		std::cout << "Cycy lost due to L3 miss but L3 hit:" << getCyclesLostDueL2CacheMisses(sstate1,sstate2) << std::endl;
		std::cout << "L2 Hit Ratio:" << getL2CacheHitRatio(sstate1,sstate2) << std::endl;
		std::cout << "L3 Hit Ratio:" << getL3CacheHitRatio(sstate1,sstate2) << std::endl;
		std::cout << "L3 CM:" << getL3CacheMisses(sstate1,sstate2) << std::endl;
		std::cout << "L2 CM:" << getL2CacheMisses(sstate1,sstate2) << std::endl;
		std::cout << "L2 CH:" << getL2CacheHits(sstate1,sstate2) << std::endl;
		std::cout << "L3 CH:" << getL3CacheHits(sstate1,sstate2) << std::endl;

		std::cout << "Local Memory Bandwidth" << getLocalMemoryBW(sstate1,sstate2) << std::endl;
		std::cout << "Remote Memory Bandwidth:" << getRemoteMemoryBW(sstate1,sstate2) << std::endl;


//		std::cout << ":" << getL3CacheHitsNoSnoop(sstate1,sstate2) << std::endl;
//		std::cout << ":" << getL3CacheHitsSnoop(sstate1,sstate2) << std::endl;
//		std::cout << ":" << getInvariantTSC(sstate1,sstate2) << std::endl;

		std::cout << "bytes read from DRAM:" << getBytesReadFromMC(sstate1,sstate2) << std::endl;
		std::cout << "bytes written to DRAM :" << getBytesWrittenToMC(sstate1,sstate2) << std::endl;
		std::cout << "read/write requests from all IO :" << getIORequestBytesFromMC(sstate1,sstate2) << std::endl;
//		std::cout << ":" << getSMICount(sstate1,sstate2) << std::endl;
//		std::cout << ":" << getAllIncomingQPILinkBytes(sstate1,sstate2) << std::endl;
	}
