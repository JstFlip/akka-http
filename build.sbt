name := "akka-http"

ThisBuild / name := "akka-http"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.13.14"

val akkaVersion = "2.6.20"
val akkaHttpVersion = "10.2.10"
val scalaTestVersion = "3.2.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion,
  "com.pauldijou" %% "jwt-spray-json" % "5.0.0"
)
