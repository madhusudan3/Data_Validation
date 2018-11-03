/*

For a specified day, Column consistency code calculates the average
of distinct column count for last 30 days and then compares it to the distinct column count of a specified Day.

Example Run Command:

KM:
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
--files /usr/hdp/current/spark-client/conf/hive-site.xml,/home/gm201k/dataValidation.conf \
./data-engineering-datavalidation-assembly-1.0.jar "$configpath/ColumnQualityConfigFiles/" "20180101" "20180101" > ~/columnConsistency_20180101.log &

AWS:
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
/home/gm201k@macdev.att.com/data-engineering-datavalidation-assembly-1.0.jar "s3://dv-mac-attdev-5943-us-east-1/dv-gm201k/dataValidationConfigFiles/ColumnQualityConfigFiles/" "20180831" "20180831" > /home/gm201k@macdev.att.com/columnConsistency_20180831.log &

*/


package com.att.consistency.columnConsistency

import java.text.SimpleDateFormat
import java.util.Calendar

import com.att.utility.BaseUtility
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession

object ColumnConsistencyCheck extends BaseUtility {

  def columnConsistencyCheckExecution(environment: String, database: String, tableType: String, columnConsistencyField: String, startDate: Int, endDate: Int, businessLine: String, viewMode: Any, viewType: Any, sparkSession: SparkSession, sc: SparkContext) {

    val dateFormat = new SimpleDateFormat("yyyyMMdd")
    val timeStampFormat = new SimpleDateFormat("yyyyMMddhhmmssSSS")

    val (table, partitionField) = findTableANDPartitionNames(tableType)

    val datesArray = datesCalculation(startDate, endDate)

    val extraWhereCondition = whereCondition(table.toUpperCase, businessLine, viewMode, viewType)

    val executionStartTime = Calendar.getInstance.getTimeInMillis

    val (avgStartDay, avgEndDate) = columnConsistencyFindDates(startDate, endDate, sparkSession)

    println(s"""ColumnConsistencyCheck: Calculating average count for $columnConsistencyField column in table $table $extraWhereCondition during startDate: $avgStartDay end_dt: $avgEndDate""")

    val averageRecordCount = Option(sparkSession.sql(s"""SELECT AVG(daycount) FROM (SELECT daycount, ROW_NUMBER() OVER (PARTITION BY environment, databasename, tablename, viewmode, businessline, viewtype, columnconsistencyfield ORDER BY logdate DESC) as rownum FROM $outputDatabase.$columnConsistencyOutputTable where environment = '$environment' AND tablename = '$table' AND viewmode = '$viewMode' AND businessline = '$businessLine' AND viewtype ='$viewType' AND columnconsistencyfield = '$columnConsistencyField' AND partitiondate between $avgStartDay AND $avgEndDate) sq1 where rownum = 1""").head.get(0)).getOrElse(0.0).toString.toDouble

    datesArray.distinct.map { dateColumn =>

      println(s"ColumnConsistencyCheck: Column Consistency Check For the field $columnConsistencyField table $table $partitionField $dateColumn AND $extraWhereCondition")

      val columnConsistencyCheckQuery = s"""SELECT distinct $columnConsistencyField FROM $database.$table WHERE $partitionField = $dateColumn AND $extraWhereCondition """

      val dayCount = sparkSession.sql(s"""$columnConsistencyCheckQuery""").count

      val executionEndTime = Calendar.getInstance.getTimeInMillis
      val executionTime = (executionEndTime - executionStartTime) / 1000

      val partitionColumn = dateColumn
      val columnConsistencyPercentDiff = (((dayCount.toDouble - averageRecordCount.toDouble) / averageRecordCount.toDouble) * 100.00).toInt
      val category = findCategory(columnConsistencyPercentDiff)
      val logDate = dateFormat.format(Calendar.getInstance.getTime)

      val timeStamp = timeStampFormat.format(Calendar.getInstance.getTime)

      println(s"""ColumnConsistencyCheck:     $timeStamp, $environment, $database, $tableType,$viewMode,$businessLine,$viewType,$columnConsistencyField,$dayCount,$averageRecordCount,$columnConsistencyPercentDiff,$category,$logDate,$partitionColumn """)
      sparkSession.sql(s""" INSERT INTO $outputDatabase.$columnConsistencyOutputTable PARTITION (partitionDate) SELECT '$timeStamp', '$environment', '$database', '$tableType', '$viewMode', '$businessLine', '$viewType', '$columnConsistencyField', '$dayCount', '$averageRecordCount', '$columnConsistencyPercentDiff', '$category', '$logDate', '$partitionColumn' """)

      //println(s"""     $timeStamp,$columnConsistencyCheckQuery,ColumnConsistencyCheck,$dateColumn,$logDate""")
      sparkSession.sql(s""" INSERT INTO $outputDatabase.$queryLogTable VALUES ('$timeStamp', "$columnConsistencyCheckQuery", 'ColumnConsistencyCheck', '$dateColumn','$logDate')""")

      println(s"ColumnConsistencyCheck: Time taken in seconds $executionTime sec")

    }
  }


  def main(args: Array[String]) {

    val timeFormat = new SimpleDateFormat("yyyyMMddhhmmss")
    val jobStartTime = timeFormat.format(Calendar.getInstance.getTime)

    val spark = SparkSession
      .builder()
      .enableHiveSupport()
      .appName(this.getClass.getName + jobStartTime)
      .enableHiveSupport()
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .getOrCreate()

    // Set Spark code Log Level
    val sc = spark.sparkContext
    sc.setLogLevel(sparkLogLevel)

    // Arguments
    val automateFileLocation = args(0)
    val startDate = args(1).toInt
    val endDate = args(2).toInt

    println(s"TotalConsistencyCheck: Starting Column Consistency Check For StartDate $startDate EndDate $endDate")

    // Loading the config File
    val automateFile = spark.read.option("delimiter", confFileDelimiter).option("header",confFileHeader).csv(s"$automateFileLocation")

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
      val businessLine = cols(3).toString
      val viewMode = cols(4)
      val viewType = cols(5)
      val columnConsistencyField = cols(6).toString
      val priority = Option(cols(7)).getOrElse("lowPriorityColumn")

      val priorityArray = priorityConditionDef(priority.toString.toUpperCase,columnConsistencyExecutionPriority)

      if (priorityArray.contains(priority.toString.toUpperCase) ) {

        columnConsistencyCheckExecution(environment, database, tableType, columnConsistencyField, startDate, endDate, businessLine, viewMode, viewType, spark, sc)

      }
      println(s"ColumnConsistencyCheck: ---------- Application progress status: $statusPercentage % ----------")
    }
    spark.close

  }
}