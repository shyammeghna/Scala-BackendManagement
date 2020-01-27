package com.groupaxis.groupsuite.routing.application.services

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.groupaxis.groupsuite.routing.infrastructor.es.ESRuleWriteRepository
import com.groupaxis.groupsuite.routing.infrastructor.jdbc.JdbcSAASqlFunctionWriteRepository
import com.groupaxis.groupsuite.routing.write.domain.model.routing.rule.RuleMessages.{PointRenumbered, RenumberRuleSeqES}
import com.groupaxis.groupsuite.routing.write.domain.model.routing.sql.function.SAASqlFunction.RenumberPointSeq
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContext}


object SAASqlFunctionWriteService {

  final val Name = "saa-sql-function-write-service"

  def props(saaSqlFunctionRepo : JdbcSAASqlFunctionWriteRepository): Props = Props(classOf[SAASqlFunctionWriteService], saaSqlFunctionRepo)

}

class SAASqlFunctionWriteService(saaSqlFunctionRepo : JdbcSAASqlFunctionWriteRepository) extends Actor with Logging {

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
    case RenumberPointSeq(startNumber, incrementNumber, pointName)=>
      logger.info("receiving RenumberPointSeq on JdbcSAASqlFunctionWriteRepository")
      val result = saaSqlFunctionRepo.renumberPointSeq(startNumber, incrementNumber, pointName)
      result.fold(
        errorMsg => {
          logger.info("Rule update failed with " + errorMsg)
        },
        ruleUpdated => {
          try {
            logger.info(s"rule $pointName renumbered, now it will be renumbered into ES")
            Await.result((esRuleWriteRepository ? RenumberRuleSeqES(pointName, startNumber, incrementNumber)).mapTo[PointRenumbered], 5.seconds)
            logger.info("rule was renumbered into ES ")
          } catch {
            case e : Exception =>
              logger.error("rule was not updated into ES : " + e.getMessage)
          }
        })
      sender() ! result
  }

}
