package com.groupaxis.groupsuite.common.application.services

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.groupaxis.groupsuite.common.infrastructor.es.ESUserWriteRepository
import com.groupaxis.groupsuite.common.infrastructor.jdbc.JdbcUserWriteRepository
import com.groupaxis.groupsuite.common.write.domain.model.user.UserMessages._
import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

object UserWriteService {

  final val Name = "user-write-service"

  def props(userRepo: JdbcUserWriteRepository): Props = Props(classOf[UserWriteService], userRepo)

}

class UserWriteService(userRepo: JdbcUserWriteRepository) extends Actor with Logging {
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

  //import org.elasticsearch.common.settings.Settings

  //val settings = Settings.settingsBuilder().put("cluster.name", config.getString("elastic.cluster.name")).build()
  //val client: ElasticClient = ElasticClient.transport(settings, ElasticsearchClientUri(s"elasticsearch://" + config.getString("elastic.url")))
  //val esUserWriteRepository = context.actorOf(ESUserWriteRepository.props(client), ESUserWriteRepository.Name)

  def receive: Receive = {
    case CreateUser(id, userEntityUpdate) =>
      logger.info(s" receiving create($userEntityUpdate) on UserWriteService")
      val result = userRepo.createUser(userEntityUpdate.merge(id))
      result.fold(
        errorMsg => {
          logger.info("User creation failed with " + errorMsg)
        },
        userCreated => {
          try {
            logger.info(s"user $result created, now it will be inserted into ES")
            //Await.result((esUserWriteRepository ? InsertUserES(userCreated.user.toES())).mapTo[UserESInserted], 5.seconds)
            logger.info("user was created into ES ")
          } catch {
            case e: Exception =>
              logger.error("user was not inserted into ES : " + e.getMessage)
          }
        })
      sender() ! result

    case UpdateUser(id, userEntityUpdate) =>
      logger.debug(s" receiving update($userEntityUpdate) on UserWriteService")
      val result = userRepo.updateUser(id, userEntityUpdate)
      result.fold(
        errorMsg => {
          logger.info("User update failed with " + errorMsg)
        },
        userUpdated => {
          try {
            logger.info(s"user $result created, now it will be inserted into ES")
            //Await.result((esUserWriteRepository ? UpdateUserES(userUpdated.user.toES())).mapTo[UserESInserted], 5.seconds)
            logger.info("user was updated into ES ")
          } catch {
            case e: Exception =>
              logger.error("user was not updated into ES : " + e.getMessage)
          }
        })
      sender() ! result

    case FindUserByUsername(id) =>
      logger.debug(s" receiving FindUserByUsername($id) on UserWriteService")
      val result = userRepo.getUserByUsername(id)
      logger.debug(s"retrieving user $result")
      sender() ! result

    case FindAllUsers() =>
      logger.debug(s" receiving FindAllUsers on RuleWriteService")
      val result = userRepo.getUsers()
      logger.debug(s"retrieving users $result")
      sender() ! result
  }
}
