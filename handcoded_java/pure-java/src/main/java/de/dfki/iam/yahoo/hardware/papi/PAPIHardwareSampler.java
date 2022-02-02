package de.dfki.iam.yahoo.hardware.papi;

import de.dfki.iam.yahoo.hardware.AbstractHardwareSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import papi.EventSet;
//import papi.Papi;
//import papi.Constants;
//import papi.PapiException;

import java.io.Serializable;
import java.util.function.Supplier;

public class PAPIHardwareSampler implements AbstractHardwareSampler {


    private static final Logger LOG = LoggerFactory.getLogger(PAPIHardwareSampler.class);

//    private transient EventSet eventSet;

    private int[] countersId;

    private String[] countersDescription;

    private String hwPerfCountersCfg;


    public PAPIHardwareSampler(String hwPerfCounters) {
        this.hwPerfCountersCfg = hwPerfCounters;
    }


    @Override
    public void startSampling() throws Exception {

//        Papi.initThread();
//
//        String[] tokens = hwPerfCountersCfg.split(",");
//
//        Constants placeholder = new Constants();
//
//        countersId = new int[tokens.length];
//        countersDescription = new String[tokens.length];
//
//        for (int i = 0; i < tokens.length; i++) {
//
//            String token = tokens[i].trim();
//            String tokenName = token + "_name";
//
//            try {
//                countersId[i] =
//                        Constants.class.getDeclaredField(token).getInt(placeholder);
//
//                countersDescription[i] = (String)
//                        Constants.class.getDeclaredField(tokenName).get(placeholder);
//
//                LOG.info("GREPTHIS :: Event {} with id 0x{} added", countersDescription[i], Integer.toHexString(countersId[i]));
//
//            } catch (NoSuchFieldException ex) {
//                LOG.info("GREPTHIS :: {} is not available and will be ignored", token);
//            }
//
//        }
//
//        eventSet = EventSet.create(countersId);
//
//        eventSet.start();


    }

    @Override
    public void stopSampling(String prefix) throws Exception {

//        if (eventSet == null) {
//            return;
//        }
//
//        eventSet.stop();
//
//        long[] counters = eventSet.getCounters();
//
//        for (int i = 0; i < counters.length; i++) {
//            LOG.info("GREPTHIS :: {} :: counter [{}]=[{}]",
//                    prefix, countersDescription[i], counters[i]);
//        }
//
//        eventSet.destroy();
    }


}
