// Databricks notebook source exported at Tue, 28 Jun 2016 11:22:17 UTC
// MAGIC %md
// MAGIC # [Scalable Data Science](http://www.math.canterbury.ac.nz/~r.sainudiin/courses/ScalableDataScience/)
// MAGIC 
// MAGIC 
// MAGIC ## Reading NetFlow files in Apache Spark
// MAGIC 
// MAGIC ### Scalable data science project by Ivan Sadikov
// MAGIC 
// MAGIC *supported by* [![](https://raw.githubusercontent.com/raazesh-sainudiin/scalable-data-science/master/images/databricks_logoTM_200px.png)](https://databricks.com/)
// MAGIC and 
// MAGIC [![](https://raw.githubusercontent.com/raazesh-sainudiin/scalable-data-science/master/images/AWS_logoTM_200px.png)](https://www.awseducate.com/microsite/CommunitiesEngageHome)

// COMMAND ----------

// MAGIC %md
// MAGIC 
// MAGIC **NetFlow** is a flexible and extensible method/format to record network performance data developed by Cisco, e.g. for network security, monitoring network traffic, etc. Here is a link to Wikipedia page [describing it](https://en.wikipedia.org/wiki/NetFlow). 
// MAGIC 
// MAGIC The basic unit of NetFlow is a **flow**, which essentially defines interaction between source and destination, including both sides' IPs, ports, number of bytes sent, etc, depending on format.
// MAGIC 
// MAGIC Currently there are several NetFlow formats out there, most common are _5_, _7_, and the latest format _9_. Each file consists of **header** and **content**. Header includes information about format version, stream version, start capturing time and end capturing time, flow count, vendor identifier, comment, etc. It also has set of flags that define processing, including compression flag. Content consists of fixed size records, for example, for NetFlow v5, record size is 64 bytes, and includes about 2 dozen fields. You can find list of available fields on Cisco website, Wikipedia page or just by searching for a specific format. 
// MAGIC 
// MAGIC One of the most popular tools to use for reading and writing NetFlow files is [flow-tools](http://linux.die.net/man/1/flow-tools) library, which gives use different commands of interacting with the data, e.g. displaying raw data, filtering data, or creating an aggregated report + generating dummy data.

// COMMAND ----------

// MAGIC %md 
// MAGIC In this notebook we assume that data has been already collected by routers, we will be reading exported files using [spark-netflow](https://spark-packages.org/package/sadikovi/spark-netflow) package, [GitHub link](https://github.com/sadikovi/spark-netflow).
// MAGIC 
// MAGIC **spark-netflow** package is a datasource for reading NetFlow files in Spark SQL. It currently supports only export version 5 and 7, but other versions can easily be added later. Here we will go through basic usage and features, but I recommend to have a look at [README](https://github.com/sadikovi/spark-netflow#features) in repository for some additional features, that are not mentioned in notebook, e.g. statistics.

// COMMAND ----------

// MAGIC %md
// MAGIC ## Initial setup (importing files and adding library)

// COMMAND ----------

// MAGIC %md 
// MAGIC Download couple of example files generated by **flow-tools** for versions 5 and 7.

// COMMAND ----------

// MAGIC %sh
// MAGIC rm /tmp/ftv*
// MAGIC curl -L -o /tmp/netflow-v5-sample https://github.com/sadikovi/spark-netflow/raw/master/src/test/resources/correct/ftv5.2016-01-13.nocompress.bigend.sample
// MAGIC curl -L -o /tmp/netflow-v7-sample https://github.com/sadikovi/spark-netflow/raw/master/src/test/resources/correct/ftv7.2016-02-14.compress.9.bigend.sample
// MAGIC ls -lh /tmp

// COMMAND ----------

// MAGIC %md
// MAGIC Then copy files on `dbfs`. **Note** that this **removes** and creates `/tmp/netflow`, so if you keep some files in this folder, it is recommended to copy them in different directory.

// COMMAND ----------

dbutils.fs.rm("/tmp/netflow", true)
dbutils.fs.mkdirs("/tmp/netflow")
dbutils.fs.cp("file:/tmp/netflow-v5-sample", "dbfs:/tmp/netflow/netflow-v5-sample")
dbutils.fs.cp("file:/tmp/netflow-v7-sample", "dbfs:/tmp/netflow/netflow-v7-sample")
display(dbutils.fs.ls("dbfs:/tmp/netflow"))

// COMMAND ----------

// MAGIC %md
// MAGIC Make sure you attached library to the cluster, in order to do that read [Databricks guide on adding external library](https://docs.cloud.databricks.com/docs/latest/databricks_guide/02%20Product%20Overview/04%20Libraries.html), and select these steps:
// MAGIC - On a screen to upload library, choose `Maven coordinates` instead of uploading a jar
// MAGIC - And press button `Search Spark Packages and Maven Central`
// MAGIC - Wait until it loads Spark packages and then search for `spark-netflow`
// MAGIC - Select latest version `1.0.0-s_2.10` for Scala 2.10.x
// MAGIC 
// MAGIC I also recommend restarting cluster after adding library. 
// MAGIC After that, on `Clusters` page, you should see your cluster with new attached library.

// COMMAND ----------

// MAGIC %md
// MAGIC ## Working with datasource
// MAGIC Now we are all set, let's make some simple queries against files we have saved on `dbfs`.

// COMMAND ----------

// Usage is similar to either JSON or Parquet datasources
// Note that "version" option is required, which means we only read files of the same export version, in this case 5.
// Refer to GitHub page to see full list of columns for a version
val df = sqlContext.read.format("com.github.sadikovi.spark.netflow").option("version", "5").load("/tmp/netflow/netflow-v5-sample")

// COMMAND ----------

// Number of records in the file
println(s"Count: ${df.count()}")

// COMMAND ----------

display(df.select("srcip", "dstip", "protocol", "octets"))

// COMMAND ----------

// Here is some aggregation queries
display(df.groupBy("protocol").sum("octets"))

// COMMAND ----------

// You can also view raw NetFlow data, e.g. IP addresses as numbers, raw protocol values by disabling conversion
val df = sqlContext.read.format("com.github.sadikovi.spark.netflow").option("version", "5").option("stringify", "false").load("/tmp/netflow/netflow-v5-sample")

display(df.select("srcip", "dstip", "protocol", "octets"))

// COMMAND ----------

// You can also use short-cuts by importing netflow package
import com.github.sadikovi.spark.netflow._

// Currently available `netflow5` and `netflow7` methods
val df = sqlContext.read.netflow7("/tmp/netflow/netflow-v7-sample")
display(df)

// COMMAND ----------

// MAGIC %md
// MAGIC **spark-netflow** allows to select any combination of columns, or do aggregation the way you want, since it is essentially Spark SQL. You can use SQL to do filtering by any column in a dataset. This is something you cannot do with _flow-tools_ that are limited in a way of displaying data (you can only choose between certain reports or aggregated reports), and also you need to create a filter file using specific syntax.

// COMMAND ----------

val df = sqlContext.read.netflow7("/tmp/netflow/netflow-v7-sample")
// All filters are pushed down to the datasource by default
val filteredDF = df.filter($"protocol" === "UDP" && $"srcip" === "0.0.0.10" && $"srcport" > 9).select("srcip", "dstip", "octets")
display(filteredDF)

// COMMAND ----------

// Package also supports predicate-pushdown on unix time of the flow, so it discard file before reading content
val filteredDF = df.filter($"unix_secs" >= 1)

// This will use `SkipScan` strategy which will be logged on executor, something like that:
// 16/06/05 04:22:33 INFO NetFlowRelation: Reduced filter: Some(GreaterThanOrEqual(unix_secs,1))
// 16/06/05 04:22:33 INFO NetFlowRelation: Resolved NetFlow filter: Some(Ge(Column(unix_secs)[Long][0], 1))
// 16/06/05 04:22:33 INFO NetFlowRelation: Resolved statistics index: Map()
// 16/06/05 04:22:34 INFO NetFlowReader: Skip scan based on strategy Strategy [SkipScan]

filteredDF.show()

// COMMAND ----------

// MAGIC %md
// MAGIC **spark-netflow** also supports some features that are bit tricky to show here: 
// MAGIC - collecting statistics (see below) - automatically collects and applies statistics while reading a file
// MAGIC - different partitioning schema (file per partition, fixed number of partitions, or auto-partitioning)

// COMMAND ----------

// You can collect statistics, so next time file is read, it will check predicate against those statistics without reading content of the file, here is an example:
val df = sqlContext.read.option("statistics", "true").netflow5("/tmp/netflow/netflow-v5-sample")

// In logs you should see something like this:
// 16/06/05 04:31:15 INFO NetFlowRelation: Statistics: Some(StatisticsPathResolver(None))
// 16/06/05 04:43:15 INFO NetFlowRelation: Resolved statistics index: Map(0 -> MappedColumn(unix_secs,Column(unix_secs)[Long][0],true,None)...)

// Now, do simple count to collect statistics (refer to the README on GitHub page for statistics conditions)
df.count()

// COMMAND ----------

// MAGIC %md
// MAGIC Let's check file system, you should see new statistics file `.statistics-netflow-v5-sample` added

// COMMAND ----------

// MAGIC %fs
// MAGIC ls /tmp/netflow

// COMMAND ----------

// Now let's read file and filter by IP, this will trigger statistics read, and predicate will be resolved including that information.
// Searching by IP which is not in the file should result in `SkipScan`.
val filteredDF = df.select("srcip", "dstip", "octets").filter($"dstip" === "192.168.0.1")
filteredDF.show()

// In logs you should see something similar to this:
// 16/06/05 04:38:05 INFO NetFlowRelation: Reduced filter: Some(EqualTo(dstip,192.168.0.1))
// 16/06/05 04:38:05 INFO NetFlowRelation: Resolved NetFlow filter: Some(Eq(Column(dstip)[Long][20], 3232235521))
// ...
// 16/06/05 04:38:06 INFO NetFlowReader: Skip scan based on strategy Strategy [SkipScan]

// COMMAND ----------

// MAGIC %md
// MAGIC # [Scalable Data Science](http://www.math.canterbury.ac.nz/~r.sainudiin/courses/ScalableDataScience/)
// MAGIC 
// MAGIC 
// MAGIC ## Reading NetFlow files in Apache Spark
// MAGIC 
// MAGIC ### Scalable data science project by Ivan Sadikov
// MAGIC 
// MAGIC *supported by* [![](https://raw.githubusercontent.com/raazesh-sainudiin/scalable-data-science/master/images/databricks_logoTM_200px.png)](https://databricks.com/)
// MAGIC and 
// MAGIC [![](https://raw.githubusercontent.com/raazesh-sainudiin/scalable-data-science/master/images/AWS_logoTM_200px.png)](https://www.awseducate.com/microsite/CommunitiesEngageHome)