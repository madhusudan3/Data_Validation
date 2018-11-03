/*
Architecture flow.

data connectivity for logs
latency 3,7 days, rerun process
data Checks at each step.
quick checks vs detailed checks.
Alert or Notifications.
Tables List at each level

todo config files and excel sheet Live to LIVE

 */

package com.att.utility

import java.io._
import java.text.SimpleDateFormat
import java.util.logging.{ConsoleHandler, _}
import java.util.{Calendar, Date}

import com.typesafe.config.ConfigFactory
import org.apache.commons.lang.time.DateUtils
import org.apache.spark.sql.SparkSession

import scala.collection.mutable.ArrayBuffer
import scala.util.Try


trait BaseUtility {

  val conf = ConfigFactory.parseFile(new File("dataValidation.conf")).withFallback(ConfigFactory.load())

  //  Parameters common to the DataValidation code
  val sparkLogLevel = Try(conf.getString("dataValidation.common.sparkLogLevel")).getOrElse("WARN")
  val numDays: Int = conf.getInt("dataValidation.common.maxNumExecutionDays") // 31
  val confFileDelimiter: String = conf.getString("dataValidation.common.confFileDelimiter")
  val confFileHeader: Boolean = conf.getBoolean("dataValidation.common.confFileHeader")
  val outputDatabase: String = conf.getString("dataValidation.common.outputDatabase") // "validation_framework"
  val queryLogTable: String = conf.getString("dataValidation.common.queryLogTable") // "dataValidation_QueryDetails"

  // ColumnQuality parameters
  val columnQualityOutputTable: String = conf.getString("dataValidation.columnQuality.columnQualityOutputTable")
  val columnQualityExecutionPriority: String = conf.getString("dataValidation.columnQuality.columnsExecutionPriority")

  // TotalConsistency parameters
  val totalConsistencyOutputTable: String = conf.getString("dataValidation.totalConsistency.totalConsistencyOutputTable")

  // ColumnConsistency parameters
  val columnConsistencyExecutionPriority: String = conf.getString("dataValidation.columnConsistency.columnsExecutionPriority")
  val columnConsistencyOutputTable: String = conf.getString("dataValidation.columnConsistency.columnConsistencyOutputTable")

  // DuplicateCheck parameters
  val duplicateCheckOutputTable: String = conf.getString("dataValidation.duplicateCheck.duplicateCheckOutputTable")

  val referencePath: String = conf.getString("dataValidation.schemaCheck.referencePath")
  val checkPath: String = conf.getString("dataValidation.schemaCheck.checkPath")
  val depth: Int = conf.getInt("dataValidation.schemaCheck.depth")
  val basePath: String = conf.getString("dataValidation.schemaCheck.basePath")
  val schemaCheckTable: String = conf.getString("dataValidation.schemaCheck.schemaCheckTable")
  val filter: String = conf.getString("dataValidation.schemaCheck.filter")

  protected lazy val log = Logger.getLogger(this.getClass.getName)
  //log.addHandler(new StreamHandler(System.out, new SimpleFormatter()))
  val simpleFormatter = new SimpleFormatter
  val consoleHandler = new ConsoleHandler
  log.addHandler(consoleHandler)
  consoleHandler.setFormatter(simpleFormatter)
  //log.setLevel(Level.INFO)
  //val fileHandler = new FileHandler("./DataStatistics.log")
  //log.addHandler(fileHandler)
  //fileHandler.setFormatter(simpleFormatter)


  def toIntegerValue(inputString: String): Int = {
    var output = 0
    if (inputString.replaceAll("[^\\d.E-]", "").isEmpty) {
      output = 0
    }
    else {
      output = inputString.replaceAll("[^\\d.E-]", "").toInt
    }
    output
  }

  def toStringValue(inputString: String): String = {
    var output = ""
    if (inputString.replaceAll("[^\\d.E-]", "").isEmpty) {
      output = "0"
    }
    else {
      output = inputString.replaceAll("[^\\d.E-]", "").toDouble.formatted("%1.2f")
    }
    output
  }

  def priorityConditionDef(priority: String, columnsExecutionPriority: String): Array[String] = {

    val requiredPriority = columnsExecutionPriority
    val priorityMap = Map("LOWPRIORITYCOLUMN" -> 1, "MEDIUMPRIORITYCOLUMN" -> 2, "HIGHPRIORITYCOLUMN" -> 3)
    val requiredPriorityInt = priorityMap(requiredPriority)
    val priorityArray = priorityMap.filter(_._2 >= requiredPriorityInt).keys.toArray
    priorityArray
  }

  def findTableANDPartitionNames(tableType: String): (String, String) = {
    var table = ""
    var partitionField = ""

    val tableTypeMap = Map("VIEWERSHIP" -> "EV_VIEWING_HISTORY", "EV_VIEWING_HISTORY" -> "EV_VIEWING_HISTORY", "ACCOUNT" -> "EV_ACCOUNT", "EV_ACCOUNT" -> "EV_ACCOUNT", "CHANNEL" -> "EV_CHANNEL", "EV_CHANNEL" -> "EV_CHANNEL", "EV_CHANNEL_PROGRAM_SCHEDULE" -> "EV_CHANNEL_PROGRAM_SCHEDULE", "EV_CHANNEL_PACKAGE_MARKET_CHANNEL" -> "EV_CHANNEL_PACKAGE_MARKET_CHANNEL", "EV_CHANNEL_PACKAGE_MARKET" -> "EV_CHANNEL_PACKAGE_MARKET", "EV_ACCOUNT_PACKAGE_MARKET" -> "EV_ACCOUNT_PACKAGE_MARKET")
    val tablePartitionMap = Map("EV_VIEWING_HISTORY" -> "program_watch_start_dt_local", "EV_ACCOUNT" -> "data_dt", "EV_CHANNEL" -> "data_dt", "EV_CHANNEL_PROGRAM_SCHEDULE" -> "data_dt", "EV_CHANNEL_PACKAGE_MARKET_CHANNEL" -> "data_dt", "EV_CHANNEL_PACKAGE_MARKET" -> "data_dt", "EV_ACCOUNT_PACKAGE_MARKET" -> "data_dt")

    if (!tableTypeMap.keys.toArray.contains(tableType.toUpperCase)) {
      println(s"""JOB ERROR: Enter table name from ${tableTypeMap.values.toList.distinct.mkString(",")} list""")
      System.exit(1)
    } else {
      table = tableTypeMap(tableType.toUpperCase)
      partitionField = tablePartitionMap(table.toUpperCase)
    }
    (table, partitionField)
  }

  def whereCondition(table: String, businessLine: String, viewMode: Any, viewType: Any): String = {

    var extraWhereCondition = ""

    if (table == "EV_VIEWING_HISTORY") {
      val viewing_platform = viewMode.toString
      val channel_tune_type = viewType.toString
      if (businessLine.toUpperCase == "DIRECT-NOW" || businessLine.toUpperCase == "DTVNOW") {
        extraWhereCondition = s""" ((business_line = 'OTT' and source_system = 'QUICKPLAY') or (business_line = 'ATT-OTT' and source_system = 'OMNITURE' )) AND viewing_platform ='$viewing_platform' AND UPPER(channel_tune_type) ='$channel_tune_type' """
      } else {
        if (viewing_platform == "STB") {
          extraWhereCondition = s""" business_line = '$businessLine' AND viewing_platform ='$viewing_platform' AND UPPER(channel_tune_type) ='$channel_tune_type' """
        } else {
          extraWhereCondition = s""" business_line = '$businessLine' AND viewing_platform != 'STB' AND UPPER(channel_tune_type) ='$channel_tune_type' """
        }
      }
    }else if (table == "EV_ACCOUNT" | table == "EV_CHANNEL") {
      val status = viewMode.toString
      extraWhereCondition = s""" business_line= '$businessLine' AND status='$status' """
    } else if (table == "EV_CHANNEL_PROGRAM_SCHEDULE" | table == "EV_CHANNEL_PACKAGE_MARKET_CHANNEL" | table == "EV_CHANNEL_PACKAGE_MARKET" | table == "EV_ACCOUNT_PACKAGE_MARKET") {
      extraWhereCondition = s""" business_line = '$businessLine' """
    } else {
      extraWhereCondition = ""
    }
    extraWhereCondition
  }


  def datesCalculation(startDate: Int, endDate: Int): Array[String] = {

    val dateFormat = new SimpleDateFormat("yyyyMMdd")

    val endDate_plus1_dateFormat = new Date(dateFormat.parse(dateFormat.format(DateUtils.addDays(new Date(dateFormat.parse(endDate.toString).getTime()),1))).getTime())

    val datesArray = new ArrayBuffer[String]()

    val calendar = Calendar.getInstance
    calendar.setTime(new Date(dateFormat.parse(startDate.toString).getTime()))

    while ( calendar.getTime().before(endDate_plus1_dateFormat) ) {
      val date = calendar.getTime
      val date_str = dateFormat.format(date)
      datesArray += date_str
      val next_date = DateUtils.addDays(date,1)
      calendar.setTime(next_date)
    }

    datesArray.distinct.toArray
  }

  def findCategory(percentage: Double): String = {
    var category = ""
    if (Math.abs(percentage) < 5) {
      category = "lessThan5Percent"
    } else if (Math.abs(percentage) >= 5 & Math.abs(percentage) < 10) {
      category = "5to9percent"
    } else if (Math.abs(percentage) >= 10 & Math.abs(percentage) <= 15) {
      category = "10to15percent"
    } else {
      category = "GreaterThan15Percent"
    }
    category
  }

  def columnConsistencyFindDates(start_dt: Int, end_dt: Int, sparkSession: SparkSession): (String, String) = {

    // Calculate start and end dates
    val days30BeforeStartDate = sparkSession.sql(s"""SELECT date_add(from_unixtime(unix_timestamp(String($start_dt),'yyyyMMdd')), -30) as days30BeforeStartDate""").collect.mkString(",").replaceAll("[^\\d.]", "")
    val days1BeforeStartDate = sparkSession.sql(s"""SELECT date_add(from_unixtime(unix_timestamp(String($start_dt),'yyyyMMdd')), -1) as days1BeforeStartDate""").collect.mkString(",").replaceAll("[^\\d.]", "")

    val avgStartDay = days30BeforeStartDate
    val avgEndDate = days1BeforeStartDate

    (avgStartDay, avgEndDate)
  }

  def totalConsistencyFindDates(start_dt: Int, end_dt: Int, sparkSession: SparkSession): (String, String, String, String) = {

    val previousYearMonth = sparkSession.sql(s"""SELECT from_unixtime(unix_timestamp(add_months(from_unixtime(unix_timestamp(String('$start_dt'),'yyyyMMdd'),'yyyy-MM-dd'),-12),'yyyy-MM-dd'),'yyyyMMdd')""").head.getString(0)

    val days = sparkSession.sql(s"""SELECT from_unixtime(unix_timestamp(date_add(from_unixtime(unix_timestamp(String($previousYearMonth),'yyyyMMdd')), 1 - day(from_unixtime(unix_timestamp(String($previousYearMonth),'yyyyMMdd')))),'yyyy-MM-dd'),'yyyyMMdd') as previousYearMonthStartDay, from_unixtime(unix_timestamp(last_day(from_unixtime(unix_timestamp(String($previousYearMonth),'yyyyMMdd'))),'yyyy-MM-dd'),'yyyyMMdd') as previousYearMonthLastDay, from_unixtime(unix_timestamp(date_add(from_unixtime(unix_timestamp(String($start_dt),'yyyyMMdd')), -30),'yyyy-MM-dd'),'yyyyMMdd') as days30BeforeStartDate, from_unixtime(unix_timestamp(date_add(from_unixtime(unix_timestamp(String($start_dt),'yyyyMMdd')), -1),'yyyy-MM-dd'),'yyyyMMdd') as days1BeforeStartDate""")
    val previousYearMonthStartDay = days.head.getString(0)
    val previousYearMonthEndDay = days.head.getString(1)

    val days30BeforeStartDate = days.head.getString(2)
    val days1BeforeStartDate = days.head.getString(3)

    (previousYearMonthStartDay, previousYearMonthEndDay, days30BeforeStartDate, days1BeforeStartDate)
  }

}