/*
 * Copyright (c) 2015-2017 EpiData, Inc.
*/

import SQLite.{ DB => DBLite }
import java.sql.SQLException
import cassandra.DB
import com.datastax.driver.core.exceptions.NoHostAvailableException
import com.epidata.lib.models.{ AutomatedTest, SensorMeasurement }
import play.api._
import play.api.{ Configuration, Environment }
import service.{ AppEnvironment, Configs, DataService, DataSinkService, KafkaService, ZMQProducer }
import providers.DemoProvider

import scala.concurrent.Future
import javax.inject._
import play.api.inject.{ ApplicationLifecycle, Binding, Module }
import securesocial.core.RuntimeEnvironment
import service.ZMQInit

import scala.collection.JavaConverters._

class AppModule extends Module {
  def bindings(env: Environment, conf: Configuration) = Seq(
    (bind[RuntimeEnvironment].to[AppEnvironment]).eagerly(),
    (bind[ApplicationStart].toSelf).eagerly(),
    (bind[ApplicationStop].toSelf).eagerly())
}

// `ApplicationStop` object with shut-down hook
@Singleton
class ApplicationStop @Inject() (lifecycle: ApplicationLifecycle) {
  if ((Configs.measDB == "sqlite") && (Configs.userDB == "sqlite")) {
    lifecycle.addStopHook { () =>
      Future.successful(DBLite.close)
    }
  } else if ((Configs.measDB == "cassandra") && (Configs.userDB == "cassandra")) {
    lifecycle.addStopHook { () =>
      Future.successful(DB.close)
    }
  } else {
    lifecycle.addStopHook { () =>
      Future.successful(DBLite.close)
      Future.successful(DB.close)
    }
  }
}

// `ApplicationStart` object for application start-up
@Singleton
class ApplicationStart @Inject() (env: Environment, conf: Configuration) {
  Configs.init(conf)

  // Connect to the SQLite database.
  if ((Configs.measDB == "sqlite") || (Configs.userDB == "sqlite")) {
    try {
      DBLite.connect(conf.getOptional[String]("sqlite.url").get)
    } catch {
      case e: SQLException =>
        throw new SQLException(s"Unable to connect to SQLite database: ${e}")
    }
  }

  // Connect to the Cassandra database.
  if ((Configs.measDB == "cassandra") || (Configs.userDB == "cassandra")) {
    try {
      DB.connect(
        conf.getOptional[String]("cassandra.node").get,
        conf.getOptional[Configuration]("pillar.epidata").get
          .getOptional[Configuration](env.mode.toString.toLowerCase).get
          .getOptional[String]("cassandra-keyspace-name").get,
        conf.getOptional[Configuration]("pillar.epidata").get
          .getOptional[Configuration](env.mode.toString.toLowerCase).get
          .getOptional[String]("replicationStrategy").getOrElse("SimpleStrategy"),
        conf.getOptional[Configuration]("pillar.epidata").get
          .getOptional[Configuration](env.mode.toString.toLowerCase).get
          .getOptional[Int]("replicationFactor").getOrElse(1),
        conf.getOptional[String]("cassandra.username").get,
        conf.getOptional[String]("cassandra.password").get,
        env.getFile("conf/pillar/migrations/epidata"))
    } catch {
      case e: NoHostAvailableException =>
        throw new IllegalStateException(s"Unable to connect to cassandra server: ${e}")
    }
  }

  if (conf.getOptional[String]("queue.service").get.equalsIgnoreCase("Kafka")) {
    KafkaService.init("127.0.0.1:" + conf.getOptional[Int]("queue.servers").get)
  } else if (conf.getOptional[String]("queue.service").get.equalsIgnoreCase("ZMQ")) {
    // Initiating object that will initiate 3 instances of ZMQProducer/DataSink to be used across the application
    ZMQInit.init(conf)
  } else {
    throw new IllegalStateException(s"Invalid string used to configure queue.service config: " + conf.getOptional[String]("queue.service").get + ". Expected: 'Kafka' or 'ZMQ'")
  }

  val tokens = seqAsJavaList(conf.getOptional[Seq[String]]("application.api.tokens").get)

  DataService.init(tokens)

  if (!conf.getOptional[Boolean]("application.ingestion.2ways").getOrElse(false)) {
    val kafkaConsumer = new DataSinkService("127.0.0.1:" + conf.getOptional[Int]("queue.servers").get, "data-sink-group", DataService.MeasurementTopic)
    kafkaConsumer.run()
  }

}
