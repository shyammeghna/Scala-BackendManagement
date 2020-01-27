package com.groupaxis.groupsuite.common.application.services

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, DeathPactException, OneForOneStrategy, Props, actorRef2Scala}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.write.domain.model.permission.PermissionMessages._
import com.groupaxis.groupsuite.commons.protocol.{Master, Work, WorkResult}
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}


object PermissionService {

  final val Name = "permission-service"

  def props(workTimeout: FiniteDuration): Props = Props(classOf[PermissionService], workTimeout)

  case object Ok

  case object NotOk

}

class PermissionService(workTimeout: FiniteDuration) extends Actor with Logging {

  import PermissionService._

  def nextWorkId(): String = UUID.randomUUID().toString

  override val supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException => Stop
    case _: Exception =>
      logger.error("Restarting permissionMasterProxy actor")
      //      currentWorkId foreach { workId => sendToMaster(WorkFailed(workerId, workId)) }
      //      context.become(idle)
      Restart
  }

  //Producer
  val permissionMasterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/permission/master"),
    name = "permissionMasterProxy")

  //Consumer
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  private var promises = Map[String, Promise[Any]]()

  def receive: Receive = {
    case fap: FindAllPermissions =>
      logger.debug(s"sending to PermissionServiceMaster FindAllPermissions")
      (permissionMasterProxy ? Work(nextWorkId(), fap)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from PermissionMaster FindAllPermissions")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from PermissionServiceMaster FindAllPermissions")
          NotOk
      } pipeTo sender()

    case _: DistributedPubSubMediator.SubscribeAck =>
    case WorkResult(workId, result) =>
      logger.info(s"---------------      Consumed result: $result")
      promises.get(workId).map { pRest => pRest success result }
      promises -= workId
  }

  def addWorkPromise(workId: String): Future[Any] = {
    val f = Promise[Any]()
    promises += (workId -> f)
    logger.info("wainting........")
    f.future
  }

}
