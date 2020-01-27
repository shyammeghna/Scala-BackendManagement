package com.groupaxis.groupsuite.common.application.services

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.infrastructor.jdbc.JdbcMappingWriteRepository
import com.groupaxis.groupsuite.common.write.domain.model.mapping.MappingMessages._
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object MappingWriteService {

  final val Name = "mapping-write-service"

  def props(mappingRepo: JdbcMappingWriteRepository): Props = Props(classOf[MappingWriteService], mappingRepo)

}

class MappingWriteService(mappingRepo: JdbcMappingWriteRepository) extends Actor with Logging{
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
    case FindAllMappings() =>
      logger.debug(s" receiving FindAllMappings on MappingWriteService")
      val result = mappingRepo.getMappings()
      logger.debug(s"retrieving mappings $result")
      sender() ! result

    case FindMappingsBySystem(system) =>
      logger.debug(s" receiving FindMappingsBySystem $system on MappingWriteService")
      val result = mappingRepo.getMappingsBySystem(system)
      logger.debug(s"retrieving mappings $result")
      sender() ! result

    case FindMappingByKeywordAndSystem(keyword, system) =>
      logger.debug(s" receiving FindMappingByKeywordAndSystem ($keyword, $system) on MappingWriteService")
      val result = mappingRepo.getMappingsByKeywordAndSystem(keyword, system)
      logger.debug(s"retrieving mapping $result")
      sender() ! result

    case CreateMapping(mapping) =>
      logger.debug(s" receiving Creating (${mapping.keyword}, ${mapping.for_system}) on MappingWriteService")
      val result = mappingRepo.createMapping(mapping.merge())
      logger.debug(s"creating mapping $result")
      sender() ! result

    case UpdateMapping(mapping) =>
      logger.debug(s" receiving Updating (${mapping.keyword}, ${mapping.for_system}) on MappingWriteService")
      val result = mappingRepo.updateMapping(mapping.merge())
      logger.debug(s"updating mapping $result")
      sender() ! result

    case DeleteMappingByKeywordAndSystem(keyword, system) =>
      logger.debug(s" receiving Deleting (${keyword}, ${system}) on MappingWriteService")
      val result = mappingRepo.deleteMappingByKeywordAndSystem(keyword, system)
      logger.debug(s"deleting mapping $result")
      sender() ! result
  }

}
