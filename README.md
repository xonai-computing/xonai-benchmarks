# xonai-benchmarks

This repository contains scripts and resources that we created for running TPC benchmarks in the cloud:

## TPC-H benchmarks
### Data Generation
The `benchmark.py` script can be used to create TPC-H tables on S3. Before this script can be executed on an Ec2 instance,
a few variables must be set and several dependencies need to be installed:
1. The environment variable `XONAI_HOME` denotes the path to the benchmark root directory under which `xonai-benchmarks` and the projects mentioned 
below are placed, for example `/home/ec2-user/bench`
2. `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` hold the credentials for accessing S3.
3. A suitable Spark release can be downloaded from [here](https://archive.apache.org/dist/spark/spark-3.1.2/spark-3.1.2-bin-hadoop3.2.tgz).
In addition, the `SPARK_HOME` environment variable needs to be set to its installation location.
4. The [tpch-kit](https://github.com/databricks/tpch-dbgen) should be downloaded into `XONAI_HOME`. The project can be 
compiled with the commands from lines [15 and 16](https://github.com/xonai-computing/xonai-benchmarks/blob/0102102c9ca02f82ddf08661f6247ef1025f2a38/scripts/bootstrap_oss.sh#L15)
in the EMR bootstrap scripts.
5. The [tpcds-kit](https://github.com/databricks/tpcds-kit.git) should also be downloaded into `XONAI_HOME`. Its code is 
currently not used by our benchmark scripts but `spark-sql-perf` requires its presence.
6. The underlying library for most scripts in this repo is [spark-sql-perf](https://github.com/databricks/spark-sql-perf.git) which, 
just like the previous two kits, should be cloned under `XONAI_HOME`. It uses (sbt)[https://www.scala-sbt.org]
as build manager and can therefore be built with the command `sbt package`

After completing the steps mentioned above, the TPC-H tables can be created with the following command:
```
python3 $XONAI_HOME/xonai-sql-perf/benchmark.py tpch gen -r s3a://mybucket/tpch1500 -s 1500
```
This invocation uses a scale factor of 1500 GB (`-s 1500`) and writes the table contents under the S3 location 
`s3s://mybucket/tpch1500`

### Benchmark programs
The `cloudbench` module contains programs that can be used to execute TPC-H queries against a cluster. Their logic is
based on the benchmark code from [this](https://github.com/aws-samples/emr-on-eks-benchmark#benchmark-for-emr-on-ec2) 
AWS repo which was adopted for TPC-H. [RunMainTPC.scala](https://github.com/xonai-computing/xonai-benchmarks/blob/main/cloudbench/src/main/scala/com/xonai/RunMainTPC.scala)
executes the queries and writes the configuration, query plans, and various benchmarking times to an intermediate output 
file. [SummarizeResults.scala](https://github.com/xonai-computing/xonai-benchmarks/blob/main/cloudbench/src/main/scala/com/xonai/SummarizeResults.scala)
can be used locally to create a summary (min/median/max) of the query runtimes from the intermediate file. [RunTPC.scala](https://github.com/xonai-computing/xonai-benchmarks/blob/main/cloudbench/src/main/scala/com/xonai/RunTPC.scala)
includes both steps in a single program.

To build the cloudbench module, the Databricks [SQL perf library](https://github.com/databricks/spark-sql-perf) needs to 
be compiled first with the command `sbt +package`. The resulting JAR file should then be placed into a cloudbench/libs folder. The 
cloudbench submodule can then be compiled via `sbt assembly` and the resulting JAR file 
(_xonai-sql-perf/cloudbench/target/scala-2.12/cloudbench-assembly-1.0.jar_) can be used for local or cloud benchmarks:

### Local Execution
The following command executes all TPC-H queries in local mode:
``` terminal
$SPARK_HOME/bin/spark-submit \
--class com.xonai.RunMainTPC \
--master local[4] \
--conf spark.driver.memory=4g \
path/to/cloudbench-assembly-1.0.jar \
s3a://my_bucket/tpch10 s3a://my_bucket/bench_results /home/hadoop/bench/tpch-dbgen 10 3
```
The programs expect five arguments which are specified on the last line of the sample command above:
- A base path to a TPC-H dataset (_s3://my_bucket/tpch10_)
- An output path (_s3://my_bucket/bench_results_) 
- The local path under which the tpch-kit was installed, this was specified in the installation step 4 above
- The scale factor of the input table (_10_ GB) (not too important for executions)
- The total number of iterations (_3_)

### Cluster Executions
The [scripts](https://github.com/xonai-computing/xonai-benchmarks/tree/main/scripts) folder contains a number of
bootstrapping scripts for executing the TPC-H benchmarks against a cluster:

#### EMR on EC2
For benchmarking an EMR runtime, the [bootstrap_emr.sh](https://github.com/xonai-computing/xonai-benchmarks/blob/main/scripts/bootstrap_emr.sh)
script can be added to the bootstrapping actions of a cluster. It will install the TPC-H kit and spark-sql-perf library on 
the master node. In addition, a few adjustments should be made to `/usr/lib/spark/conf/spark-defaults.conf`: The value 
for `spark.dynamicAllocation.enabled` and `spark.shuffle.service.enabled` should be changed to `false` and the following 
properties should be appended to the end of the file:
```
spark.network.timeout 2000s
spark.executor.heartbeatInterval 300s
spark.sql.broadcastTimeout 3000s
spark.sql.adaptive.enabled true
spark.sql.adaptive.localShuffleReader.enabled true
spark.sql.adaptive.coalescePartitions.enabled true
spark.sql.adaptive.skewJoin.enabled true
```
Alternatively, the properties can also be supplied as command line arguments via `--conf`.

Similar to the local execution mode described above, TPC-H queries can now be executed against the EMR cluster with a 
command like
```
spark-submit \
--class com.xonai.RunMainTPC \
s3://location/to/cloudbench-assembly-1.0.jar \
s3://location/to/tpch_dataset/ s3://output/location /home/hadoop/bench/tpch-dbgen 3000 1
```

Open source Spark on EMR can be enabled with the help of the [bootstrap_oss.sh](https://github.com/xonai-computing/xonai-benchmarks/blob/main/scripts/bootstrap_oss.sh)
bootstrapping script. After the cluster is initialized, all configuration files from `/usr/lib/spark/conf/` on the master
node should be copied to `/home/hadoop/spark-3.1.2-bin-without-hadoop/conf/`, it is likely not possible to perform
this step as part of the bootstrapping process. The same modifications to `spark-defaults.conf` that were described in
the previous section should be made. Finally, the line `export SPARK_DIST_CLASSPATH=$(hadoop classpath)` should be added
to `spark-env.sh` and the entry for SPARK_HOME should be changed to `export SPARK_HOME=/home/hadoop/spark-3.1.2-bin-without-hadoop`

The open source runtime can now be used with a command similar to
```
/home/hadoop/spark-3.1.2-bin-without-hadoop/bin/spark-submit \
--class com.xonai.RunMainTPC \
s3://location/to/cloudbench-assembly-1.0.jar \
s3://location/to/tpch_dataset/ s3://output/location /home/hadoop/bench/tpch-dbgen 3000 1
```

#### Standalone Mode on EC2
Before Spark became part of the EMR release, applications could be run via standalone mode against an EC2 cluster. This
deployment option becomes less complicated when a dedicated Amazon Machine Image is created first. All system and runtime 
dependencies like Java should be installed on this image, the benchmark projects and Spark itself can be baked into the 
image by executing the steps from the EMR [bootstrap script](https://github.com/xonai-computing/xonai-benchmarks/blob/main/scripts/bootstrap_oss.sh).
In addition, a `spark-defaults.conf` with the same contents as above should be created. 

An EC2 cluster consisting of one Master node and one or more Worker nodes that all utilize the new AMI can now be
spawned. The master and worker daemons can be started by executing our [helper script](https://github.com/xonai-computing/xonai-benchmarks/blob/main/scripts/launch_standalone_daemons.py) 
locally. The script uses tags of running Ec2 instances to identify the cluster roles: An instance tagged
with the key-value pair `Purpose`: `Master` will be identified as Master node, any running instance
with `Purpose`: `Worker` will become a Worker. Several gaps in the script need to be filled, these
are marked with a `# ToDo`
 
After the Spark Workers have successfully registered, the master URL should be noted and used as part 
of the `spark-submit` command:
```
$SPARK_HOME/bin/spark-submit \
--class com.xonai.RunMainTPC \
--master spark://ip-....ec2.internal:7077 \
--deploy-mode client \
s3a://xonai/bootstrapping/cloudbench-assembly-1.0.jar \
s3a://location/to/tpch_dataset/ s3a://output/location /home/ubuntu/bench/tpch-dbgen 1000 1
```

## TPC-DS benchmarks
Coming soon