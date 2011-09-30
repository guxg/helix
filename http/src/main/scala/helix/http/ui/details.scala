package helix.http.ui

import helix.domain.{Project,ScalaVersion,Version,Module}

case class ProjectDetail(groupId: String, artifactId: String){
  import helix.domain.Service._
  lazy val project: Option[Project] = 
    findProjectByGroupAndArtifact(groupId, artifactId)
}

import scala.xml.{NodeSeq,Text}

trait BuildSystem {
  protected def project: Project
  protected def usage: (String,String,String,String,String) => NodeSeq
  def name: String
  def render: NodeSeq = 
    (for {
      a <- project.artifactId
      g <- project.groupId
      s <- project.repositoryURL
      v <- project.versions.headOption
    } yield usage(project.name, g,a,v.identifier,s)) getOrElse NodeSeq.Empty
}
case class SBT10Plus(project: Project) extends BuildSystem {
  def name = "SBT 0.10+"
  def usage = (name, group, artifact, version, repo) =>
    <pre>{
      """libraryDependencies += "%s" %%%% "%s" %% "%s"
       
resolvers += "%s-repo" at "%s" """.format(group, artifact, version, name, repo)
    }</pre>
}
case class Maven(project: Project) extends BuildSystem {
  def name = "Maven"
  def usage = (name, group, artifact, version, repo) => <pre> {
"""<dependency>
  <groupId>%s</groupId>
  <artifactId>%s</artifactId>
  <version>%s</version>
</dependency>

<repository>
  <id>%s-repo</id>
  <name>%s Repo</name>
  <url>%s</url>
</repository>""".format(group, artifact, version, name, name, repo)
  } </pre>
}

import net.liftweb.common.{Box,Empty,Full}
import net.liftweb.util.NamedPF
import net.liftweb.http._
import net.liftweb.sitemap.Loc

object ProjectInformation extends Loc[ProjectDetail]{
  val name = "details"
  
  private val path = "project" :: "show" :: Nil
  
  val text = new Loc.LinkText[ProjectDetail](detail =>
    Text((for(p <- detail.project) yield p.name).getOrElse("Unknown")))
  
  val link = new Loc.Link[ProjectDetail](path, false)
  
  def params = Nil
    
  def defaultValue = Empty
  
  override val rewrite: LocRewrite = Full(NamedPF("Project Rewrite"){
    case RewriteRequest(ParsePath("projects" :: gid :: aid :: Nil,"",true,_),_,_) =>
        (RewriteResponse(path), ProjectDetail(gid,aid))
  })
  
  /** snippets **/
  import net.liftweb.util.Helpers._
  import helix.http.ui.DomainBindings._
  import helix.http.Vars.CurrentContributor
  
  override val snippets: SnippetTest = {
    case ("information", Full(pd)) => information(pd)
    case ("contributors", Full(pd)) => contributors(pd.project)
    case ("overview", Full(pd)) => overview(pd.project)
    case ("versions", Full(pd)) => versions(pd.project.map(_.versions).getOrElse(Nil))
    case ("ready_or_pending", Full(pd)) => readyOrPending(pd.project)
    case ("is_contributor", Full(pd)) => isContributor(pd.project)
    case ("usage_examples", Full(pd)) => usageExamples(pd.project)
    case ("modules", Full(pd)) => modules(pd.project.map(_.modules).getOrElse(Nil))
  }
  
  def buildTools(project: Option[Project]): List[BuildSystem] = 
    project.map(p => List(SBT10Plus(p), Maven(p))).getOrElse(Nil)
  
  def usageExamples(project: Option[Project]) = 
    "*" #> buildTools(project).map { b => 
      "tool_name" #> b.name &
      "usage" #> b.render
    }
  
  def isContributor(project: Option[Project]): NodeSeq => NodeSeq = 
    xhtml => (for {
      u <- CurrentContributor.is
      p <- project
      r <- p.contributors.find(_.login == u.login)
    } yield xhtml) getOrElse NodeSeq.Empty
  
  def contributors(project: Option[Project]): NodeSeq => NodeSeq = 
    project.map { p => 
      "li" #> p.contributors.map { c =>
        "h4 *" #> c.login &
        "p *" #> "%s commits".format(c.contributions) & 
        "img [src]" #> c.avatar
      }
    } getOrElse dontDisplayAnything
  
  def versions(versions: List[Version]) = 
    "tr" #> versions.map { version =>
      "version" #> version.identifier &
      "scalaversion" #> version.compatibility
    }
  
  def modules(modules: List[Module]) = 
    if(modules.isEmpty) "*" #> NodeSeq.Empty
    else "tr" #> modules.map { module => 
      "module_name" #> module.name &
      "module_description" #> module.description
    }
  
  def overview(project: Option[Project]): NodeSeq => NodeSeq = {
    import org.joda.time.{Period,DateTime}
    import org.joda.time.format.PeriodFormatterBuilder
    
    val formatter = new PeriodFormatterBuilder()
      .appendYears().appendSuffix(" year, ", " years, ")
      .appendMonths().appendSuffix(" month, ", " months, ")
      .appendWeeks().appendSuffix(" week, ", " weeks, ")
      .appendDays().appendSuffix(" day", " days")
      .printZeroNever()
      .toFormatter()
    
    // TODO: Refactor into a for comprehension
    project.map { p => 
      "latest_version" #> p.versions.headOption.map(_.identifier).getOrElse("unknown.") & 
      "age" #> formatter.print(
        new Period(
          new DateTime(p.createdAt), 
          new DateTime
        )) &
      "contributor_count" #> p.contributors.size
    } getOrElse dontDisplayAnything
  }
  
  def information(details: ProjectDetail) = 
    (for(project <- details.project) 
      yield project.bind) getOrElse dontDisplayAnything
  
  val dontDisplayAnything = "*" #> NodeSeq.Empty
  val suggestAddingProject = 
    "*" #> <lift:embed what="_nonexistant_project"></lift:embed>
  
  def readyOrPending(project: Option[Project]) = 
    (for(p <- project) yield {
      if(p.setupComplete) "ready ^*" #> NodeSeq.Empty
      else "pending ^*" #> NodeSeq.Empty
    }) getOrElse suggestAddingProject
}
