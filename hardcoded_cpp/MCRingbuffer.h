#ifndef BUFFER_H
#define BUFFER_H

#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h> // memcpy
#include <cstddef>

struct __attribute__((packed)) record {
    uint8_t user_id[16];
    uint8_t page_id[16];
    uint8_t campaign_id[16];
    char event_type[9];
    char ad_type[9];
    int64_t current_ms;
    uint32_t ip;

    record()
    {
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
// Global Definitions & Variables
#define CACHE_LINE 64 		// cache line is 64 bytes (this can vary from cpu to cpu)
#define BUFFER_SIZE 131072 	// we tried multiples of 2 and took the one which gave us the best results
//#define BATCH_SIZE 1024		// this means that buffer->head/tail are only updated every `BATCH_SIZE` inserts/updates
							// (we tried multiples of 2 and took the one which gave us the best results)
#define NEXT(current) ((current)+1) % (BUFFER_SIZE); // returns the next element in a circular buffer of size BUFFER_SIZE


class MCRingbuffer
{
public:
	MCRingbuffer()
	{
		readVar = 0;
		writeVar = 0;
		localWrite = 0;
		nextRead = 0;
		rBatch = 0;
		localRead = 0;
		nextWrite = 0;
		wBatch = 0;
		batchSize = 1024;
		blockOnEmpty = 0;

	}
	void Insert(record* element);
	int Extract(record* element);
	bool empty();
private:
	record buffer[BUFFER_SIZE];
	/* Variable definitions */
	char cachePad0[CACHE_LINE];

	/*shared control variables*/
	volatile int readVar;
	volatile int writeVar;
	char cachePad1[CACHE_LINE - 2 * sizeof(int)];

	/*consumer’s local control variables*/
	int localWrite;
	int nextRead;
	int rBatch;
	char cachePad2[CACHE_LINE - 3 * sizeof(int)];

	/*producer’s local control variables*/
	int localRead;
	int nextWrite;
	int wBatch;
	char cachePad3[CACHE_LINE - 3 * sizeof(int)];

	/*constants*/
	//int max;
	int blockOnEmpty;
	int batchSize;
	char cachePad4[CACHE_LINE - 3 * sizeof(int)];
	//record element;

};
#endif
