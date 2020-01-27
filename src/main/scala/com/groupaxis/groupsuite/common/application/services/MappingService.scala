package com.groupaxis.groupsuite.common.application.services

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, DeathPactException, OneForOneStrategy, Props}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.write.domain.model.mapping.MappingMessages._
import com.groupaxis.groupsuite.commons.protocol.{Master, Work}
import org.apache.logging.log4j.scala.Logging
import akka.pattern.{ask, pipe}

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object MappingService {

  final val Name = "parameter-service"

  def props(workTimeout: FiniteDuration): Props = Props(classOf[MappingService], workTimeout)

  case object Ok

  case object NotOk

}

class MappingService(workTimeout: FiniteDuration) extends Actor with Logging {

  import MappingService._

  def nextWorkId(): String = UUID.randomUUID().toString

  override val supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException => Stop
    case _: Exception =>
      logger.error("Restarting parameterMasterProxy actor")
      //      currentWorkId foreach { workId => sendToMaster(WorkFailed(workerId, workId)) }
      //      context.become(idle)
      Restart
  }

  //Producer
  val mappingMasterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/mapping/master"),
    name = "mappingMasterProxy")

  //Consumer
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  private var promises = Map[String, Promise[Any]]()

  def receive: Receive = {
    case FindAllMappings =>
      val findAllMappings = FindAllMappings
      logger.debug(s"sending to MappingMasterProxy $findAllMappings")
      (mappingMasterProxy ? Work(nextWorkId(), findAllMappings)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from MappingServiceMaster FindAllMappings")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from MappingServiceMaster FindAllMappings")
          NotOk
      } pipeTo sender()

    case FindMappingsBySystem(system) =>
      val findMappingsBySystem = FindMappingsBySystem(system)
      logger.debug(s"sending to MappingMasterProxy $findMappingsBySystem")
      (mappingMasterProxy ? Work(nextWorkId(), findMappingsBySystem)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from MappingServiceMaster findMappingsBySystem")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from MappingServiceMaster findMappingsBySystem")
          NotOk
      } pipeTo sender()

    case FindMappingByKeywordAndSystem(keyword, system) =>
      val findMappingByKeywordAndSystem = FindMappingByKeywordAndSystem(keyword, system)
      logger.debug(s"sending to MappingMasterProxy $findMappingByKeywordAndSystem")
      (mappingMasterProxy ? Work(nextWorkId(), findMappingByKeywordAndSystem)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from MappingServiceMaster findMappingByKeywordAndSystem")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from MappingServiceMaster findMappingByKeywordAndSystem")
          NotOk
      } pipeTo sender()

    case CreateMapping(mapping) =>
      val createMapping = CreateMapping(mapping)
      logger.debug(s"sending to MappingMasterProxy $createMapping")
      (mappingMasterProxy ? Work(nextWorkId(), createMapping)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from MappingServiceMaster createMapping")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from MappingServiceMaster createMapping")
          NotOk
      } pipeTo sender()

    case UpdateMapping(mapping) =>
      val updateMapping = UpdateMapping(mapping)
      logger.debug(s"sending to MappingMasterProxy $updateMapping")
      (mappingMasterProxy ? Work(nextWorkId(), updateMapping)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from MappingServiceMaster createMapping")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from MappingServiceMaster createMapping")
          NotOk
      } pipeTo sender()

    case DeleteMappingByKeywordAndSystem(keyword, system) =>
      val deleteMappingByKeywordAndSystem = DeleteMappingByKeywordAndSystem(keyword, system)
      logger.debug(s"sending to MappingMasterProxy $deleteMappingByKeywordAndSystem")
      (mappingMasterProxy ? Work(nextWorkId(), deleteMappingByKeywordAndSystem)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from MappingServiceMaster deleteMapping")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from MappingServiceMaster deleteMapping")
          NotOk
      } pipeTo sender()
  }

  def addWorkPromise(workId: String): Future[Any] = {
    val f = Promise[Any]()
    promises += (workId -> f)
    logger.info("wainting........")
    f.future
  }
}
