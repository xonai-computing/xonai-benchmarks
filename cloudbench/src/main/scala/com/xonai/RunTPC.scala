package com.xonai

import com.databricks.spark.sql.perf.tpch.{TPCH, TPCHTables}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.functions.col
import org.apache.log4j.{Level, LogManager}
import scala.util.Try

object RunTPC {
  def main(args: Array[String]) {
    val tpcDataDir = args(0)
    val resultLocation = args(1)
    val tpcKit = args(2)
    val scaleFactor = Try(args(3)).getOrElse("1")
    val iterations = Try(args(4)).getOrElse("1").toInt
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
      useDoubleForDecimal = false,
      useStringForDate = false)
    tables.createTemporaryTables(tpcDataDir, "parquet")
    println("Created temporary tables")
    val tpch = new TPCH(spark.sqlContext)
    // Start experiment
    val experiment = tpch.runExperiment(
      tpch.queries,
      iterations = iterations,
      resultLocation = resultLocation,
      forkThread = true)
    experiment.waitForFinish(24 * 60 * 60)
    // Collect general results
    val resultPath = experiment.resultPath
    println(s"Reading result at $resultPath")
    val specificResultTable = spark.read.json(resultPath)
    specificResultTable.show()
    // Summarize results
    val result = specificResultTable
      .withColumn("result", explode(col("results")))
      .withColumn("executionSeconds", col("result.executionTime") / 1000)
      .withColumn("queryName", col("result.name"))
    result.select("iteration", "queryName", "executionSeconds").show()
    println(s"Final results at $resultPath")
    val aggResults = result.groupBy("queryName").agg(
      callUDF("percentile", col("executionSeconds").cast("double"), lit(0.5)).as('medianRuntimeSeconds),
      callUDF("min", col("executionSeconds").cast("double")).as('minRuntimeSeconds),
      callUDF("max", col("executionSeconds").cast("double")).as('maxRuntimeSeconds)
    ).orderBy(col("queryName"))
    aggResults.repartition(1).write.csv(s"$resultPath/summary.csv")
    aggResults.show(10)
    spark.stop()
  }
}
