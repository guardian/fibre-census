import com.google.inject.AbstractModule
import java.time.Clock

import helpers.{ESClientManager, ESClientManagerImpl}
import play.api.libs.concurrent.AkkaGuiceSupport
import services.AlertsActor

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.

 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module extends AbstractModule with AkkaGuiceSupport {

  override def configure() = {
    bind(classOf[ESClientManager]).to(classOf[ESClientManagerImpl])
    bindActor[AlertsActor]("AlertsActor")
  }

}
