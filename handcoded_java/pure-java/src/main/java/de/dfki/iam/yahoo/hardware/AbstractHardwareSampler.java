package de.dfki.iam.yahoo.hardware;

import java.io.Serializable;

public interface AbstractHardwareSampler extends Serializable {

	void startSampling() throws Exception;
	void stopSampling(String prefix) throws Exception;


}
