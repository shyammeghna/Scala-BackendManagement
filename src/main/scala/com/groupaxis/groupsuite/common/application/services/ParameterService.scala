package com.groupaxis.groupsuite.common.application.services

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, DeathPactException, OneForOneStrategy, Props}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.write.domain.model.parameter.ParameterMessages.{FindParameterByKey, FindParameters, UpdateParameter}
import com.groupaxis.groupsuite.commons.protocol.{Master, Work}
import org.apache.logging.log4j.scala.Logging
import akka.pattern.{ask, pipe}

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.{DurationInt, FiniteDuration}


object ParameterService {

  final val Name = "parameter-service"

  def props(workTimeout: FiniteDuration): Props = Props(classOf[ParameterService], workTimeout)

  case object Ok

  case object NotOk

}

class ParameterService(workTimeout: FiniteDuration) extends Actor with Logging{

  import ParameterService._

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
  val parameterMasterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/parameter/master"),
    name = "parameterMasterProxy")

  //Consumer
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  private var promises = Map[String, Promise[Any]]()

  def receive: Receive = {
    case FindParameterByKey(keyName) =>
      val findParameter = FindParameterByKey(keyName)
      logger.debug(s"sending to ParameterMasterProxy $findParameter")
      (parameterMasterProxy ? Work(nextWorkId(), findParameter)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ParameterServiceMaster FindParameterByKey")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ParameterServiceMaster FindParameterByKey")
          NotOk
      } pipeTo sender()

    case FindParameters() =>
      val findParameters = FindParameters()
      logger.debug(s"sending to ParameterMasterProxy $findParameters")
      (parameterMasterProxy ? Work(nextWorkId(), findParameters)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ParameterServiceMaster FindParameters")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ParameterServiceMaster FindParameters")
          NotOk
      } pipeTo sender()

    case UpdateParameter(parameter) =>
      val updateParameter = UpdateParameter(parameter)
      logger.debug(s"sending to ParameterMasterProxy $updateParameter")
      (parameterMasterProxy ? Work(nextWorkId(), updateParameter)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ParameterServiceMaster UpdateParameter")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ParameterServiceMaster UpdateParameter")
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
