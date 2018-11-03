/*

For a specified execution day, total consistency code calculates the averages for
two months (last 30 days and same month of previous year) from the previously computed
Total consistency result and compares against the specified day.

Example Run Command:

KM:
nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.consistency.totalConsistency.TotalConsistencyCheck \
--conf spark.shuffle.service.enabled=true \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.dynamicAllocation.minExecutors=30 \
--conf spark.dynamicAllocation.maxExecutors=60 \
--conf "spark.sql.shuffle.partitions=500" \
--conf "spark.sql.autoBroadCastJoinThreshold=250mb" \
--conf "spark.yarn.driver.memoryOverhead=3200" \
--conf "spark.yarn.executor.memoryOverhead=2400" \
--executor-memory 32G \
--num-executors 50 \
--driver-memory 4g \
--executor-cores 4 \
--conf "spark.driver.extraJavaOptions=-Dconfig.file=dataValidation.conf" \
--files /usr/hdp/current/spark-client/conf/hive-site.xml,/home/gm201k/dataValidation.conf#dataValidation.conf \
~/data-engineering-datavalidation-assembly-1.0.jar "$configpath/TotalConsistencyCheckAutomationFile.txt" "20180101" "20180131" > ~/TotalConsistency201801.log &

AWS:
nohup spark-submit \
--master yarn \
--deploy-mode cluster \
--class com.att.consistency.totalConsistency.TotalConsistencyCheck \
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
/home/gm201k@macdev.att.com/data-engineering-datavalidation-assembly-1.0.jar "s3://dv-mac-attdev-5943-us-east-1/dv-gm201k/dataValidationConfigFiles/TotalConsistencyCheckAutomationFile.txt" "20180801" "20180831" > /home/gm201k@macdev.att.com/TotalConsistency20180801_20180831_all.log &

*/


package com.att.consistency.totalConsistency

import java.text.SimpleDateFormat
import java.util.Calendar

import com.att.consistency.columnConsistency.ColumnConsistencyCheck.{columnConsistencyOutputTable, outputDatabase}
import com.att.utility.BaseUtility
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession


object TotalConsistencyCheck extends BaseUtility {

  def totalConsistencyCheckExecution(environment: String, database: String, tableType: String, startDate: Int, endDate: Int, businessLine: String, viewMode: Any, viewType: Any, sparkSession: SparkSession, sc: SparkContext): Unit = {

    val (table, partitionField) = findTableANDPartitionNames(tableType)

    val datesArray = datesCalculation(startDate, endDate)

    val extraWhereCondition = whereCondition(table.toUpperCase, businessLine, viewMode, viewType)

    val dateFormat = new SimpleDateFormat("yyyyMMdd")
    val timeStampFormat = new SimpleDateFormat("yyyyMMddhhmmssSSS")
    val logDate = dateFormat.format(Calendar.getInstance.getTime)

    val executionStartTime = Calendar.getInstance.getTimeInMillis

    val (previousYearMonthStartDay, previousYearMonthEndDay, days30BeforeStartDate, days1BeforeStartDate) = totalConsistencyFindDates(startDate, endDate, sparkSession)

    println(s"""TotalConsistencyCheck: Calculating average count for table $table $extraWhereCondition during $previousYearMonthStartDay, $previousYearMonthEndDay AND $days30BeforeStartDate, $days1BeforeStartDate """)

    val averageRecordCount = Option(sparkSession.sql(s"""SELECT AVG(daycount) FROM (SELECT daycount, ROW_NUMBER() OVER (PARTITION BY environment, databasename, tablename, viewmode, businessline, viewtype ORDER BY logdate DESC) as rownum FROM $outputDatabase.$columnConsistencyOutputTable where environment = '$environment' AND tablename = '$table' AND viewmode = '$viewMode' AND businessline = '$businessLine' AND viewtype ='$viewType' AND ((partitiondate between $previousYearMonthStartDay AND $previousYearMonthEndDay) OR (partitiondate between $days30BeforeStartDate AND $days1BeforeStartDate))) sq1 where rownum = 1""").head.get(0)).getOrElse(0.0).toString.toDouble

    datesArray.distinct.map { dateColumn =>

      val totalConsistencyCheckQuery = s"""SELECT $partitionField FROM $database.$table WHERE $partitionField = $dateColumn AND $extraWhereCondition"""

      val dayCount = sparkSession.sql(s"""$totalConsistencyCheckQuery""").count

      val totalConsistencyPercentDiff = (((dayCount.toDouble - averageRecordCount) / averageRecordCount) * 100.00).toInt

      println(s"""TotalConsistencyCheck: AverageRecordCount $averageRecordCount, DayCount $dayCount ${dayCount.toDouble}, TotalConsistencyPercentDiff $totalConsistencyPercentDiff""")

      val category = findCategory(totalConsistencyPercentDiff)

      val executionEndTime = Calendar.getInstance.getTimeInMillis
      val executionTime = (executionEndTime - executionStartTime) / 1000

      val timeStamp = timeStampFormat.format(Calendar.getInstance.getTime)

      println(s"""TotalConsistencyCheck:     $timeStamp, $environment, $database, $tableType,$viewMode,$businessLine,$viewType,$dayCount,$averageRecordCount,$totalConsistencyPercentDiff,$category,$logDate,$dateColumn""")
      sparkSession.sql(s""" INSERT INTO $outputDatabase.$totalConsistencyOutputTable PARTITION (partitionDate) SELECT '$timeStamp', '$environment', '$database', '$tableType', '$viewMode', '$businessLine', '$viewType', '$dayCount', '$averageRecordCount', '$totalConsistencyPercentDiff', '$category', '$logDate', '$dateColumn' """)

      //println(s"""     $timeStamp,$totalConsistencyCheckQuery,TotalConsistencyCheck,$dateColumn,$logDate""")
      sparkSession.sql(s""" INSERT INTO $outputDatabase.$queryLogTable VALUES ('$timeStamp', "$totalConsistencyCheckQuery", 'TotalConsistencyCheck', '$dateColumn', '$logDate')""")

      println(s"TotalConsistencyCheck: Time taken in seconds $executionTime sec")


    }
  }


  def main(args: Array[String]) {

    val timeFormat = new SimpleDateFormat("yyyyMMddhhmmss")
    val jobStartTime = timeFormat.format(Calendar.getInstance.getTime)

    val sparkSession = SparkSession
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

    println(s"TotalConsistencyCheck: Starting Total Consistency Check For StartDate $startDate EndDate $endDate")

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
      val businessLine = cols(3).toString
      val viewMode = cols(4)
      val viewType = cols(5)

      totalConsistencyCheckExecution(environment, database, tableType, startDate, endDate, businessLine, viewMode, viewType, sparkSession, sc)

      println(s"TotalConsistencyCheck: ---------- Application progress status: $statusPercentage % ----------")

    }
    sparkSession.close
  }
}
