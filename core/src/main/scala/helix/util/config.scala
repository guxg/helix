package helix.util

object Config {
  import org.streum.configrity.Configuration
  
  private val mode = Configuration.systemProperties.get[String]("run.mode") match {
    case Some(p@"production") => p
    case _ => "development"
  }
  
  // fugly and dangerous, but working for the moment.
  lazy val Conf = Configuration.load(io.Source.fromInputStream(
    this.getClass.getResourceAsStream("/helix.%s.conf".format(mode))))
}
