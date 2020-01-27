package com.groupaxis.groupsuite.routing.application.services

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.groupaxis.groupsuite.routing.infrastructor.es.ESRuleWriteRepository
import com.groupaxis.groupsuite.routing.infrastructor.jdbc.JdbcRuleWriteRepository
import com.groupaxis.groupsuite.routing.write.domain.model.routing.keywordDefinition.KeywordMessages.FindAllKeywords
import com.groupaxis.groupsuite.routing.write.domain.model.routing.rule.RuleMessages._
import com.groupaxis.groupsuite.synchronizator.app.DBHelper
import com.groupaxis.groupsuite.synchronizator.app.UpdateApp.{logger, saaPointESRepository, saaRuleRepository, timeout}
import com.groupaxis.groupsuite.synchronizator.domain.model.points.SAASynchronousRules
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext}

object KeywordWriteService {

  final val Name = "keyword-definition-write-service"

  def props(ruleRepo : JdbcRuleWriteRepository): Props = Props(classOf[KeywordWriteService], ruleRepo)

}

class KeywordWriteService(ruleRepo : JdbcRuleWriteRepository) extends Actor with Logging {
  //import RuleWriteService._
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ArithmeticException      => Resume
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case e: Exception                =>
        logger.warn("[RuleWriteService] Exception has been received, so restarting the actor " + e.getMessage)
        e.printStackTrace()
        Restart
    }

  //TODO: Move this to another actor to make the denormalization asynchronous
  val config : Config = context.system.settings.config
  import org.elasticsearch.common.settings.Settings
  val settings = Settings.settingsBuilder().put("cluster.name", config.getString("elastic.cluster.name")).build()
  val client : ElasticClient = ElasticClient.transport(settings, ElasticsearchClientUri(s"elasticsearch://"+config.getString("elastic.url")))
  val esRuleWriteRepository = context.actorOf(ESRuleWriteRepository.props(client), ESRuleWriteRepository.Name)

  def receive: Receive = {

    case FindAllKeywords() =>
      logger.debug(s" receiving FindAllkeywords on KeywordWriteService")
      val result = ruleRepo.getKeywords()
      logger.debug(s"retrieving rule $result")
      sender() ! result
  }
  private def updateSAARoutingPoints()={

  }
}

