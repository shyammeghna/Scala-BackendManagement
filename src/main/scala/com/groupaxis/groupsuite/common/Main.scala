package com.groupaxis.groupsuite.common

import akka.actor.ActorSystem
import com.groupaxis.groupsuite.common.interfaces.http.HttpCommonService
import com.groupaxis.groupsuite.persistence.datastore.jdbc.Database
import com.typesafe.config.{Config, ConfigFactory}

//import akka.actor.ActorPath
//import akka.actor.ActorPath

object AuthenticationApp {
  private val authenticationClusterName = "groupsuite-routing"

  def main(args: Array[String]): Unit = {
    //for (jvmArg(name, value) <- args) System.setProperty(name, value)

    // load worker.conf
    val conf: Config = ConfigFactory.parseString("akka.remote.netty.tcp.port=0").
      withFallback(ConfigFactory.load("worker"))
    val database = new Database(slick.driver.PostgresDriver, conf.getString("database.url"), conf.getString("database.user"), conf.getString("database.password"))

    if (args.isEmpty) {
      startHttpService(database, 0)
    }
  }


  def startHttpService(database: Database, port: Int): Unit = {
    val conf = ConfigFactory.load()
    val system = ActorSystem(authenticationClusterName, conf)


    system.actorOf(HttpCommonService.props(conf, database), HttpCommonService.Name)

  }
}
