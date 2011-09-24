import sbt._, Keys._
import com.github.siasia.WebPlugin._
import bees.RunCloudPlugin._

object BuildSettings {
  val buildOrganization = "eu.getintheloop"
  val buildVersion      = "0.2"
  val buildScalaVersion = "2.9.0-1"

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := buildOrganization,
    version      := buildVersion,
    scalaVersion := buildScalaVersion,
    scalacOptions += "-deprecation",
    resolvers ++= Seq(
      ".m2" at "file://"+Path.userHome+"/.m2/repository",
      "salat" at "http://repo.novus.com/snapshots/",
      "typesafe" at "http://repo.typesafe.com/typesafe/releases/"
    )
  )
  
  val httpSettings = 
    buildSettings ++ webSettings ++ deploymentSettings ++ Seq(
      libraryDependencies ++= Seq(
        "javax.servlet" % "servlet-api" % "2.5" % "provided",
        "org.eclipse.jetty" % "jetty-webapp" % "7.3.0.v20110203" % "jetty"
      )
    )
}

object Build extends Build {
  import BuildSettings._
  
  lazy val root = Project("helix", file("."),
    settings = buildSettings
  ) aggregate(core, frontend)
  
  lazy val core: Project = Project("helix-core", file("core"), 
    settings = buildSettings  ++ Seq(
      libraryDependencies ++= Seq(
        "se.scalablesolutions.akka" % "akka-actor" % "1.1.3" % "compile",
        "se.scalablesolutions.akka" % "akka-stm" % "1.1.3" % "compile",
        "org.multiverse" % "multiverse-alpha" % "0.6.2" % "compile",
        "org.streum" %% "configrity" % "0.8.0" % "compile",
        "ch.qos.logback" % "logback-classic" % "0.9.26" % "compile",
        "org.mongodb" % "mongo-java-driver" % "2.5.3" % "compile",
        "com.mongodb.casbah" %% "casbah-core" % "2.1.5.0" % "compile",
        "com.novus" %% "salat-util" % "0.0.8-SNAPSHOT" % "compile",
        "com.novus" %% "salat-core" % "0.0.8-SNAPSHOT" % "compile",
        "net.databinder" %% "dispatch-http" % "0.8.5" % "compile",
        "net.databinder" %% "dispatch-json" % "0.8.5" % "compile",
        "net.databinder" %% "dispatch-lift-json" % "0.8.5" % "compile"
      )
    )
  )
  
  lazy val http: Project = Project("helix-http", file("http"), 
    settings = httpSettings ++ Seq(
      libraryDependencies ++= Seq(
        "net.liftweb" %% "lift-textile" % "2.4-M2" % "compile",
        "net.liftweb" %% "lift-webkit" % "2.4-M2" % "compile",
        "net.liftweb" %% "lift-wizard" % "2.4-M2" % "compile"
      ),
      jettyScanDirs := Nil,
      temporaryWarPath <<= (sourceDirectory in Compile)(_ / "webapp")
    )
  ) dependsOn(core) //settings( :_*)
  
}