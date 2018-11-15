name := "FibreCensus"
 
version := "1.0" 
      
lazy val `fibrecensus` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
      
scalaVersion := "2.12.2"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )

val phantomVersion = "2.27.0"
libraryDependencies ++= Seq (
  "com.outworkers" %% "phantom-connectors" % phantomVersion,
  "com.outworkers" %% "phantom-dsl" % phantomVersion,
  "com.outworkers" %% "phantom-jdk8" % phantomVersion
)
val circeVersion = "0.9.3"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "com.dripower" %% "play-circe" % "2610.0"
)

unmanagedResourceDirectories in Test +=  { baseDirectory ( _ /"target/web/public/test" ).value }



      