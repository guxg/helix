package helix.search

import helix.lib.Searching
import helix.domain.Project
import org.elasticsearch.node.{Node,NodeBuilder}

trait ESSearching extends Searching {
  def searching: SearchProvider
  
  class ESSearchProvider extends SearchProvider {
    import NodeBuilder.nodeBuilder
    
    private lazy val client = nodeBuilder.client(true).node.client
    
    def index(project: Project){
      import org.elasticsearch.common.xcontent.XContentFactory._
      for {
        headline <- project.headline
        description <- project.description
        group <- project.groupId
        artifact <- project.artifactId
        uid = "%s.%s".format(group, artifact)
      }{
        client.prepareIndex("helix", "project", uid)
          .setSource(jsonBuilder()
            .startObject()
            .field("name", project.name)
            .field("headline", headline)
            .field("description", description)
            .field("groupId", group)
            .field("artifactId", artifact)
            .field("contributors", project.contributors.map(_.login).mkString(" "))
            .endObject()
          ).execute().actionGet()
      }
    }
    
    import org.elasticsearch.index.query.FilterBuilders._
    import org.elasticsearch.index.query.QueryBuilders._
    import org.elasticsearch.action.search.SearchType
    import org.elasticsearch.search.SearchHit
    import scala.collection.JavaConverters._
    
    def search(term: String): List[Project] = 
      client.prepareSearch("helix")
        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
        .setQuery(termQuery("_all", term))
        .setFrom(0).setSize(60).setExplain(true)
        .execute().actionGet().hits.getHits.toList.map { hit =>
          val result = hit.getSource.asScala.map { case (k,v) => k -> v.asInstanceOf[String] }
          Project(
            name = result.get("name").getOrElse("unknown"),
            groupId = result.get("groupId"), 
            artifactId = result.get("artifactId"),
            headline = result.get("headline"), 
            description = result.get("description")
          )
        }
  }
}

import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.loader.YamlSettingsLoader

class SearchServer {
  import NodeBuilder.nodeBuilder
  import ImmutableSettings.settingsBuilder
  
  private var node: Option[Node] = None
  
  def start(){
    // if the server is not operational, start it
    if(node.isEmpty){
      node = Option(nodeBuilder.client(false).node)
      node.foreach(_.start())
      status()
    }
  }
  
  // TODO: load settings from a yaml on the classpath
  // def settings: ImmutableSettings = {
  //   new YamlSettingsLoader().load(this.getClass.getResourceAsStream())
  //   settingsBuilder.put()
  // }
  
  def stop() = 
    node.foreach(_.close())
  
  def client: Option[Client] = node.map(_.client)
  
  // not 100% what the hell this is for
  private def health: ClusterHealthStatus =
    client.map(_.admin.cluster.prepareHealth().execute.actionGet.getStatus
      ).getOrElse(ClusterHealthStatus.RED)
  
  private def isRedStatus: Boolean = 
    health eq ClusterHealthStatus.RED
  
  def status(){
    for(c <- client){
      if(isRedStatus){
        c.admin.cluster.prepareHealth()
          .setWaitForYellowStatus
          .setTimeout("30s")
          .execute().actionGet()
      }
    }
    if(isRedStatus)
      throw new RuntimeException("ES cluster health status is RED. Server is not able to start.")
  }
}