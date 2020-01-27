package com.groupaxis.groupsuite.common.application.services

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, DeathPactException, OneForOneStrategy, Props, actorRef2Scala}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.write.domain.model.applicationLog.ApplicationLogMessages._
import com.groupaxis.groupsuite.commons.protocol.{Master, Work, WorkResult}
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

object ApplicationLogService {

  final val Name = "log-service"

  def props(workTimeout: FiniteDuration): Props = Props(classOf[ApplicationLogService], workTimeout)

  case object Ok

  case object NotOk

}

class ApplicationLogService(workTimeout: FiniteDuration) extends Actor with Logging {

  import ApplicationLogService._

  def nextWorkId(): String = UUID.randomUUID().toString

  override val supervisorStrategy = OneForOneStrategy() {
    case _: ActorInitializationException => Stop
    case _: DeathPactException => Stop
    case _: Exception =>
      logger.error("Restarting ruleMasterProxy actor")
      //      currentWorkId foreach { workId => sendToMaster(WorkFailed(workerId, workId)) }
      //      context.become(idle)
      Restart
  }

  //Producer
  val applicationLogMasterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/applicatonLog/master"),
    name = "applicationLogMasterProxy")

  //Consumer
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  private var promises = Map[String, Promise[Any]]()

  def receive: Receive = {
    case faal: FindAllApplicationLogs =>
      logger.debug(s"sending to ApplicationLogMaster FindAllApplicationLogs")
      (applicationLogMasterProxy ? Work(nextWorkId(), faal)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ApplicationLogMaster FindAllApplicationLogs")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ApplicationLogMaster FindAllApplicationLogs")
          NotOk
      } pipeTo sender()

    case FindApplicationLogById(id) =>
      val findLog = FindApplicationLogById(id)
      logger.debug(s"sending to ApplicationLogMaster FindApplicationLogById")
      (applicationLogMasterProxy ? Work(nextWorkId(), findLog)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ApplicationLogMaster FindApplicationLogById")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ApplicationLogMaster FindApplicationLogById")
          NotOk
      } pipeTo sender()

    case FindApplicationLogByUserAndTime(user, startTime) =>
      val findLog = FindApplicationLogByUserAndTime(user, startTime)
      logger.debug(s"sending to ApplicationLogMaster FindApplicationLogByUserAndStartTime")
      (applicationLogMasterProxy ? Work(nextWorkId(), findLog)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ApplicationLogMaster FindApplicationLogByUserAndStartTime")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ApplicationLogMaster FindApplicationLogByUserAndStartTime")
          NotOk
      } pipeTo sender()

    case CreateApplicationLog(log) =>
      val createLog = CreateApplicationLog(log)
      logger.debug(s"sending to ApplicationLogMaster CreateApplicationLog")
      (applicationLogMasterProxy ? Work(nextWorkId(), createLog)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ApplicationLogMaster CreateApplicationLog")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ApplicationLogMaster CreateApplicationLog")
          NotOk
      } pipeTo sender()

    case UpdateApplicationLog(logUpdate) =>
      val updateLog = UpdateApplicationLog(logUpdate)
      logger.debug(s"sending to ApplicationLogMaster UpdateApplicationLog")
      (applicationLogMasterProxy ? Work(nextWorkId(), updateLog)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ApplicationLogMaster UpdateApplicationLog")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ApplicationLogMaster UpdateApplicationLog")
          NotOk
      } pipeTo sender()

    case DeleteApplicationLog(log) =>
      val deleteLog = DeleteApplicationLog(log)
      logger.debug(s"sending to ApplicationLogMaster UpdateApplicationLog")
      (applicationLogMasterProxy ? Work(nextWorkId(), deleteLog)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ApplicationLogMaster UpdateApplicationLog")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ApplicationLogMaster UpdateApplicationLog")
          NotOk
      } pipeTo sender()

    case ClearApplicationLogs(days) =>
      val deleteLog = ClearApplicationLogs(days)
      logger.debug(s"sending to ApplicationLogMaster ClearApplicationLogs")
      (applicationLogMasterProxy ? Work(nextWorkId(), deleteLog)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from ApplicationLogMaster ClearApplicationLogs")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from ApplicationLogMaster ClearApplicationLogs")
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
