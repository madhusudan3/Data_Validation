import sbt._
import Keys._

name := "data-engineering-datavalidation"
version := "1.0"
scalaVersion := "2.10.5"

val sparkVersion = "2.1.1"

fork := true

// Note the dependencies are provided

libraryDependencies ++= Seq(

    "org.apache.spark" %% "spark-core" % sparkVersion % "provided",

    "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",

    "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",

    "org.apache.spark" %% "spark-hive" % sparkVersion % "provided"
  )

libraryDependencies += "com.typesafe" % "config" % "1.2.1"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.349"

resolvers ++= Seq("apache-snapshots" at "http://repository.apache.org/snapshots/")

resolvers += "com.typesafe" at "https://mvnrepository.com/artifact/com.typesafe/config"
//resolvers += Resolver.file("Local", file( "/Users/gm201k/Downloads"))(Resolver.ivyStylePatterns)

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

unmanagedJars in Compile += file("lib/config-1.3.1.jar")
javaOptions ++= Seq("-Dconfig.file=resources/sample.conf")

// META-INF discarding for the FAT JAR
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "log4j.properties" => MergeStrategy.last
  case "plugin.properties" => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

lazy val spark_run = taskKey[Unit]("Builds the assembly and ships it to the Spark Cluster")
spark_run := {
  ("/full/path/to/bin/spark_submit " + assembly.value) !
}
