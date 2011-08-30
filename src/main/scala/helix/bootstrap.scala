package bootstrap.liftweb

import net.liftweb.common.{Box,Full,Empty}
import net.liftweb.util.NamedPF
import net.liftweb.http._
import net.liftweb.sitemap._

import helix.github.{Client => Github}
// import helix.github.GithubClient.AccessToken
import helix.db.Storage

class Boot {
  def boot {
    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))
    
    LiftRules.dispatch.append(helix.github.OAuth)
    
    LiftRules.loggedInTest = Full(() => Github.isAuthenticated)
    
    LiftRules.htmlProperties.default.set((r: Req) =>
      new Html5Properties(r.userAgent))
    
    LiftRules.uriNotFound.prepend(NamedPF("404handler"){
      case (req,failure) => NotFoundAsTemplate(ParsePath(List("404"),"html",false,false))
    })
    
    LiftRules.snippetDispatch.append {
      case "project_wizard" => helix.snippet.ProjectWizard
      case "recently_added_projects" => helix.snippet.RecentlyAddedProject
      case "contributor_info" => helix.snippet.CurrentContributorInfo
    }
    
    import net.liftweb.sitemap.Loc.{Unless,If}
    import helix.sitemap.ProjectInformation
    
    def RequiresLogin(to: String) =If(
      () => Github.isAuthenticated, 
      () => RedirectWithState("/oauth/login", 
          RedirectState(() => Github.LoginRedirect.set(Full(to)))))
    
    LiftRules.setSiteMap(SiteMap(
      Menu("Home") / "index",
      Menu("Error") / "error",
      Menu("Tags: List") / "tags",
      Menu("Projects: List") / "projects",
      Menu("Projects: Add") / "project" / "add" >> RequiresLogin("/project/add"),
      Menu(ProjectInformation)
    ))
  }
}


import javax.servlet._ 
import javax.servlet.http._ 
import net.liftweb.http.LiftFilter 

class CloudBeesLiftFilter extends LiftFilter { 
  private def run_mode_set_? = (null != System.getProperty("run.mode"))
  private def run_mode_from(config: FilterConfig) = Option(config.getServletContext.getInitParameter("run.mode"))
  override def init(config: FilterConfig) { 
    if (!run_mode_set_?) {
      val mode = run_mode_from(config) getOrElse "development"
      System.setProperty("run.mode", mode)
    }
    super.init(config) 
  }
}
