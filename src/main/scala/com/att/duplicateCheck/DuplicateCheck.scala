/*

For checking the duplicate rows count.
Example case: A particular setup box can view only one program at a time.

Example Run Command:

KM:
nohup spark-submit \
--master yarn \
--deploy-mode client \
--class com.att.duplicateCheck.DuplicateCheck \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.dynamicAllocation.minExecutors=30 \
--conf spark.dynamicAllocation.maxExecutors=60 \
--conf "spark.sql.shuffle.partitions=500" \
--conf "spark.sql.autoBroadCastJoinThreshold=250mb" \
--conf "spark.yarn.driver.memoryOverhead=3200" \
--conf "spark.yarn.executor.memoryOverhead=2400" \
--executor-memory 32G \
--driver-memory 16g \
--executor-cores 4 \
--conf "spark.driver.extraJavaOptions=-Dconfig.file=dataValidation.conf" \
--files /usr/hdp/current/spark-client/conf/hive-site.xml,/home/gm201k/dataValidation.conf \
~/data-engineering-datavalidation-assembly-1.0.jar "$configpath/DuplicateCheckAutomationFile.txt" "20180102" "20180102" > ~/DuplicateCheckAutomationFile_20180102.log &


AWS:
nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.duplicateCheck.DuplicateCheck \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.dynamicAllocation.minExecutors=5 \
--conf spark.dynamicAllocation.maxExecutors=10 \
--conf "spark.sql.shuffle.partitions=500" \
--conf "spark.sql.autoBroadCastJoinThreshold=250mb" \
--conf "spark.yarn.driver.memoryOverhead=3200" \
--conf "spark.yarn.executor.memoryOverhead=2400" \
--executor-memory 32G \
--driver-memory 4g \
--executor-cores 4 \
--conf "spark.driver.extraJavaOptions=-Dconfig.file=dataValidation.conf" \
--files /home/gm201k@macdev.att.com/dataValidation.conf \
/home/gm201k@macdev.att.com/data-engineering-datavalidation-assembly-1.0.jar "s3://dv-mac-attdev-5943-us-east-1/dv-gm201k/dataValidationConfigFiles/DuplicateCheckAutomationFile.txt" "20180801" "20180831" > /home/gm201k@macdev.att.com/DuplicateCheckAutomationFile_20180801_20180831.log &

*/


package com.att.duplicateCheck

import java.text.SimpleDateFormat
import java.util.Calendar

import com.att.utility.BaseUtility
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession



object DuplicateCheck extends BaseUtility {

  def duplicateCheckExecution(environment: String, database: String, tableType: String, duplicateCheckColumn: String, selectColumns: String, startDate: Int, endDate: Int, businessLine: String, viewMode: Any, viewType: Any, sparkSession: SparkSession, sc: SparkContext) {

    val (table, partitionField) = findTableANDPartitionNames(tableType)

    val datesArray = datesCalculation(startDate, endDate)
    val extraWhereCondition = whereCondition(table.toUpperCase, businessLine, viewMode, viewType)

    val dateFormat = new SimpleDateFormat("yyyyMMdd")
    val timeStampFormat = new SimpleDateFormat("yyyyMMddhhmmssSSS")

    datesArray.map { dateColumn =>
      println(s"""DuplicateCheck: Date $dateColumn, table $table, duplicate check column $duplicateCheckColumn, where condition $extraWhereCondition """)

      val executionStartTime = Calendar.getInstance.getTimeInMillis

      val duplicateCountQuery = s"""SELECT $selectColumns FROM $database.$table WHERE $partitionField=$dateColumn AND $extraWhereCondition GROUP BY $selectColumns HAVING COUNT($duplicateCheckColumn) > 1"""

      val duplicateCount = sparkSession.sql(s"""$duplicateCountQuery""").count

      if (duplicateCount > 0) {
        val logDate = dateFormat.format(Calendar.getInstance.getTime)

        val timeStamp = timeStampFormat.format(Calendar.getInstance.getTime)

        println(s"""DuplicateCheck:     $timeStamp, $environment, $database, $tableType,$viewMode,$businessLine,$viewType,$selectColumns,$duplicateCheckColumn,$duplicateCount,$logDate,$dateColumn""")
        sparkSession.sql(s""" INSERT INTO $outputDatabase.$duplicateCheckOutputTable PARTITION (partitionDate) SELECT '$timeStamp', '$environment', '$database', '$tableType', '$viewMode', '$businessLine', '$viewType', '$selectColumns', '$duplicateCheckColumn', '$duplicateCount','$logDate', '$dateColumn' """)

        //println(s"""     $timeStamp,$duplicateCountQuery,DuplicateCheck,$dateColumn,$logDate""")
        sparkSession.sql(s""" INSERT INTO $outputDatabase.$queryLogTable VALUES ('$timeStamp', "$duplicateCountQuery", 'DuplicateCheck', '$dateColumn', '$logDate')""")

      }
      val executionEndTime = Calendar.getInstance.getTimeInMillis
      println("DuplicateCheck: Time taken in seconds " + (executionEndTime - executionStartTime) / 1000 + " sec")
    }
  }

  def main(args: Array[String]) {

    val timeFormat = new SimpleDateFormat("yyyyMMddhhmmss")
    val jobStartTime = timeFormat.format(Calendar.getInstance.getTime)

    val sparkSession =
      SparkSession
        .builder()
        .enableHiveSupport()
        .appName(this.getClass.getName + jobStartTime)
        .enableHiveSupport()
        .config("hive.exec.dynamic.partition", "true")
        .config("hive.exec.dynamic.partition.mode", "nonstrict")
        .getOrCreate()

    // Set spark code Log Level
    val sc = sparkSession.sparkContext
    sc.setLogLevel(sparkLogLevel)

    // Arguments
    val automateFileLocation = args(0)
    val startDate = args(1).toInt
    val endDate = args(2).toInt

    println(s"DuplicateCheck: Starting Duplicate Check For StartDate $startDate EndDate $endDate")

    // Loading the config File
    val automateFile = sparkSession.read.option("delimiter", confFileDelimiter).option("header",confFileHeader).csv(s"$automateFileLocation")

    // Variables for capturing application progress status
    val totalLineCount = automateFile.distinct.count
    var lineNumber = 0

    //Parsing Config File
    automateFile.collect.map { cols =>

      lineNumber+=1
      val statusPercentage = ((lineNumber * 100.00)/totalLineCount).formatted("%1.2f")

      val environment = cols(0).toString
      val database = cols(1).toString
      val tableType = cols(2).toString
      val duplicateCheckColumn = cols(3).toString
      val selectColumns = cols(4).toString
      val businessLine = cols(5).toString
      val viewMode = cols(6)
      val viewType = cols(7)

      duplicateCheckExecution(environment, database, tableType, duplicateCheckColumn, selectColumns, startDate, endDate, businessLine, viewMode, viewType, sparkSession, sc)

      println(s"DuplicateCheck: ---------- Application progress status: $statusPercentage % ----------")

    }

    sparkSession.close
  }
}