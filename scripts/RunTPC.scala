import com.databricks.spark.sql.perf.Query
import com.databricks.spark.sql.perf.ExecutionMode.CollectResults
import com.databricks.spark.sql.perf.tpcds.{TPCDS, TPCDSTables}
import com.databricks.spark.sql.perf.tpch.{TPCH, TPCHTables}
import org.apache.commons.io.IOUtils
import org.apache.spark.sql._

// Dataset size in GB.
var scaleFactor = sys.env.getOrElse("TPC_SF", "1")

// Data format.
var format = sys.env.getOrElse("TPC_FORMAT", "parquet")

// TPC benchmark kind.
var suite = sys.env.getOrElse("TPC_SUITE", "tpch")

// Path to read the data from.
var rootDir = sys.env.getOrElse("ROOT_DIR", sys.env("HOME") + s"/xonai-sql-perf-data/$suite/$scaleFactor/")

//===------------------------------------------------------------------------===
// Benchmark Execution.
//===------------------------------------------------------------------------===

if (suite != "tpcds" && suite != "tpch") {
  println(s"invalid benchmark suite: $suite")
  sys.exit(1)
}

val tpc = if (suite == "tpcds") {
  new TPCDS(spark.sqlContext)
} else {
  new TPCH(spark.sqlContext)
}

// Benchmark results will be written as JSON to this location.
var resultLocation = sys.env.getOrElse("OUTPUT_DIR", sys.env("HOME") + s"/xonai-sql-perf-data").stripSuffix("/")
resultLocation += s"/$suite/results"

// How many times to run the whole set of queries.
val iterations = sys.env.getOrElse("TPC_ITERS", "1").toInt

// Timeout in seconds.
val timeout = sys.env.getOrElse("TPC_TIMEOUT", s"${24 * 60 * 60}").toInt

// Selected queries to run.
var queryFilter = sys.env.getOrElse("TPC_QUERIES", "").split(',').toSeq

// Queries to run, which can be filtered.
def queries = {
  if (suite == "tpcds") {
    var tpcds = tpc.asInstanceOf[TPCDS]
    val filteredQueries = queryFilter match {
      case Seq("") => tpcds.tpcds2_4Queries
      case _ => tpcds.tpcds2_4Queries.filter(q => queryFilter.contains(q.name))
    }
    filteredQueries
  } else {
    var tpch = tpc.asInstanceOf[TPCH]
    val filteredQueries = queryFilter match {
      case Seq("") => tpch.queries
      case _ => tpch.queries.filter(q => queryFilter.contains(q.name))
    }
    filteredQueries
  }
}
if (queries.isEmpty) {
  println("error: no queries to run");
  sys.exit(1)
}

val tables = if (suite == "tpcds") {
  new TPCDSTables(
    spark.sqlContext,
    dsdgenDir = sys.env("TPCDS_DSDGEN_DIR"),
    scaleFactor)
} else {
  new TPCHTables(
    spark.sqlContext,
    dbgenDir = sys.env("TPCH_DBGEN_DIR"),
    scaleFactor)
}

tables.createTemporaryTables(rootDir, format)

val experiment = tpc.runExperiment(
  queries,
  iterations = iterations,
  resultLocation = resultLocation,
  forkThread = true)

experiment.waitForFinish(timeout)

val summary = experiment.getCurrentResults
  .withColumn("Name", substring(col("name"), 2, 100))
  .withColumn("Runtime", (col("parsingTime") + col("analysisTime") +
                          col("optimizationTime") + col("planningTime") +
                          col("executionTime")) / 1000.0)
  .select('Name, 'Runtime)

summary.show(9999, false)

sys.exit(0)
