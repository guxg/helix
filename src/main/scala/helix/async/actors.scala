package helix.async 

import akka.actor.{Actor,ActorRef}
import akka.actor.{Actor,Scheduler}, Actor._
import akka.config.Supervision._
import akka.dispatch._
import akka.routing._
import helix.domain.Project

object Manager {
  def start(){
    // touch the agents
    Agents.all
    // start the actors
    List(
      actorOf[Statistics], 
      actorOf[ProjectManager]
    ).foreach(_.start())
  }
  def stop(){
    Actor.registry.shutdownAll
    Agents.all.foreach(_.close())
  }
}

object ProjectManager {
  case class UpdateAttributes(project: Project)
}
class ProjectManager extends Actor 
    with DefaultActorPool 
    with BoundedCapacityStrategy
    with ActiveFuturesPressureCapacitor 
    with SmallestMailboxSelector 
    with BasicNoBackoffFilter {
  
  self.faultHandler = OneForOneStrategy(classOf[RuntimeException] :: Nil, 10, 10000)
  
  def receive = _route
  def lowerBound = 2
  def upperBound = 4
  def rampupRate = 0.1
  def partialFill = true
  def selectionCount = 1
  def instance = actorOf[ProjectWorker]
}
class ProjectWorker extends Actor {
  import ProjectManager._
  import helix.github.Client
  import helix.domain.Service
  
  def receive = {
    case UpdateAttributes(project) => 
      println(">>>>>>>>>>>> UPDATING")
      (for {
        unr <- project.usernameAndRepository
        repo <- Client.repositoryInformation(unr)
        created <- repo.createdAt
        contributors = Client.contributorsFor(unr)
      } yield project.copy(
        contributors = contributors,
        createdAt = created.toDate,
        setupComplete = true
      )) foreach(p => Service.updateProject(p.id, p))
  }
}

/**
listProjectsAlphabetically(limit = 50).foreach { project => 
  try {
    val score = calculateProjectActivityScore(project).toLong
    val newpro = project.copy(activityScore = score)
    updateProject(project.id, newpro)
  } catch {
    case err => println("~~~~~~~~ ERROR: " + err) // log that error!
  }
}
**/

/**
 * This actor handles pure background tasks
 * for computation and aggregation of global 
 * statistics that are used by multiple parts
 * of the application
 */
object Statistics {
  case object UpdateTotalProjectCount
  case object UpdateAverageProjectContributorCount
  case object UpdateAverageProjectWatcherCount
}
class Statistics extends Actor {
  import java.util.concurrent.TimeUnit.HOURS
  import helix.domain.Service._
  import Statistics._ 
  import Agents._
  
  self.faultHandler = OneForOneStrategy(classOf[Exception] :: Nil, 10, 100)
  
  def receive = { 
    case UpdateTotalProjectCount => 
      TotalProjectCount send findAllProjectCount
      Scheduler.scheduleOnce(self, UpdateTotalProjectCount, 6, HOURS)
    
    case UpdateAverageProjectContributorCount => 
    
    case UpdateAverageProjectWatcherCount =>
    
  }
  
  override def preStart {
    List(UpdateTotalProjectCount).foreach(self ! _)
  }
}