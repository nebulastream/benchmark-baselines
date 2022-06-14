#include "monitors/Measurement.h"

#include <iomanip>

#include "buffers/QueryBuffer.h"
#include "dispatcher/ITaskDispatcher.h"
#include "dispatcher/JoinTaskDispatcher.h"
#include "dispatcher/TaskDispatcher.h"
#include "monitors/LatencyMonitor.h"

long Measurement::m_sumTuples = 0;
int Measurement::m_measurements = 0;

Measurement::Measurement(int id, ITaskDispatcher *dispatcher, LatencyMonitor *monitor) :
    m_id(id), m_dispatcher(dispatcher), m_firstBuffer(dispatcher->getBuffer()), m_monitor(monitor) {
  if (JoinTaskDispatcher *d = dynamic_cast<JoinTaskDispatcher *>(m_dispatcher)) {
    m_secondBuffer = d->getSecondBuffer();
  }
}

void Measurement::stop() {
  m_monitor->stop();
}

std::string Measurement::getInfo(long delta, int inputTuple, int outputTuple) {
  std::string s;
  auto storedBytes = m_firstBuffer->getAverageStoredBytes();
  m_bytesProcessed = (long) m_firstBuffer->getBytesProcessed();
  m_bytesGenerated += (m_secondBuffer == nullptr) ? 0 : + (long) m_secondBuffer->getBytesProcessed();
  m_bytesGenerated = m_dispatcher->getBytesGenerated();
  if (m__bytesProcessed > 0) {
    m_Dt = ((double) delta / 1000.0);
    m_MBpsProcessed = ((double) m_bytesProcessed - (double) m__bytesProcessed) / m__1MB_ / m_Dt;
    m_MBpsGenerated = ((double) m_bytesGenerated - (double) m__bytesGenerated) / m__1MB_ / m_Dt;
    std::string q_id = std::to_string(m_id);
    q_id = std::string(3 - q_id.length(), '0') + q_id;

    // Create an output string stream
    std::ostringstream streamObj;
    streamObj << std::fixed;
    streamObj << std::setprecision(3);
    streamObj << " S" + q_id + " " << m_MBpsProcessed << " MB/s ";
    if (inputTuple != 0) {
      streamObj << "(" << (m_bytesProcessed - m__bytesProcessed) / inputTuple << " t/sec) ";
      m_sumTuples += (m_bytesProcessed - m__bytesProcessed) / inputTuple;
      m_measurements++;
      streamObj << "(Average: " << m_sumTuples / m_measurements << " t/sec) ";
      if (storedBytes > 0) {
        streamObj << "[ASB: " << storedBytes << "] ";
      }
    }
    streamObj << "output " << m_MBpsGenerated << " MB/s "; //["+std::to_string(monitor)+"]";
    if (outputTuple != 0) {
      streamObj << "(" << (m_bytesGenerated - m__bytesGenerated) / outputTuple << " t/sec) ";
    }
    if (m_monitor != nullptr) {
      streamObj << m_monitor->toString();
    }
    s = streamObj.str();
  }
  m__bytesProcessed = m_bytesProcessed;
  m__bytesGenerated = m_bytesGenerated;
  return s;
}

Measurement::~Measurement() {};