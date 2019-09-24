import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

// Versions
object Version {
  val scala = "2.12.10"
  val lspace = "0.6.3"
  val `ns.lspace` = "0.0.9"
}

// Libraries
object Library {
  val lspaceGraph = "eu.l-space" %% "lspace-graph" % Version.lspace
  val lspaceParseArgonaut = "eu.l-space" %% "lspace-parse-argonaut" % Version.lspace
  val lspaceServices = "eu.l-space" %% "lspace-services" % Version.lspace
  val lspaceServicesTests = "eu.l-space" %% "lspace-services" % Version.lspace % "test" classifier "tests"
  val lspaceNS = "eu.l-space" %% "lspace-ns" % Version.`ns.lspace`

  val scalaCsv = "com.github.tototoshi" %% "scala-csv" % "1.3.6"
  val scalaCsvRefined = "com.nrinaudo" %% "kantan.csv-refined" % "0.5.1"

//  val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.11.1"
//  val pureconfigGeneric = "com.github.pureconfig" %% "pureconfig-generic" % "0.11.1"

  val scalaTest =
    Def.setting("org.scalatest" %%% "scalatest" % "3.1.0-RC2" % "test")
}

object Dependencies {
  import Library._

  val nsDeps =
    Def.setting(Seq(lspaceNS, scalaTest.value))

  val apiDeps =
    Def.setting(
      Seq(lspaceServices,
          lspaceNS,
          scalaTest.value,
          "eu.l-space" %% "lspace-parse-argonaut" % Version.lspace % "test"))

//  val utilDeps = Def.setting(Seq(pureconfig, pureconfigGeneric))

  val serviceDeps =
    Def.setting(
      Seq(
        lspaceGraph,
        lspaceParseArgonaut,
        lspaceServices,
        lspaceServicesTests,
        lspaceNS,
//        pureconfig,
//        pureconfigGeneric,
        "com.twitter" %% "twitter-server" % "19.8.0",
        scalaCsv,
        scalaTest.value
      ))
}
