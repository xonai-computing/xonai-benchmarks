package com.xonai

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.functions.col

object SummarizeResults extends App {
  val spark = SparkSession
    .builder
    .appName(s"TPC Cloud Benchmark Summarization")
    .config("spark.hadoop.fs.s3a.access.key", "") // ToDo: Provide credentials
    .config("spark.hadoop.fs.s3a.secret.key", "") // ToDo: Provide credentials
    .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
    .master("local[1]")
    .getOrCreate()
  val intermediatePath = "" // ToDo: Specify S3 URI to the output of a previous invocation of RunMainTPC.scala
  val resultPath = "" // ToDo: Specify local result path

  println(s"Reading result at $intermediatePath")
  val specificResultTable = spark.read.json(intermediatePath)
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
  aggResults.repartition(1).write.csv(resultPath)
  aggResults.show(10)

  spark.stop()
}
