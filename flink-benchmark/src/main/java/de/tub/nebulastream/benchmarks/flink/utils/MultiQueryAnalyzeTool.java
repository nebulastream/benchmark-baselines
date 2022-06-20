package de.tub.nebulastream.benchmarks.flink.utils;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.summary.Sum;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultiQueryAnalyzeTool {

    public static class Result {

        SummaryStatistics throughputs;
        Map<String, Map<String, SummaryStatistics>> perHostThr;

        public Result(SummaryStatistics throughputs,  Map<String, Map<String, SummaryStatistics>> perHostThr) {
            this.throughputs = throughputs;
            this.perHostThr = perHostThr;
        }
    }

    public static Result analyze(String file) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(file));

        String l;
        Pattern throughputPattern = Pattern.compile(".*That's ([0-9.]+) elements\\/second\\/core.*");
        Pattern hostPattern = Pattern.compile(".*Worker: ([0-9.]+).*");
        Pattern queryPattern = Pattern.compile(".*Query ([0-9.]+).*");

        SummaryStatistics throughputs = new SummaryStatistics();
        String currentHost = null;
        String currentQuery = null;
        Map<String, DescriptiveStatistics> perHostLat = new HashMap<String, DescriptiveStatistics>();
        Map<String, Map<String, SummaryStatistics>> perHostThr = new HashMap<String, Map<String, SummaryStatistics>>();

        while (sc.hasNextLine()) {
            l = sc.nextLine();
            // ---------- host ---------------
            Matcher hostMatcher = hostPattern.matcher(l);
            if (hostMatcher.matches()) {
                currentHost = hostMatcher.group(1);
            }
            Matcher queryMatcher = queryPattern.matcher(l);
            if (queryMatcher.matches()) {
                currentQuery = queryMatcher.group(1);
            }
            // ---------- throughput ---------------
            Matcher tpMatcher = throughputPattern.matcher(l);
            if (tpMatcher.matches()) {
                double eps = Double.valueOf(tpMatcher.group(1));
                throughputs.addValue(eps);
                //	System.out.println("epts = "+eps);

                Map<String, SummaryStatistics> perQuery = perHostThr.get(currentQuery);
                if (perQuery == null) {
                    perQuery = new HashMap<String, SummaryStatistics>();
                    perHostThr.put(currentHost, perQuery);
                }
                SummaryStatistics perHost = perQuery.get(currentHost);
                if (perHost == null) {
                    perHost = new SummaryStatistics();
                    perQuery.put(currentHost, perHost);
                }
                perHost.addValue(eps);
            }
        }

        return new Result(throughputs, perHostThr);
    }

    public static void main(String[] args) throws IOException {
        String inputFile = args[0];
        String benchmarkName = args[1];
        String workerThreads = args[2];
        Result r1 = analyze(inputFile);
        SummaryStatistics throughputs = r1.throughputs;

        System.err.println("================= Throughput (" + r1.perHostThr.size() + " reports ) =====================");
        double sumThroughput = 0;
        for (Map.Entry<String, Map<String, SummaryStatistics>> queryEntry : r1.perHostThr.entrySet()) {
            for (Map.Entry<String, SummaryStatistics> entry : queryEntry.getValue().entrySet()) {
                System.err.println("====== " + entry.getKey() + " (entries: " + entry.getValue().getN() + ")=======");
                System.err.println("Mean throughput " + entry.getValue().getMean());
                sumThroughput = sumThroughput + entry.getValue().getMean();
            }
        }
        System.err.println("Sum throughput " + sumThroughput);

        FileOutputStream fos = new FileOutputStream("./" + benchmarkName + ".csv", true);
        fos.write(benchmarkName.getBytes());
        fos.write(",".getBytes());
        fos.write(workerThreads.getBytes());
        fos.write(",".getBytes());
        fos.write(Long.toString(Math.round(sumThroughput)).getBytes());
        fos.write("\n".getBytes());
        fos.close();


    }
}