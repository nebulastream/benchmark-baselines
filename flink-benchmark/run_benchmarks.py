import os
import subprocess
import argparse

parser = argparse.ArgumentParser(description="Script for running Flink benchmarks")
parser.add_argument("--flink_version", default="1.20.1", help="Flink version to download (default: 1.20.1)")
args = parser.parse_args()

flink = "flink-1.20.1"
jar_path = os.path.join("target", "yahoo-bench-flink_2.11-0.1-SNAPSHOT.jar")

queries = {
    #"clustermonitoring1": "clustermonitoring.CM1",
    #"clustermonitoring2": "clustermonitoring.CM2",
    #"linearroadbenchmark1": "linearroad.LR1",
    #"linearroadbenchmark2": "linearroad.LR2",
    #"manufacturingequipment1": "manufacturingequipment.ME1",
    #"smartgrid1": "smartgrid.SG1",
    #"smartgrid2": "smartgrid.SG2",
    #"smartgrid3": "smartgrid.SG3",
    #"ysb": "ysb.YSB",
    #"multiquery_ysb": "multiquery.ysb.YSB",
    "nexmark1": "nextmark.NE1",
    "nexmark2": "nextmark.NE2",
    "nexmark8": "nextmark.NE8"
}


def download_flink():
    flink_url = f"https://dlcdn.apache.org/flink/{flink}/{flink}-bin-scala_2.12.tgz"
    subprocess.run(["wget", flink_url, "-O", "flink.tgz"], check=True)
    subprocess.run(["tar", "-xvf", "flink.tgz"], check=True)
    os.remove("flink.tgz")


def prepare():
    # Run Maven
    subprocess.run(["mvn", "package"], check=True)
    # Set config file
    subprocess.run(["cp", "flink-conf.yaml", os.path.join(flink, "conf")], check=True)
    # CLeanup log files
    subprocess.run(f"rm -rf {flink}/log/*", shell=True, check=True)


def run_flink_job(query, parallelism):
    # Start Flink cluster
    subprocess.run([os.path.join(flink, "bin", "start-cluster.sh")], check=True)
    # Start query
    print(f"Now running query {query} with {parallelism} threads.")
    subprocess.run([os.path.join(flink, "bin", "flink"), "run", "--class", f"de.tub.nebulastream.benchmarks.flink.{query}", jar_path, "--parallelism", parallelism]) # continue even if it fails
    # Stop Flink cluster
    subprocess.run([os.path.join(flink, "bin", "stop-cluster.sh")], check=True)


def analyze_logs(query_name, parallelism):
    # Find log file
    all_log_files = os.listdir(os.path.join(flink, "log"))
    matching_files = [f for f in all_log_files if f.startswith("flink-") and "taskexecutor-" in f and f.endswith(".log")]
    if not len(matching_files) == 1:
        print(f"Log file not found.")
    log_file = os.path.join(flink, "log", matching_files[0])

    # Calculate throughput
    subprocess.run(["java", "-cp", jar_path, "de.tub.nebulastream.benchmarks.flink.utils.AnalyzeTool", log_file, query_name, parallelism], check=True)


def main():
    global flink_version
    flink_version = f"flink-{args.flink_version}"

    if not os.path.exists(flink):
        download_flink()

    #for parallelism in ["1", "2", "4", "8"]: #, "16"]:
    for parallelism in ["8"]:
        for query_name, query_class in queries.items():
            prepare()
            run_flink_job(query_class, parallelism)
            analyze_logs(query_name, parallelism)

if __name__ == "__main__":
    main()
