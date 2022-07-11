package com.xonai

import com.databricks.spark.sql.perf.tpch.{TPCH, TPCHTables}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.functions.col
import org.apache.log4j.{Level, LogManager}
import scala.util.Try

object RunMainTPC {
  def main(args: Array[String]) {
    val tpcDataDir = args(0)
    val resultLocation = args(1)
    val tpcKit = args(2)
    val scaleFactor = Try(args(3)).getOrElse("1")
    val iterations = Try(args(4)).getOrElse("1").toInt
    val queriesArg: Set[String] = Try(args(5)).getOrElse("").split(",").toSet[String]
      .flatMap(entry => {
        if (entry.isEmpty) {
          None
        } else {
          Some(s"Q$entry")
        }
      })

    println(s"\n\nRunning RunTPCCloud reading from $tpcDataDir writing to $resultLocation using kit location $tpcKit")
    println(s"Scale $scaleFactor with $iterations iterations\n\n")
    val spark = SparkSession
      .builder
      .appName(s"TPC Cloud Benchmark $scaleFactor GB")
      .getOrCreate()
    LogManager.getLogger("org").setLevel(Level.WARN)
    val tables = new TPCHTables(spark.sqlContext,
      dbgenDir = tpcKit,
      scaleFactor = scaleFactor,
      useDoubleForDecimal = true, // ToDo: Change to false once decimal is supported by Fuse
      useStringForDate = false)
    tables.createTemporaryTables(tpcDataDir, "parquet")
    println("Created temporary tables")
    val tpch = new TPCH(spark.sqlContext)
    val queriesToRun = if (queriesArg.isEmpty) {
      tpch.queries
    } else {
      tpch.queries.filter(query => queriesArg.contains(query.name))
    }
    // Start experiment
    println(s"Starting run with queries ${queriesToRun.map(_.name)} using $iterations iterations, writing to $resultLocation")
    val experiment = tpch.runExperiment(
      queriesToRun,
      iterations = iterations,
      resultLocation = resultLocation,
      forkThread = true)
    experiment.waitForFinish(24 * 60 * 60)
    spark.stop()
  }
}
