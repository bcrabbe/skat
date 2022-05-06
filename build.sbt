lazy val akkaVersion = "2.5.0"

enablePlugins(JavaAppPackaging)

lazy val commonSettings = Seq(
  organization := "com.bcrabbe",
  version := "0.0.1",
  scalaVersion := "2.12.15",
  semanticdbEnabled := true, // enable SemanticDB
  semanticdbVersion := scalafixSemanticdb.revision, // only required for Scala 2.x
  scalacOptions += "-Ywarn-unused-import" // required by `RemoveUnused` rule
)

lazy val deps = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalactic" %% "scalactic" % "3.2.9",
  "org.scalatest" %% "scalatest" % "3.2.9" % "test",
  "org.mockito" %% "mockito-scala-scalatest" % "1.13.6" % "test",
  "io.github.ghostbuster91.scalafix-unified" %% "unified" % "0.0.8"
)

lazy val commons = (project in file("common")).settings(commonSettings, libraryDependencies ++= deps)
lazy val client = (project in file("client")).settings(commonSettings, libraryDependencies ++= deps).dependsOn(commons)
lazy val server = (project in file("server")).settings(commonSettings, libraryDependencies ++= deps).dependsOn(commons)
