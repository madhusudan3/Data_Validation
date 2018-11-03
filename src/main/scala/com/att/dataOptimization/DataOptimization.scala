/*

nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.dataOptimization.DataOptimization \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.dynamicAllocation.minExecutors=5 \
--conf spark.dynamicAllocation.maxExecutors=100 \
--conf "spark.sql.shuffle.partitions=500" \
--conf "spark.sql.autoBroadCastJoinThreshold=250mb" \
--conf "spark.driver.memoryOverhead=3200" \
--conf "spark.executor.memoryOverhead=2400" \
--executor-memory 32G \
--driver-memory 20g \
--executor-cores 4 \
s3://pr-ip-binnew-4978-us-east-1/dataValidation-assembly-1.0.jar "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/" "/user/hadoop/dl/viewership/eview/ev_ev_viewing_history/" "s3://pr-ip-att-4978-us-east-1/" "20171125" "arn:aws:kms:us-east-1:497834522136:key/787663dc-56cb-4d86-93ec-3100ddd5c820" > ~/vw_20171125.log &



program_watch_start_dt_local=
inputPath outputPath basePath filter

*/


package com.att.dataOptimization

import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import org.apache.spark.sql.{SparkSession, _}

import org.apache.spark.sql.functions._

import java.net.URI

import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import java.util.ArrayList
import org.apache.spark.sql.{DataFrame}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration
import com.amazonaws.services.s3.model._
import scala.util.control.NonFatal
import com.amazonaws.services.s3.AmazonS3ClientBuilder


object DataOptimization {

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

  def depth3(tablePath: String, filter: String, s3fs: org.apache.hadoop.fs.FileSystem): Array[String] = {

    val childDepth1Array = findChildPath(tablePath, s3fs).filter(_.contains(filter))
    val childDepth2Array = findChilds(childDepth1Array, s3fs)

    println("DataOptimization: ===================== Starting Depth3 ==============================")
    val childDepth3Array = findChilds(childDepth2Array, s3fs)
    println("DataOptimization: ===================== Completed Depth3 ==============================")
    childDepth3Array

  }

  def copyFile(keyId: String, bucket: String, s3Path: String, f: String) = {
    val fs = FileSystem.get(new Configuration())
    val filePath = new Path(f)
    val is = fs.open(filePath)
    val om = new ObjectMetadata()
    val status = fs.getFileStatus(filePath)
    om.setContentLength(status.getLen)
    try {
      val request = new PutObjectRequest(bucket, s"${s3Path}/${filePath.getName}", is, om)
        .withSSEAwsKeyManagementParams(new SSEAwsKeyManagementParams(keyId))
      AmazonS3ClientBuilder.defaultClient().putObject(request)
      fs.delete(filePath, false)
      true
    } catch {
      case NonFatal(e) => {
        e.printStackTrace()
      }
        false
    }
  }

  def hdfsToS3(spark: SparkSession, keyId: String, localPath: String, bucket: String, s3Path: String) = {
    val fs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
    val path = new Path(localPath)
    val files = fs.listStatus(path)
      .filter(fs => fs.isFile)
      .map(f => f.getPath.toString)

    val parallelUpload = spark.sparkContext.parallelize(files).map(
      f => {
        copyFile(keyId, bucket, s3Path, f)
      }
    )
    val failedCopies = parallelUpload.filter(x => x == false).count
    // Get rid of the directory if all files were successfully copied
    if (failedCopies == 0) {
      fs.delete(path, true)
    }
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

    val inputPath = args(0) // "s3://dv-mac-attdev-5943-us-east-1/dds/commoncontent/viewership/OTT/"
    val outputPath = args(1) // "hdfs:///user/gm201k/testData/"
    val basePath = args(2) // "s3://dv-mac-attdev-5943-us-east-1/"
    val filter = args(3)  // "20180501"
    val outputLoad_dt = 99999999
    val keyId = args(4)

//    val inputPath = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/"
//    val outputPath = "/user/hadoop/dl/viewership/eview/ev_ev_viewing_history/"
//    val basePath = "s3://pr-ip-att-4978-us-east-1/"
//    val filter = "20171130"
//    val outputLoad_dt = 99999999
//    val keyId = "arn:aws:kms:us-east-1:497834522136:key/787663dc-56cb-4d86-93ec-3100ddd5c820"

    val s3fs = FileSystem.get(new URI(basePath), sc.hadoopConfiguration)

    val allChildPathsArray = depth3(inputPath, filter, s3fs)

    val totalNumPaths = allChildPathsArray.length
    var lineNumber = 0

    println(s"DataOptimization: TotaNumPaths: $totalNumPaths")

    allChildPathsArray.distinct.map { childPath =>

      lineNumber+=1
      val statusPercentage = ((lineNumber * 100.00)/totalNumPaths).formatted("%1.2f")

      println(s"DataOptimization: Executing for path $childPath")
      println(s"DataOptimization: Running for $lineNumber out of $totalNumPaths folders")

      val inputData = spark.read.parquet(s"$childPath").drop("load_dt").withColumn("load_dt",lit(s"$outputLoad_dt"))

      val path = childPath.replaceAll(inputPath, "")

      val finalPath = outputPath+path

      println(s"DataOptimization: Output path $finalPath")

      inputData.write.mode(SaveMode.Overwrite).partitionBy("load_dt").parquet(s"$finalPath")

      println(s"DataOptimization: Copied data to HDFS $childPath")

      val filename = finalPath.replace("/user/hadoop/","")
      val outputFolder = "pr-ip-att-4978-us-east-1"

      val filename1 = filename+"load_dt=99999999"
      hdfsToS3(spark, keyId, filename1, outputFolder, filename1)

      println(s"DataOptimization: Copied data to S3 $childPath")
      println(s"DataOptimization: ---------- Application progress status: $statusPercentage % ----------")

    }
    spark.close

  }
}

//filter(col("load_dt") =!= "99999999")
//val path1 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=ATT-OTT/source_system=OMNITURE/"
//val path2 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=ATT-OTT/source_system=OMNITURE/load_dt=99999999/"
//(spark.read.parquet(path2).count)*2==spark.read.parquet(path2).count
//
//val path1 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=DIRECTV/source_system=AMS/"
//val path2 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=DIRECTV/source_system=AMS/load_dt=99999999/"
//(spark.read.parquet(path2).count)*2==spark.read.parquet(path2).count
//
//val path1 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=DIRECTV/source_system=OMNITURE/"
//val path2 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=DIRECTV/source_system=OMNITURE/load_dt=99999999/"
//(spark.read.parquet(path2).count)*2==spark.read.parquet(path1).count
//
//val path1 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=OTT/source_system=QUICKPLAY/"
//val path2 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=OTT/source_system=QUICKPLAY/load_dt=99999999/"
//(spark.read.parquet(path2).count)*2==spark.read.parquet(path1).count
//
//val path1 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=U-verse/source_system=IPTV_DC/"
//val path2 = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history/program_watch_start_dt_local=20171130/business_line=U-verse/source_system=IPTV_DC/load_dt=99999999/"
//(spark.read.parquet(path2).count)*2==spark.read.parquet(path1).count



//val path = "s3://pr-ip-att-4978-us-east-1/dl/viewership/eview/ev_ev_viewing_history"
//
//val df = spark.read.parquet(path)
//
//df.createOrReplaceTempView("inputTable")
//
//spark.sql("select program_watch_start_dt_local, business_line, source_system, count(*) from inputTable where program_watch_start_dt_local between 20171120 and 20171125 group by program_watch_start_dt_local, business_line, source_system order by program_watch_start_dt_local")
//
//
//
//
//
//
//
//val path = "/user/hadoop/dl/viewership/eview/ev_ev_viewing_history/"
//
//val df = spark.read.parquet(path).filter(col("program_watch_start_dt_local") === "20171125").filter(col("load_dt") === "99999999")
//
//df.createOrReplaceTempView("inputTable")
//
//spark.sql("select program_watch_start_dt_local, business_line, source_system, count(*) from inputTable where program_watch_start_dt_local = 20171125 group by program_watch_start_dt_local, business_line, source_system order by program_watch_start_dt_local")
//
//
//val path = "/user/hadoop/dl/viewership/eview/ev_ev_viewing_history/"
//
//val df = spark.read.parquet(path).filter(col("program_watch_start_dt_local") === "20171125").filter(col("load_dt") =!= "99999999")
//
//df.createOrReplaceTempView("inputTable")
//
//spark.sql("select program_watch_start_dt_local, business_line, source_system, count(*) from inputTable where program_watch_start_dt_local = 20171125 group by program_watch_start_dt_local, business_line, source_system order by program_watch_start_dt_local")
