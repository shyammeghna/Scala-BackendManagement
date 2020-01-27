package com.groupaxis.groupsuite.common.application.services

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.groupaxis.groupsuite.common.infrastructor.es.ESUserWriteRepository
import com.groupaxis.groupsuite.common.infrastructor.jdbc.JdbcApplicationLogWriteRepository
import com.groupaxis.groupsuite.common.write.domain.model.applicationLog.ApplicationLogMessages._
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

object ApplicationLogWriteService {

  final val Name = "log-write-service"

  def props(logRepo: JdbcApplicationLogWriteRepository): Props = Props(classOf[ApplicationLogWriteService], logRepo)

}

class ApplicationLogWriteService(logRepo: JdbcApplicationLogWriteRepository) extends Actor with Logging {
  //import UserWriteService._
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ArithmeticException => Resume
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case e: Exception =>
        logger.warn("[RuleWriteService] Exception has been received, so restarting the actor " + e.getMessage)
        e.printStackTrace()
        Restart
    }

  //TODO: Move this to another actor to make the denormalization asynchronous
  val config: Config = context.system.settings.config

  def receive: Receive = {
    case CreateApplicationLog(log) =>
      logger.info(s" receiving create($log) on ApplicationLogWriteService")
      val result = logRepo.createApplicationLog(log)
      result.fold(
        errorMsg => {
          logger.info("Log creation failed with " + errorMsg)
        },
        applicationLogCreated => {
          logger.info(s"log $result created")
        })
      sender() ! result

    case FindAllApplicationLogs =>
      logger.info(s" receiving FindAllApplicationLogs on ApplicationLogWriteService")
      val result = logRepo.getApplicationLogs()
      result.fold(
        errorMsg => {
          logger.info("Logs retrieving failed with " + errorMsg)
        },
        applicationLogsFound => {
          logger.info(s"retrieving $result")
        })
      sender() ! result

    case FindApplicationLogById(id) =>
      logger.info(s" receiving log $id on ApplicationLogWriteService")
      val result = logRepo.getApplicationLogById(id)
      result.fold(
        errorMsg => {
          logger.info(s"Log $id retrieving failed with " + errorMsg)
        },
        applicationLogFound => {
          logger.info(s"retrieving $result")
        })
      sender() ! result

    case FindApplicationLogByUserAndTime(user, time) =>
      logger.info(s" receiving log $user $time on ApplicationLogWriteService")
      val result = logRepo.getApplicationLogByUserAndTime(user, time)
      result.fold(
        errorMsg => {
          logger.info(s"Log $user $time retrieving failed with " + errorMsg)
        },
        applicationLogFound => {
          logger.info(s"retrieving $result")
        })
      sender() ! result

    case FindApplicationLogDeltaExportInfo() =>
      logger.info(s" receiving FindApplicationLogDeltaExportInfo on ApplicationLogWriteService")
      val result = logRepo.getApplicationLogDeltaExportInfo()
      result.fold(
        errorMsg => {
          logger.info(s"Log FindApplicationLogDeltaExportInfo retrieving failed with " + errorMsg)
        },
        applicationLogFound => {
          logger.info(s"retrieving $result")
        })
      sender() ! result

    case UpdateApplicationLog(logUpdate) =>
      logger.info(" update log "+ logUpdate.id + " on ApplicationLogWriteService")
      val result = logRepo.updateApplicationLog(logUpdate)
      result.fold(
        errorMsg => {
          logger.info(s"Log ${logUpdate.id} updating failed with " + errorMsg)
        },
        applicationLogDeleted => {
          logger.info(s"update $result")
        })
      sender() ! result

    case DeleteApplicationLog(log) =>
      logger.info(" delete log "+ log.id + " on ApplicationLogWriteService")
      val result = logRepo.deleteApplicationLog(log)
      result.fold(
        errorMsg => {
          logger.info(s"Log ${log.id} deleting failed with " + errorMsg)
        },
        applicationLogDeleted => {
          logger.info(s"delete $result")
        })
      sender() ! result

    case ClearApplicationLogs(days) =>
      logger.info(" clear logs on ApplicationLogWriteService")

      val result = logRepo.clearApplicationLogs(days)
      result.fold(
        errorMsg => {
          logger.info(s"Logs clearing failed with " + errorMsg)
        },
        applicationLogDeleted => {
          logger.info(s"clear $result")
        })
      sender() ! result

  }



}
