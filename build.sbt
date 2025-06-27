import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerExposedPorts, dockerUsername}

name := "FibreCensus"
 
version := "1.0" 
      
lazy val `fibrecensus` = (project in file("."))
  .enablePlugins(PlayScala)
    .settings(version := sys.props.getOrElse("build.number","DEV"),
      dockerExposedPorts := Seq(9000),
      dockerUsername  := sys.props.get("docker.username"),
      dockerRepository := Some("guardianmultimedia"),
      packageName in Docker := "guardianmultimedia/fibrecensus",
      packageName := "fibrecensus",
      dockerBaseImage := "amazoncorretto:8-alpine3.18-full",
      dockerAlias := docker.DockerAlias(None,Some("guardianmultimedia"),"fibrecensus",Some(sys.props.getOrElse("build.number","DEV"))),
      dockerCommands ++= Seq(
        Cmd("USER", "root"),
        Cmd("RUN", "apk", "add", "--no-cache", "bash"),
      ))

resolvers += "Akka library repository".at("https://repo.akka.io/maven")
      
scalaVersion := "2.12.2"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )

val elastic4sVersion = "6.2.12"
libraryDependencies ++= Seq (
  "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "test",
  "com.sksamuel.elastic4s" %% "elastic4s-embedded" % elastic4sVersion % "test",
)
val circeVersion = "0.9.3"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "com.dripower" %% "play-circe" % "2610.0",
  "com.nimbusds" % "nimbus-jose-jwt" % "9.37.2",
  "com.github.scopt" %% "scopt" % "4.1.0"
)

libraryDependencies += "com.typesafe.play" %% "play-mailer" % "6.0.1"
libraryDependencies += "com.typesafe.play" %% "play-mailer-guice" % "6.0.1"

unmanagedResourceDirectories in Test +=  { baseDirectory ( _ /"target/web/public/test" ).value }

enablePlugins(DockerPlugin)
