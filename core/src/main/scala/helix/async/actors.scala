package helix.async 

import java.util.concurrent.TimeUnit.{HOURS,MINUTES}
import akka.actor.{Actor,ActorRef}
import akka.actor.{Actor,Scheduler,PoisonPill}, Actor._
import akka.config.Supervision._
import akka.dispatch._
import akka.routing._
import helix.domain.Project

object Manager {
  private def hasNetworkConnection: Boolean = 
    try {
      java.net.InetAddress.getAllByName("api.github.com")
      true
    } catch {
      case e => false
    }
  
  private lazy val actors = 
    List(actorOf[ProjectManager], actorOf[ScheduledTask])
  
  def start(){
    // touch the agents
    Agents.all
    // start the actors
    if(hasNetworkConnection)
      actors.foreach(_.start())
  }
  def stop(){
    Agents.all.foreach(_.close())
    actors.foreach(_ ! PoisonPill)
    // Actor.registry.shutdownAll
  }
}

object ProjectManager {
  case class UpdateAttributes(project: Project)
  case class UpdateSearchIndex(withProject: Project)
}
class ProjectManager extends Actor 
    with DefaultActorPool 
    with BoundedCapacityStrategy
    with ActiveFuturesPressureCapacitor 
    with SmallestMailboxSelector 
    with BasicNoBackoffFilter {
  
  self.faultHandler = OneForOneStrategy(classOf[RuntimeException] :: Nil, 10, 10000)
  
  // akka pool implementation details
  def receive = _route
  def lowerBound = 2
  def upperBound = 4
  def rampupRate = 0.1
  def partialFill = true
  def selectionCount = 1
  def instance = actorOf(new ProjectWorker(self))
}
class ProjectWorker(owner: ActorRef) extends Actor {
  import ProjectManager._
  import helix.domain.Service
  import org.joda.time.DateTime
  
  def receive = {
    // case UpdateSearchIndex(project) => 
    
    case msg@UpdateAttributes(project) => {
      (for {
        unr <- project.usernameAndRepository
        repo <- Service.github.repositoryInformation(unr)
        created <- repo.createdAt
        contributors = Service.github.contributorsFor(unr)
      } yield project.copy(
        contributors = contributors,
        contributorCount = contributors.size,
        watcherCount = repo.watchers.toLong,
        forkCount = repo.forks.toLong,
        createdAt = created.toDate,
        setupComplete = true,
        updatedAt = new DateTime().getMillis
      )) map(_.copy(activityScore = Service.calculateProjectAggregateScore(project)
      )) foreach { p =>
        Service.updateProject(p.id, p)
        // send to solr
        // self ! UpdateSearchIndex(p)
      }
    }
  }
}

/**
 * This actor handles pure background tasks
 * for computation and aggregation of global 
 * statistics that are used by multiple parts
 * of the application
 */
object ScheduledTask {
  case object UpdateTotalProjectCount
  case object UpdateAverageProjectForkCount
  case object UpdateAverageProjectWatcherCount
  case object UpdateStaleProjects
}
class ScheduledTask extends Actor {
  import helix.util.Config
  import helix.domain.Service._
  import ScheduledTask._ 
  import Agents._
  
  self.faultHandler = OneForOneStrategy(classOf[Exception] :: Nil, 10, 100)
  
  val timeMetric: java.util.concurrent.TimeUnit = 
    if(Config.isProduction) HOURS else MINUTES
  
  def receive = { 
    case msg@UpdateTotalProjectCount => 
      TotalProjectCount send findAllProjectCount
      Scheduler.scheduleOnce(self, msg, 3, timeMetric)
      
    case msg@UpdateAverageProjectWatcherCount =>
      AverageProjectWatcherCount send findAverageWatcherCount
      Scheduler.scheduleOnce(self, msg, 3, timeMetric)
    
    case msg@UpdateAverageProjectForkCount =>
      AverageProjectForkCount send findAverageForkCount
      Scheduler.scheduleOnce(self, msg, 3, timeMetric)
    
    case msg@UpdateStaleProjects => {
      for(actor <- registry.actorFor[ProjectManager]){
        findStaleProjects.foreach(actor ! ProjectManager.UpdateAttributes(_))
      }
      Scheduler.scheduleOnce(self, msg, 3, timeMetric)
    }
  }
  
  override def preStart {
    List(
      UpdateTotalProjectCount, 
      UpdateAverageProjectWatcherCount,
      UpdateAverageProjectForkCount,
      UpdateStaleProjects
    ).foreach(self ! _)
  }
}