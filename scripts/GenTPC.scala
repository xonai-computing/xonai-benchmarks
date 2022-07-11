import com.databricks.spark.sql.perf.tpcds.TPCDSTables
import com.databricks.spark.sql.perf.tpch.TPCHTables
import org.apache.spark.sql._

// Dataset size in GB.
var scaleFactor = sys.env.getOrElse("TPC_SF", "1")

// Data format.
var format = sys.env.getOrElse("TPC_FORMAT", "parquet")

// TPC benchmark suite.
var suite = sys.env.getOrElse("TPC_SUITE", "tpch")

// Path to generate the data to.
var rootDir = sys.env.getOrElse("ROOT_DIR", sys.env("HOME") + s"/xonai-benchmarks/$suite/$scaleFactor/")

// Name of the database to be created.
var databaseName = s"${suite}_${format}_${scaleFactor}g"

//===------------------------------------------------------------------------===
// Data Generation.
//===------------------------------------------------------------------------===

var tables = suite match {
  case "tpcds" =>
    new TPCDSTables(
      spark.sqlContext,
      dsdgenDir = sys.env("TPCDS_DSDGEN_DIR"),
      scaleFactor,
      useDoubleForDecimal = true,
      useStringForDate = false)
  case "tpch" =>
    new TPCHTables(
      spark.sqlContext,
      dbgenDir = sys.env("TPCH_DBGEN_DIR"),
      scaleFactor,
      useDoubleForDecimal = true,
      useStringForDate = false)
  case _ =>
    println(s"invalid benchmark suite: $suite")
    sys.exit(1)
}

println(s"\n\nGenerating $suite tables in $format under $rootDir with $scaleFactor GB")

tables.genData(
  rootDir,
  format,
  overwrite = true,
  partitionTables = true,
  clusterByPartitionColumns = true,
  filterOutNullPartitionValues = false,
  tableFilter = "",
  numPartitions = 200)

sql(s"create database $databaseName")

println("\n\nCreating external tables")

tables.createExternalTables(
  rootDir,
  format,
  databaseName,
  overwrite = true,
  discoverPartitions = true)

tables.analyzeTables(databaseName, analyzeColumns = true)

sys.exit()
