package de.tub.nebulastream.benchmarks.flink.nextmark;

public class NexmarkCommon {

	public static final String PERSONS_TOPIC = "nexmark_persons";
	public static final String AUCTIONS_TOPIC = "nexmark_auctions";
	public static final String BIDS_TOPIC = "nexmark_bids";

	public static final long PERSON_EVENT_RATIO = 1;
	public static final long AUCTION_EVENT_RATIO = 4;
	private static final long BID_EVENT_RATIO = 4;
	public static final long TOTAL_EVENT_RATIO = PERSON_EVENT_RATIO + AUCTION_EVENT_RATIO + BID_EVENT_RATIO;

	public static final int MAX_PARALLELISM = 50;

	public static final long START_ID_AUCTION[] = new long[MAX_PARALLELISM];
	public static final long START_ID_PERSON[] = new long[MAX_PARALLELISM];

	public static final long MAX_PERSON_ID = 540_000_000L;
	public static final long MAX_AUCTION_ID = 540_000_000_000L;
	public static final long MAX_BID_ID = 540_000_000_000L;

	public static final int HOT_SELLER_RATIO = 100;
	public static final int HOT_AUCTIONS_PROB = 85;


	public static final int HOT_AUCTION_RATIO = 100;

	static {

		START_ID_AUCTION[0] = START_ID_PERSON[0] = 0;

		long person_stride = MAX_PERSON_ID / MAX_PARALLELISM;
		long auction_stride = MAX_AUCTION_ID / MAX_PARALLELISM;
		for (int i = 1; i < MAX_PARALLELISM; i++) {
			START_ID_PERSON[i] = START_ID_PERSON[i - 1] + person_stride;
			START_ID_AUCTION[i] = START_ID_AUCTION[i - 1] + auction_stride;
		}

	}


	public static void getPersonStride(int subTaskIndex, int parallelism, long[] out) {
		long stride = MAX_PERSON_ID / parallelism;
		out[0] =  stride * subTaskIndex;
		out[1] = out[0] + stride;
	}

	public static void geAuctiontride(int subTaskIndex, int parallelism, long[] out) {
		long stride = MAX_AUCTION_ID / parallelism;
		out[0] =  stride * subTaskIndex;
		out[1] = out[0] + stride;
	}

	public static void getBidStride(int subTaskIndex, int parallelism, long[] out) {
		long stride = MAX_BID_ID / parallelism;
		out[0] =  stride * subTaskIndex;
		out[1] = out[0] + stride;
	}

}
