package de.dfki.iam.yahoo.record;

import java.io.Serializable;
import java.util.UUID;

public class IntermediateTuple implements Serializable {


	public final static IntermediateTuple POISONED_TUPLE = new IntermediateTuple(null, -1);

	public UUID campaignId;

	public long timestamp;

	public IntermediateTuple(UUID campaignId, long timestamp) {
		this.campaignId = campaignId;
		this.timestamp = timestamp;
	}

	public static int size() {
		return 3 * Long.BYTES;
	}

	@Override
	public int hashCode() {
		return campaignId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof IntermediateTuple) {
			IntermediateTuple that = (IntermediateTuple) obj;
			if (campaignId == null) {
				return this == that && that.timestamp == timestamp;
			}
			return campaignId.equals(that.campaignId) && that.timestamp == timestamp;
		}
		return false;
	}
}
