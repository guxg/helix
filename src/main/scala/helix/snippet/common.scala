package helix.snippet

import scala.xml.{NodeSeq,Text}
import net.liftweb._, 
  common.{Box,Empty,Full},
  util.Helpers._,
  http.{SHtml,S},
  wizard.Wizard
import helix.db.Storage._
import helix.domain._

trait CommonScreens { _: Wizard =>
  trait HelixScreen extends Screen {
    override def screenTop = Full(<h2>{screenNameAsHtml}</h2>)
    implicit def strToNodeSeq(s: String): NodeSeq = Text(s)
  }
  
  trait AddProjectVersionScreen extends HelixScreen {
    import scala.collection.mutable.{Map => MM}
    // Mutate some shit because its late and night and i 
    // cant think of an immutable way to do this with the
    // way SHtml.checkbox works within wizard screens. 
    // mmmmmm late night mutation. 
    object versions extends WizardVar[MM[ScalaVersion, Boolean]](MM.empty)
    override def screenName = "Project Versioning"

    val currentVersion = field("Current Version", "", notNull)

    val scalaVersions = new Field { 
        type ValueType = Boolean
        override def name = "Scala Compatibility"
        override implicit def manifest = buildIt[Boolean] 
        override def default = false
        override def toForm: Box[NodeSeq] = Full(
          <ul class="inputs-list">
            { listScalaVersions.map { v =>
              <li><label>{
                SHtml.checkbox(false, bool => 
                  versions.is += v -> bool) ++ 
                <span>{" " + v.toString}</span>
              }</label></li>
            }
          }</ul>
          <span class="help-block">
            <strong>Note:</strong> Only check the Scala version numbers that <br />
            <em>this</em> release is compatible with.
          </span>
        )
    }
  }
  
}