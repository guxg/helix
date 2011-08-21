package scalastack.db

import org.scalaquery.session._
import org.scalaquery.session.Database.threadLocalSession
import org.scalaquery.ql._
import org.scalaquery.ql.TypeMapper._
import org.scalaquery.ql.extended.MySQLDriver.Implicit._
import org.scalaquery.ql.extended.{ExtendedTable => Table}
import net.liftweb.util.{Props,Helpers}
import java.sql.Timestamp
import scalastack.domain._

object Storage extends Connection with Queries with Tables {
  def listNewestAdded = db withSession {
    for(p <- ListTopFiveProjects.list) 
      yield Project(p._1,p._2,Some(p._3), 
        permalink = Some(p._4),
        addedAt = Some(p._5),
        contributor = Some(Contributor(p._6,p._7,Some(p._8)))
      )
  }
  
  def findContributorByLogin(login: String): Option[Int] = db withSession {
    for(c <- FindContributorByLogin(login).firstOption) yield c
  }
  
  def findProjectByPermalink(link: String) = db withSession {
    for(p <- FindProjectByPermaLink(link).firstOption) 
      yield Project(p._1,p._3)
  }
  
  def createContributor(c: Contributor){
    db withSession {
      Contributors.insert(0, c.name, c.login, c.avatar.getOrElse(null), c.style)
    }
  }
  
  def setup_!() = db withSession {
    (ScalaVersions.ddl ++ Projects.ddl ++ Contributors.ddl) create
  }
}

trait Connection {
  lazy val db = Database.forURL(
    Props.get("db.url").openOr("jdbc:h2:database/temp"),
    driver = Props.get("db.driver").openOr("org.h2.Driver"),
    user = Props.get("db.user").openOr(""),
    password = Props.get("db.password").openOr(""))
}

trait Queries { _: Tables with Connection =>
   lazy val ListScalaVersions = for {
     v <- ScalaVersions
   } yield v.*
   
   lazy val ListTopFiveProjects = for {
     p <- Projects
     c <- p.contributor
     _ <- Query orderBy p.addedAt.desc
     _ <- Query take 5
   } yield p.id ~ p.name ~ p.description ~ p.permalink ~ p.addedAt ~ c.name ~ c.login ~ c.avatarUrl
   
   lazy val FindProjectByPermaLink = for {
     l <- Parameters[String]
     p <- Projects if p.permalink === l
   } yield p.*
   
   lazy val FindContributorByLogin = for {
     login <- Parameters[String]
     c <- Contributors if c.login === login
   } yield c.id
}

trait Tables {
  implicit object ReleaseTypeMapper extends MappedTypeMapper[ReleaseType, String] with BaseTypeMapper[ReleaseType] {
     def map(e: ReleaseType) = e match {
       case Final => "final"
       case RC => "rc"
       case Snapshot => "snapshot"
     }
     def comap(s: String) = s match {
       case "final" => Final
       case "rc" => RC
       case "snapshot" => Snapshot 
     }
     override def sqlTypeName = Some("enum('final', 'rc', 'snapshot')")
  }
  
  lazy val ScalaVersions = new Table[(Int,String,ReleaseType)]("scala_versions"){
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def version = column[String]("version", O.NotNull)
    def releaseType = column[ReleaseType]("release_type")
    def * = id ~ version ~ releaseType
  }
  
  lazy val Projects = new Table[(Int, Int, String, String, String, String, String, String, String, Timestamp)]("projects"){
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def contributorId = column[Int]("contributor_id")
    def name = column[String]("name", O.NotNull)
    def description = column[String]("description")
    def permalink = column[String]("permalink")
    def groupId = column[String]("group_id")
    def artifactId = column[String]("artifact_id")
    def usagePhase = column[String]("usage_phase")
    def sourceURL = column[String]("source_url")
    def addedAt = column[Timestamp]("added_at", O.Default(new Timestamp(Helpers.millis)))
    // fks
    def contributor = foreignKey("contributor_id_fk", contributorId, Contributors)(_.id)
    def * = id ~ contributorId ~ name ~ description ~ permalink ~ groupId ~ artifactId ~ usagePhase ~ sourceURL ~ addedAt
  }
  
  lazy val ProjectVersions = new Table[(Int,Int,Int,String)]("project_versions"){
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def projectId = column[Int]("project_id")
    def scalaVersionId = column[Int]("scala_version_id")
    def version = column[String]("version", O.NotNull)
    // fks
    def scalaVersion = foreignKey("scala_version_fk", scalaVersionId, ScalaVersions)(_.id)
    def project = foreignKey("project_fk", projectId, Projects)(_.id)
    // projection 
    def * = id ~ projectId ~ scalaVersionId ~ version
  }
  
  lazy val Contributors = new Table[(Int,String,String,String,String)]("contributors"){
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def login = column[String]("login", O.NotNull)
    def avatarUrl = column[String]("avatar_url")
    def style = column[String]("style")
    def * = id ~ name ~ login ~ avatarUrl ~ style
  }
  
}
