#!/usr/bin/env python3

import argparse
import os
from os.path import exists
import subprocess
import sys


def get_env(**kwargs):
    xonai_home = os.environ['XONAI_HOME']
    # Find benchmark data generators.
    tpcds_dsdgen_dir = os.path.join(xonai_home, "tpcds-kit", "tools")
    tpch_dbgen_dir = os.path.join(xonai_home, "tpch-dbgen")
    if not os.path.isdir(tpcds_dsdgen_dir):
        print("error: {} is not a directory".format(tpcds_dsdgen_dir))
        sys.exit(1)
    if not os.path.isdir(tpch_dbgen_dir):
        print("error: {} is not a directory".format(tpch_dbgen_dir))
        sys.exit(1)

    env = os.environ.copy()
    env["TPCDS_DSDGEN_DIR"] = tpcds_dsdgen_dir
    env["TPCH_DBGEN_DIR"] = tpch_dbgen_dir
    for key, val in kwargs.items():
        if val != '':
            env[key] = val
    return env


def get_cmd(script, s3_location):
    xonai_home = os.environ['XONAI_HOME']
    spark_home = os.environ['SPARK_HOME']
    spark_shell = os.path.join(spark_home, "bin", "spark-shell")
    script = os.path.join(xonai_home, "xonai-benchmarks", "scripts", script)
    spark_sql_perf_jar = os.path.join(xonai_home, "spark-sql-perf", "target", "scala-2.12",
                                      "spark-sql-perf_2.12-0.5.1-SNAPSHOT.jar")
    conf_file = os.path.join(spark_home, "conf", "spark-defaults.conf")
    command = [spark_shell, "-i", script, "--jars", spark_sql_perf_jar, "--master"]
    if s3_location:
        command.append("local[*]")
        command.append("--driver-memory")
        command.append("6g")
        command.append("--packages")
        command.append("org.apache.hadoop:hadoop-aws:3.2.0")
        command.append("--conf")
        command.append("spark.local.dir=/data/spark-temp")  # external volume mount point
    else:
        command.append("local")
        if exists(conf_file):
            command.append("--properties-file")
            command.append(conf_file)
    print('\n\nCreated shell command: ' + str(command) + '\n\n')
    return command


def gen_tpc(suite, scale_factor, root):
    echo_ps = subprocess.run(("echo", ":exit"))
    cmd = get_cmd("GenTPC.scala", root.startswith('s3a://'))
    env = get_env(TPC_SUITE=suite, TPC_SF=str(scale_factor), ROOT_DIR=root)
    cmd_ps = subprocess.run(cmd, env=env, stdin=echo_ps.stdout)


def run_tpc(suite, num_iter, queries, root, output):
    echo_ps = subprocess.run(("echo", ":exit"))
    cmd = get_cmd("RunTPC.scala", root.startswith('s3a://'))
    env = get_env(TPC_SUITE=suite, TPC_ITER=str(num_iter), TPC_QUERIES=queries, ROOT_DIR=root, OUTPUT_DIR=output)
    cmd_ps = subprocess.run(cmd, env=env, stdin=echo_ps.stdout)


def main():
    if 'XONAI_HOME' not in os.environ:
        print("error: XONAI_HOME not in env (did you forget to activate?)")
        sys.exit(1)
    if 'SPARK_HOME' not in os.environ:
        print("error: Set SPARK_HOME to the location of your local Spark distribution")
        sys.exit(1)

    # Top-level argument parser.
    parser = argparse.ArgumentParser(
        description="Spark benchmarking with Xonai integration")
    parser.add_argument(
        "suite", choices=["tpcds", "tpch"], help="benchmark suite name")

    action_parsers = parser.add_subparsers(dest="action", help="benchmark actions", required=True)

    # Argument parser for data generation.
    gen_parser = action_parsers.add_parser("gen", help="generate dataset for a benchmark suite")
    gen_parser.add_argument("-s", "--size", type=int, default=1, choices=range(1, 100000), help="data set size in GB")
    gen_parser.add_argument("-r", "--root", type=str, default='', help="root directory for tpc tables")

    # Argument parser for benchmark execution.
    run_parser = action_parsers.add_parser('run', help="run a benchmark suite")
    run_parser.add_argument("-i", "--iter", type=int, default=1, choices=range(1, 1000),
                            help="number of times to run each query")
    run_parser.add_argument("-q", "--queries", default="", help="comma-separated list of query names to execute")
    run_parser.add_argument("-r", "--root", type=str, default='', help="root directory for tpc tables")
    run_parser.add_argument("-o", "--output", default="", help="directory where results are stored")

    args = parser.parse_args()

    if args.action == "gen":
        gen_tpc(args.suite, args.size, args.root)
    elif args.action == "run":
        run_tpc(args.suite, args.iter, args.queries, args.root, args.output)


if __name__ == '__main__':
    main()
