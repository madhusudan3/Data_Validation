/*

nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--queue High \
--class com.att.checkSchema.CheckSchema \
--conf "spark.driver.extraJavaOptions=-Dconfig.file=dataValidation.conf" \
--files /home/gm201k@macdev.att.com/dataValidation.conf#dataValidation.conf \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.dynamicAllocation.minExecutors=5 \
--conf spark.dynamicAllocation.maxExecutors=100 \
--conf "spark.sql.shuffle.partitions=500" \
--conf "spark.sql.autoBroadCastJoinThreshold=250mb" \
--conf "spark.driver.memoryOverhead=3200" \
--conf "spark.executor.memoryOverhead=2400" \
--executor-memory 32G \
--driver-memory 4g \
--executor-cores 4 \
s3://ms414xtest-dev-temp/goutham/dataValidation-assembly-1.0.jar 2018 > /home/gm201k@macdev.att.com/accountSchemaCheck_201803.log &

select count(*) from validation_framework.schema_check where schema_status != "Correct";
*/


package com.att.checkSchema

import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import org.apache.spark.sql.{SparkSession, _}
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.FileSystem
import java.net.URI

import com.att.checkSchema.CheckSchema.filter
import com.att.utility.BaseUtility

import scala.collection.mutable.ArrayBuffer


object CheckSchema extends BaseUtility {

  def findChildPath(tablePath: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    val path = new Path(tablePath)

    s3fs.listStatus(path).length

    var childPathArray = new ArrayBuffer[String]()

    for (counter <- 0 until s3fs.listStatus(path).length) {
      childPathArray += tablePath + s3fs.listStatus(path).apply(counter).getPath.getName + "/"
    }

    childPathArray.toArray
  }

  def findChilds(childPathArray: Array[String], s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    var childsPathArray = new ArrayBuffer[String]()

    for (childPath1 <- childPathArray) {

      //println(childPath1)

      for (childPath2 <- findChildPath(childPath1, s3fs)) {

        println(s"$childPath2")

        childsPathArray += childPath2

      }
    }
    childsPathArray.toArray
  }

  def depth1(tablePath: String, filter: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    println("===================== Starting Depth1 ==============================")
    println("===================== Starting Depth1 ==============================")
    val childDepth1Array = findChildPath(tablePath, s3fs).filter(_.contains(filter))
    println("===================== Completed Depth1 ==============================")
    println("===================== Completed Depth1 ==============================")
    childDepth1Array

  }

  def depth2(tablePath: String, filter: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    val childDepth1Array = findChildPath(tablePath, s3fs).filter(_.contains(filter))
    println("===================== Starting Depth2 ==============================")
    println("===================== Starting Depth2 ==============================")
    val childDepth2Array = findChilds(childDepth1Array, s3fs)
    println("===================== Completed Depth2 ==============================")
    println("===================== Completed Depth2 ==============================")
    childDepth2Array

  }


  def depth3(tablePath: String, filter: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    val childDepth1Array = findChildPath(tablePath, s3fs).filter(_.contains(filter))
    val childDepth2Array = findChilds(childDepth1Array, s3fs)
    println("===================== Starting Depth3 ==============================")
    println("===================== Starting Depth3 ==============================")
    val childDepth3Array = findChilds(childDepth2Array, s3fs)
    println("===================== Completed Depth3 ==============================")
    println("===================== Completed Depth3 ==============================")
    childDepth3Array

  }

  def depth4(tablePath: String, filter: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    val childDepth1Array = findChildPath(tablePath, s3fs).filter(_.contains(filter))
    val childDepth2Array = findChilds(childDepth1Array, s3fs)
    val childDepth3Array = findChilds(childDepth2Array, s3fs)
    println("===================== Starting Depth4 ==============================")
    println("===================== Starting Depth4 ==============================")
    val childDepth4Array = findChilds(childDepth3Array, s3fs)
    println("===================== Completed Depth4 ==============================")
    println("===================== Completed Depth4 ==============================")

    childDepth4Array

  }

  def depth5(tablePath: String, filter: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    val childDepth1Array = findChildPath(tablePath, s3fs).filter(_.contains(filter))
    val childDepth2Array = findChilds(childDepth1Array, s3fs)
    val childDepth3Array = findChilds(childDepth2Array, s3fs)
    val childDepth4Array = findChilds(childDepth3Array, s3fs)
    println("===================== Starting Depth5 ==============================")
    println("===================== Starting Depth5 ==============================")
    val childDepth5Array = findChilds(childDepth4Array, s3fs)
    println("===================== Completed Depth5 ==============================")
    println("===================== Completed Depth5 ==============================")

    childDepth5Array

  }

  def findAllChildPaths(depth: Int, filter: String, tablePath: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    var outputArray = new ArrayBuffer[String]()

    if (depth == 1) {
      for (path <- depth1(tablePath, filter, s3fs)) {
        outputArray += path
      }
    } else if (depth == 2) {
      for (path <- depth2(tablePath, filter, s3fs)) {
        outputArray += path
      }
    } else if (depth == 3) {
      for (path <- depth3(tablePath, filter, s3fs)) {
        outputArray += path
      }
    } else if (depth == 4) {
      for (path <- depth4(tablePath, filter, s3fs)) {
        outputArray += path
      }
    } else if (depth == 5) {
      for (path <- depth5(tablePath, filter, s3fs)) {
        outputArray += path
      }
    }
    outputArray.toArray
  }


  def main(args: Array[String]) {

    val timeFormat = new SimpleDateFormat("yyyyMMddhhmmss")
    val jobStartTime = timeFormat.format(Calendar.getInstance.getTime)

    val appName = this.getClass.getName + jobStartTime
    val spark = SparkSession.builder()
      .enableHiveSupport()
      .appName(appName)
      .enableHiveSupport()
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .getOrCreate()

    val sc = spark.sparkContext
    sc.setLogLevel("ERROR")
    val secondsFormat = new SimpleDateFormat("yyyyMMddhhmmss")

    val filter= args(0)

//    val referencePath = "s3://pr-mac-att-5478-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20180801/business_line=DIRECTV/source_system=AMS/load_dt=20180802/part-00000-38af2f0e-c182-4f8d-a1d8-d9de90f0b988-c000.gz.parquet" //args(0)
//    val checkPath = "s3://pr-mac-att-5478-us-east-1/dl/viewership/eview/ev_ev_viewing_history/" //args(1)
//    val basePath = "s3://pr-mac-att-5478-us-east-1/"
//    val depth = 5

    val s3fs = FileSystem.get(new URI(basePath), sc.hadoopConfiguration)

    val allChildPathsArray = findAllChildPaths(depth, filter, checkPath, s3fs).filter(!_.contains("HIVE_DEFAULT_PARTITION")).filter(!_.contains("_SUCCESS"))

    val referenceDFSchema = spark.read.format("parquet").option("header", true).option("inferSchema", true).load(referencePath).schema

    val currentTime = secondsFormat.format(Calendar.getInstance.getTime)

    println(s" log time $currentTime")

    allChildPathsArray.distinct.map { childPath =>

      val childDFSchema = spark.read.format("parquet").option("header", true).option("inferSchema", true).load(s"${childPath.trim}").schema

      if (referenceDFSchema == childDFSchema)
      {
        println(s" Schema Correct for $childPath")
        spark.sql(s""" INSERT INTO $outputDatabase.$schemaCheckTable PARTITION (log_time) SELECT 'Correct', '${childPath.trim}', '$currentTime' """)

      }
      else
      {
        println(s" ---------------- Schema Wrong for $childPath")
        spark.sql(s""" INSERT INTO $outputDatabase.$schemaCheckTable PARTITION (log_time) SELECT 'Wrong', '${childPath.trim}', '$currentTime' """)
      }

    }
    spark.close

  }
}