package helix.lib

import helix.domain._

trait Repositories {
  protected def repository: HelixRepository
  
  trait HelixRepository {
    /** global lists **/
    def listProjectsAlphabetically(limit: Int, offset: Int): List[Project]
    def listFiveNewestProjects: List[Project]
    def listFiveMostActiveProjects: List[Project]
    def listScalaVersions: List[ScalaVersion]
    // def listAllTags: List[Tag]
    
    /** finders **/
    def findProjectByGroupAndArtifact(group: String, artifact: String): Option[Project]
    def findAllProjectCount: Long
    def findAverageContributorCount: Double
    
    /** creator **/
    def createProject(project: Project): Option[Project]
    def createScalaVersion(version: ScalaVersion): Boolean
    
    /** updaters **/
    def updateProject[T](id: T, project: Project): Unit
    
  }
}

trait Scoring {
  protected def scoring: ScoringStrategy
  
  trait ScoringStrategy {
    def calculateProjectActivityScore(project: Project): Double
  }
}
