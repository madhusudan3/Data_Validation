/*


nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.consistency.columnConsistency.ColumnConsistencyCheck \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.dynamicAllocation.minExecutors=30 \
--conf spark.dynamicAllocation.maxExecutors=60 \
--conf "spark.sql.shuffle.partitions=500" \
--conf "spark.sql.autoBroadCastJoinThreshold=250mb" \
--conf "spark.yarn.driver.memoryOverhead=3200" \
--conf "spark.yarn.executor.memoryOverhead=2400" \
--executor-memory 32G \
--driver-memory 4g \
--executor-cores 4 \
--conf "spark.driver.extraJavaOptions=-Dconfig.file=dataValidation.conf" \
--files /usr/hdp/current/spark-client/conf/hive-site.xml,$codepath/dataValidation.conf \
--principal "gm201k" \
--keytab  /home/gm201k/gm201k.keytab \
$codepath/dataValidation-assembly-1.0.jar "$configpath/ColumnQualityConfigFiles/" "20180701" "20180701" > $codepath/logs/ColumnConsistencyCheck/columnConsistency_20180701.log &

nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.consistency.columnConsistency.ColumnConsistencyCheck \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.dynamicAllocation.minExecutors=5 \
--conf spark.dynamicAllocation.maxExecutors=50 \
--conf "spark.sql.shuffle.partitions=500" \
--conf "spark.sql.autoBroadCastJoinThreshold=250mb" \
--conf "spark.yarn.driver.memoryOverhead=3200" \
--conf "spark.yarn.executor.memoryOverhead=2400" \
--executor-memory 32G \
--driver-memory 4g \
--executor-cores 4 \
--conf "spark.driver.extraJavaOptions=-Dconfig.file=dataValidation.conf" \
--files /home/gm201k@macdev.att.com/dataValidation.conf \
/home/gm201k@macdev.att.com/dataValidation-assembly-1.0.jar "s3://dv-mac-attdev-5943-us-east-1/dv-gm201k/dataValidationConfigFiles/ColumnQualityConfigFiles/" "20180831" "20180831" > /home/gm201k@macdev.att.com/columnConsistency_20180831.log &

*/


package com.att.countComparision

import java.text.SimpleDateFormat
import java.util.Calendar

import com.att.utility.BaseUtility
import org.apache.spark.SparkContext
import org.apache.spark.sql.{DataFrame, SparkSession}

object PreCheckCountComparision extends BaseUtility {

  def loadParquetFilesToDF(path: String, groupByColumns: String, filterCondition: String, spark: SparkSession): DataFrame = {

    val inputDF = spark.read.parquet(path)
    inputDF.createOrReplaceTempView("input_table")
    val outputDF = spark.sql(s"""SELECT $groupByColumns, COUNT(*) FROM input_table WHERE program_watch_start_dt_local LIKE GROUP BY $groupByColumns""")

    outputDF
  }

  def loadTable(tableName: String, groupByColumns: String, filterCondition: String, spark: SparkSession): DataFrame = {

    val outputDF = spark.sql(s"""SELECT $groupByColumns, COUNT(*) FROM $tableName WHERE program_watch_start_dt_local LIKE GROUP BY $groupByColumns""")

    outputDF
  }

  def loadData(spark: SparkSession): DataFrame ={

    var outputDF = spark.emptyDataFrame

    //outputDF = loadTable()
    outputDF
  }

  def main(args: Array[String]) {

    val timeFormat = new SimpleDateFormat("yyyyMMddhhmmss")
    val jobStartTime = timeFormat.format(Calendar.getInstance.getTime)

    val appName = this.getClass.getName + jobStartTime
    val sparkSession = SparkSession
      .builder()
      .enableHiveSupport()
      .appName(appName)
      .enableHiveSupport()
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .getOrCreate()

    val sc = sparkSession.sparkContext
    sc.setLogLevel(sparkLogLevel)

    println("-------------------------------------------------------------------------------------------")



    sparkSession.close

  }
}