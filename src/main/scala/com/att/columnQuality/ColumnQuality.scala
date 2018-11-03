/*
Improper value column count is compared against total row count for a specified dataset on a day

Example Run Command:

KM:
nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.columnQuality.ColumnQuality \
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
--files /usr/hdp/current/spark-client/conf/hive-site.xml,/home/gm201k/dataValidation.conf#dataValidation.conf  \
--conf "spark.driver.extraJavaOptions=-Dconfig.file=dataValidation.conf" \
~/data-engineering-datavalidation-assembly-1.0.jar "$configpath/ColumnQualityConfigFiles/" "20180101" "20180101" > ~/columnquality_20180101.log &

AWS:
nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.columnQuality.ColumnQuality \
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
/home/gm201k@macdev.att.com/data-engineering-datavalidation-assembly-1.0.jar "s3://dv-mac-attdev-5943-us-east-1/dv-gm201k/dataValidationConfigFiles/ColumnQualityConfigFiles/" "20180831" "20180831" > /home/gm201k@macdev.att.com/ColumnQuality_20180831.log &


*/


package com.att.columnQuality

import java.text.SimpleDateFormat
import java.util.Calendar

import com.att.utility.BaseUtility
import org.apache.spark.SparkContext
import org.apache.spark.sql.{SparkSession, _}


object ColumnQuality extends BaseUtility {

  def columnQualityCodeExecution(line: Row, startDate: Int, endDate: Int, sparkSession: SparkSession, sc: SparkContext) {

    //Parsing Config File
    val environment = line(0).toString
    val database = line(1).toString
    val tableType = line(2).toString
    val businessLine = line(3).toString
    val viewMode = line(4)
    val viewType = line(5)
    val dataQualityColumn = line(6).toString
    val priority = Option(line(7)).getOrElse("HIGHPRIORITYCOLUMN")
    val columnLength = Option(line(8)).getOrElse(1)

    val priorityArray = priorityConditionDef(priority.toString.toUpperCase,columnQualityExecutionPriority)

    if (priorityArray.contains(priority.toString.toUpperCase)) {

      val (table, partitionField) = findTableANDPartitionNames(tableType)

      val datesArray = datesCalculation(startDate, endDate)
      val extraWhereCondition = whereCondition(table.toUpperCase, businessLine, viewMode, viewType)
      val dateFormat = new SimpleDateFormat("yyyyMMdd")
      val timeStampFormat = new SimpleDateFormat("yyyyMMddhhmmssSSS")

      datesArray.distinct.map { dateColumn =>

        println(s"""ColumnQuality: Date $dateColumn, table $table, ColumnQualityField $dataQualityColumn, where condition $extraWhereCondition """)

        val executionStartTime = Calendar.getInstance.getTimeInMillis

        val improperColumnCountQuery = s"""SELECT $dataQualityColumn FROM ${database.trim}.${table.toLowerCase.trim} WHERE $partitionField=$dateColumn AND $extraWhereCondition AND ($dataQualityColumn is null or length($dataQualityColumn) < $columnLength or UPPER($dataQualityColumn) in ('NULL','UNKNOWN','', 'NA', 'N/A'))"""
        val improperColumnCount = sparkSession.sql(s"""$improperColumnCountQuery""").count

        println(s"""ColumnQuality: Improper row count for the field $dataQualityColumn is $improperColumnCount""")

        //if (improperColumnCount > 0) {

          val totalColumnCount = sparkSession.sql(s"""SELECT $dataQualityColumn FROM $database.$table WHERE $partitionField=$dateColumn AND $extraWhereCondition""").count

          println("ColumnQuality:    Total field count for the field " + dataQualityColumn + " is " + totalColumnCount)
          val improperColumnPercentage = (improperColumnCount * 100.00) / totalColumnCount

          val category = findCategory(improperColumnPercentage)

          println("ColumnQuality:    Percentage of improper value for the field " + dataQualityColumn + " is : " + improperColumnPercentage)
          val improperColumnPercent = improperColumnPercentage.formatted("%1.2f")
          val logDate = dateFormat.format(Calendar.getInstance.getTime)

          val timeStamp = timeStampFormat.format(Calendar.getInstance.getTime)

          println(s"""ColumnQuality:     $timeStamp, $environment, $database, $tableType,$viewMode,$businessLine,$viewType,$dataQualityColumn,$improperColumnCount,$improperColumnPercent,$totalColumnCount,$category,$logDate,$priority,$dateColumn""")
          sparkSession.sql(s""" INSERT INTO $outputDatabase.$columnQualityOutputTable PARTITION (partitionDate) SELECT '$timeStamp', '$environment', '$database', '$tableType', '$viewMode', '$businessLine', '$viewType', '$dataQualityColumn', '$improperColumnCount', '$improperColumnPercent', '$totalColumnCount', '$category', '$logDate', '$priority','$dateColumn' """)

          // println(s"""     $timeStamp,$improperColumnCountQuery,ColumnQuality,$dateColumn,$logDate""")
          sparkSession.sql(s""" INSERT INTO $outputDatabase.$queryLogTable VALUES ('$timeStamp', "$improperColumnCountQuery", 'ColumnQuality', '$dateColumn','$logDate')""")

        //}
        val executionEndTime = Calendar.getInstance.getTimeInMillis
        println("ColumnQuality: Time taken in seconds " + (executionEndTime - executionStartTime) / 1000 + " sec")
      }
    }
  }

  def main(args: Array[String]) {

    val timeFormat = new SimpleDateFormat("yyyyMMddhhmmss")
    val jobStartTime = timeFormat.format(Calendar.getInstance.getTime)

    val spark = SparkSession.builder()
      .enableHiveSupport()
      .appName(this.getClass.getName + jobStartTime)
      .enableHiveSupport()
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .getOrCreate()

    // Set spark code Log Level
    val sc = spark.sparkContext
    sc.setLogLevel(sparkLogLevel)

    // Arguments
    val automateFilePath = args(0)
    val startDate = args(1).toInt
    val endDate = args(2).toInt

    println(s"ColumnQuality: Starting Column Quality Check For StartDate $startDate EndDate $endDate")

    // Loading the config File
    val automateFile = spark.read.option("delimiter", confFileDelimiter).option("header",confFileHeader).csv(s"$automateFilePath")

    // Variables for capturing application progress status
    val totalLineCount = automateFile.distinct.count
    var lineNumber = 0

    automateFile.distinct.collect.map { line =>

      lineNumber+=1
      val statusPercentage = ((lineNumber * 100.00)/totalLineCount).formatted("%1.2f")

      columnQualityCodeExecution(line, startDate, endDate, spark, sc)

      println(s"ColumnQuality: ---------- Application progress status: $statusPercentage % ----------")
    }
    spark.close

  }
}