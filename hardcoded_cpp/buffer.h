/* Producer & Consumer Headerfile */
#ifndef BUFFER_H
#define BUFFER_H
#include <stdint.h>
#include <string.h> // memcpy
#include <cstddef>


using namespace std;

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


//struct record;
//typedef record record;

/* The type for the buffering system.  Defined in buffer.c. */
typedef struct buffer_structure buffer_t;
typedef struct buffer_structure* buffer_ptr;


/* Allocate a new buffering system.  In some driver modes, more than one
   buffer may be used simultaneously! */
buffer_ptr allocate_buffer();

/* Free any memory associated with a buffer_t */
void free_buffer(buffer_ptr);

/* Produces a new event.  Blocks until there is space to write the event.
   The event parameter, 'event', cannot be NULL.

   Note that events may be produced from a separate thread from the one
   in which they are consumed; however, you may assume that they are only
   produced from a single thread. */
void produce_event(buffer_ptr buffer, record* event);

/* Indicates that the last event has been produced. */
void produced_last_event(buffer_ptr buffer);

/* Consumes an event from 'buffer', blocking if no events are available.
   Once all events have been consumed and produced_last_event() has been
   called, returns NULL.  Once a NULL value has been returned, further
   behavior is undefined.

   Note that events may be consumed from a separate thread from the one
   in which they are produced; however, you may assume that they are only
   consumed from a single thread. */
record consume_event(buffer_ptr buffer);

bool mc_buffer_empty(buffer_ptr buffer);
bool consume_event_without_wait(buffer_ptr buffer, record* rec);
bool produce_event_without_wait(buffer_ptr buffer, record* element);
#endif

