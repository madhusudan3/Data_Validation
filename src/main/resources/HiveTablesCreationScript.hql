
-- Column Quality table creation script for storing logs

CREATE TABLE validation_framework.ColumnQualityCheck
(
    queryid bigint,
    environment STRING,
    databaseName STRING,
    tableName STRING,
    viewMode STRING,
    businessLine STRING,
    viewType STRING,
    dataQualityColumn STRING,
    improperColumnCount bigint,
    improperColumnPercent FLOAT,
    totalColumnCount bigint,
    category STRING,
    logdate Int,
    priority String
    )
PARTITIONED BY (partitionDate Int)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '^'
STORED AS TEXTFILE
LOCATION 'hdfs://COREITKGMTNC20PROD01/sandbox/sandbox43/validation_framework/dev/ColumnQualityCheck';


-- Column Consistency Check table creation script for storing logs

CREATE TABLE validation_framework.ColumnConsistencyCheck
(
    queryid bigint,
    environment STRING,
    databaseName STRING,
    tableName STRING,
    viewMode STRING,
    businessLine STRING,
    viewType STRING,
    columnConsistencyField STRING,
    dayCount bigint,
    averageRecordCount FLOAT,
    columnConsistencyPercentDiff FLOAT,
    category STRING,
    logdate Int
    )
PARTITIONED BY (partitionDate Int)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '^'
STORED AS TEXTFILE
LOCATION 'hdfs://COREITKGMTNC20PROD01/sandbox/sandbox43/validation_framework/dev/ColumnConsistencyCheck';


-- Total Consistency Check table creation script for storing logs

CREATE TABLE validation_framework.TotalConsistencyCheck
(
    queryid bigint,
    environment STRING,
    databaseName STRING,
    tableName STRING,
    viewMode STRING,
    businessLine STRING,
    viewType STRING,
    dayCount bigint,
    averageRecordCount double,
    totalConsistencyPercentDiff double,
    category STRING,
    logDate Int
    )
PARTITIONED BY (partitionDate Int)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '^'
STORED AS TEXTFILE
LOCATION 'hdfs://COREITKGMTNC20PROD01/sandbox/sandbox43/validation_framework/dev/TotalConsistencyCheck';


-- Duplicate Check table creation script for storing logs

CREATE TABLE validation_framework.DuplicateCheck
(
    queryid bigint,
    environment STRING,
    databaseName STRING,
    tableName STRING,
    viewMode STRING,
    businessLine STRING,
    viewType STRING,
    selectColumns STRING,
    duplicateCheckColumn STRING,
    duplicateCount bigint,
    logDate Int
    )
PARTITIONED BY (partitionDate Int)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '^'
STORED AS TEXTFILE
LOCATION 'hdfs://COREITKGMTNC20PROD01/sandbox/sandbox43/validation_framework/dev/DuplicateCheck';


-- Table creation script for storing queries

CREATE TABLE validation_framework.schema_check
(
    schema_status STRING,
    location STRING
    )
PARTITIONED BY (log_time Bigint)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '^'
STORED AS TEXTFILE
LOCATION '/user/hadoop/validation_framework/schema_check';


-- Table creation script for storing queries

CREATE TABLE validation_framework.dataValidation_QueryDetails
(
    queryid bigint,
    query STRING,
    scenario STRING,
    executionDate Int,
    logDate Int
    )
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '^'
STORED AS TEXTFILE
LOCATION 'hdfs://COREITKGMTNC20PROD01/sandbox/sandbox43/validation_framework/dev/dataValidation_QueryDetails';