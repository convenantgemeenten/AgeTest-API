import Dependencies._
import com.typesafe.sbt.packager.docker._
import sbtcrossproject.CrossProject
// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / organization := "nl.convenantgemeenten.agetest"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / crossScalaVersions := Seq("2.12.8")
ThisBuild / developers := List(
  Developer(
    "thijsbroersen",
    "Thijs Broersen",
    "thijsbroersen@gmail.com",
    url("https://github.com/ThijsBroersen")
  )
)

lazy val settings = commonSettings

lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-language:reflectiveCalls",
  "-Ypartial-unification",
  "-deprecation",
  "-encoding",
  "utf8"
)

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions
)

ThisBuild / version := "0.0.1-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .settings(skip in publish := true)
  .aggregate(ns.jvm, ns.js, api, service)

lazy val ns: CrossProject = (crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure) in file("ns"))
  .settings(settings)
  .settings(
    name := "ns",
    libraryDependencies ++= nsDeps.value,
    libraryDependencies += "nl.convenantgemeenten" %% "convenantgemeenten-ns" % "0.0.1-SNAPSHOT"
  )
  .jvmSettings()
  .jsSettings(
    jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )

lazy val api = (project in file("api"))
  .dependsOn(ns.jvm)
  .settings(settings)
  .settings(
    name := "api",
    libraryDependencies ++= apiDeps.value
  )

lazy val service = (project in file("service"))
  .enablePlugins(DockerPlugin).enablePlugins(JDKPackagerPlugin)
  .dependsOn(api)
  .settings(settings)
  .settings(skip in publish := true)
  .settings(
    name := "service",
    libraryDependencies ++= serviceDeps.value,
    mainClass in Compile := Some("convenantgemeenten.server.AgeTestService"),
    topLevelDirectory := None, // Don't add a root folder to the archive
    dockerBaseImage := "openjdk:11-jre",
    dockerUpdateLatest := true,
//    dockerExposedPorts := Seq(8080),
    daemonUser in Docker := "librarian",
//    daemonUserUid in Docker := Some(1000),
//    daemonGroup in Docker := "librarian",
//    daemonGroupGid in Docker := Some(1000),
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN", "usermod", "-u", "1000", "librarian"),
//      ExecCmd("RUN", "groupadd", "-g", "1000", "librarian"),
//      ExecCmd("RUN", "groupmod", "-g", "1000", "librarian"),
      Cmd("USER", "librarian")
    ),
    killTimeout := 5,
    termTimeout := 10,
    dockerUsername := Some("broersen"),
    maintainer in Docker := "Thijs Broersen",
    packageName in Docker := name.value
  )

val makeSettingsYml = Def.task {
  val file     = (resourceManaged in Compile).value / "site" / "data" / "settings.yml"
  val contents = s"version: ${version.value}"
  IO.write(file, contents)
  Seq(file)
}

lazy val site = (project in file("site"))
  .enablePlugins(MicrositesPlugin)
  .dependsOn(service % "compile->compile;compile->test")
  .settings(name := "site")
  .settings(skip in publish := true)
  .settings(
    resourceGenerators in Compile += makeSettingsYml.taskValue,
    makeMicrosite := (makeMicrosite dependsOn makeSettingsYml).value,
    scalacOptions in Tut := compilerOptions
  )
  .settings(
    micrositeName := "Agetest-API",
    micrositeDescription := "Services for performing assertions on someones age.",
    micrositeDataDirectory := (resourceManaged in Compile).value / "site" / "data",
    micrositeBaseUrl := "/Agetest-API",
    micrositeAuthor := "Thijs Broersen",
    micrositeHomepage := "https://thijsbroersen.github.io/Agetest-API",
    micrositeGithubOwner := "ThijsBroersen",
    micrositeGithubRepo := "Agetest-API",
    micrositeGitterChannel := true,
    micrositeFooterText := Some(
      "")
  )
