package com.groupaxis.groupsuite.common.application.services

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.infrastructor.jdbc.JdbcParameterWriteRepository
import com.groupaxis.groupsuite.common.write.domain.model.parameter.ParameterMessages.{FindParameterByKey, FindParameters, UpdateParameter}
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object ParameterWriteService {

  final val Name = "parameter-write-service"

  def props(permissionRepo: JdbcParameterWriteRepository): Props = Props(classOf[ParameterWriteService], permissionRepo)

}

class ParameterWriteService(parameterRepo: JdbcParameterWriteRepository) extends Actor with Logging{
  //import ParameterWriteService._
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ArithmeticException => Resume
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case e: Exception =>
        logger.warn("[ParameterWriteService] Exception has been received, so restarting the actor " + e.getMessage)
        e.printStackTrace()
        Restart
    }

  //TODO: Move this to another actor to make the denormalization asynchronous
  val config: Config = context.system.settings.config

  //import org.elasticsearch.common.settings.Settings
  def receive: Receive = {
    case FindParameterByKey(keyName) =>
      logger.debug(s" receiving FindParameterByKey ($keyName) on ParameterWriteService")
      val result = parameterRepo.getParameterByKey(keyName)
      logger.debug(s"retrieving parameter $result")
      sender() ! result

    case FindParameters() =>
      logger.debug(s" receiving FindParameters on ParameterWriteService")
      val result = parameterRepo.getParameters()
      logger.debug(s"retrieving parameters $result")
      sender() ! result

    case UpdateParameter(parameter) =>
      logger.debug(s" receiving UpdateParameter ($parameter.keyName) on ParameterWriteService")
      val result = parameterRepo.updateParameter(parameter)
      logger.debug(s"retrieving parameter $result")
      sender() ! result
  }
}