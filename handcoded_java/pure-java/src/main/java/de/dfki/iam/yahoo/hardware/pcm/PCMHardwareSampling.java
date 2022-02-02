package de.dfki.iam.yahoo.hardware.pcm;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;

//import pcm.PCMWrapper;

public class PCMHardwareSampling implements AbstractHardwareSampler {

	private long pcmHandle = -1;

	public PCMHardwareSampling(String cfgFile) {

	}

	@Override
	public void startSampling() throws Exception {
//		if (pcmHandle != -1) {
//			return;
//		}
//		try {
//			pcmHandle = PCMWrapper.getPCMHandle();
//		} catch (Throwable ex) {
//			ex.printStackTrace();
//		}
//		PCMWrapper.startSampling(pcmHandle);
	}

	@Override
	public void stopSampling(String prefix) throws Exception {
//		if (pcmHandle == -1) {
//			return;
//		}
//		PCMWrapper.stopSampling(pcmHandle);
//		PCMWrapper.destroyPCMHandle(pcmHandle);
//		pcmHandle = -1;
	}
}
