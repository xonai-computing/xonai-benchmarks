#!/bin/bash

if grep isMaster /mnt/var/lib/info/instance.json | grep false;
then
    echo "On worker node, exiting"
    exit 0
fi
echo "On master node, continuing to execute bootstrap script"

echo "Installing TPCH components"
yes | sudo yum update
yes | sudo yum install git
mkdir /home/hadoop/bench && cd /home/hadoop/bench
git clone https://github.com/databricks/tpch-dbgen && cd tpch-dbgen/
git checkout 0469309147b42abac8857fa61b4cf69a6d3128a8 -- bm_utils.c # relevant for data generation
make
cd ..
git clone https://github.com/databricks/spark-sql-perf
export XONAI_HOME=/home/hadoop/bench

echo "Bootstrapping finished"
