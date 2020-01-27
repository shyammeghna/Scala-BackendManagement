package com.groupaxis.groupsuite.common.application.services

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.infrastructor.jdbc.JdbcPermissionWriteRepository
import com.groupaxis.groupsuite.common.write.domain.model.permission.PermissionMessages.FindAllPermissions
import org.apache.logging.log4j.scala.Logging
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}


object PermissionWriteService {

  final val Name = "permission-write-service"

  def props(permissionRepo: JdbcPermissionWriteRepository): Props = Props(classOf[PermissionWriteService], permissionRepo)

}

class PermissionWriteService (permissionRepo: JdbcPermissionWriteRepository) extends Actor with Logging {
  //import PermissionWriteService._
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ArithmeticException => Resume
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case e: Exception =>
        logger.warn("[PermissionWriteService] Exception has been received, so restarting the actor " + e.getMessage)
        e.printStackTrace()
        Restart
    }

  //TODO: Move this to another actor to make the denormalization asynchronous
  val config: Config = context.system.settings.config

  //import org.elasticsearch.common.settings.Settings

  def receive: Receive = {
    case FindAllPermissions() =>
    logger.debug(s" receiving FindAllPermissions on PermissionWriteService")
    val result = permissionRepo.getPermissions()
    logger.debug(s"retrieving rule $result")
    sender() ! result
  }


}