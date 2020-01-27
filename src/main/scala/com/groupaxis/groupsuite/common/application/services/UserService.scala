package com.groupaxis.groupsuite.common.application.services

import java.util.UUID

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, DeathPactException, OneForOneStrategy, Props, actorRef2Scala}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.groupaxis.groupsuite.common.write.domain.model.user.UserMessages._
import com.groupaxis.groupsuite.commons.protocol.{Master, Work, WorkResult}
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

object UserService {

  final val Name = "user-service"

  def props(workTimeout: FiniteDuration): Props = Props(classOf[UserService], workTimeout)

  case object Ok

  case object NotOk

}

class UserService(workTimeout: FiniteDuration) extends Actor with Logging {

  import UserService._

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
  val userMasterProxy = context.actorOf(
    ClusterSingletonProxy.props(
      settings = ClusterSingletonProxySettings(context.system).withRole("backend"),
      singletonManagerPath = "/user/master"),
    name = "userMasterProxy")

  //Consumer
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! DistributedPubSubMediator.Subscribe(Master.ResultsTopic, self)

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  private var promises = Map[String, Promise[Any]]()

  def receive: Receive = {
    case FindUserByUsername(id) =>
      val findUser = FindUserByUsername(id)
      logger.debug(s"sending to RuleServiceMaster $findUser")
      (userMasterProxy ? Work(nextWorkId(), findUser)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from UserServiceMaster FindUserByUsername")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from UserServiceMaster FindUserByUsername")
          NotOk
      } pipeTo sender()

    case fau: FindAllUsers =>
      logger.debug(s"sending to RuleServiceMaster FindAllRules")
      (userMasterProxy ? Work(nextWorkId(), fau)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from UserServiceMaster FindAllUsers")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from UserServiceMaster FindAllUsers")
          NotOk
      } pipeTo sender()

    case cr: CreateUser =>
      logger.debug(s"sending to RuleServiceMaster CreateRule")
      (userMasterProxy ? Work(nextWorkId(), cr)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from Master (UserService) => CreateUser")
          Await.result(addWorkPromise(workId), 10.seconds)
      } recover {
        case _ =>
          logger.error(s"receiving KO from UserServiceMaster CreateUser")
          NotOk
      } pipeTo sender()

    case uu: UpdateUser =>
      logger.debug(s"sending to RuleServiceMaster UpdateRule")
      (userMasterProxy ? Work(nextWorkId(), uu)) map {
        case Master.Ack(workId) =>
          logger.debug(s"receiving Ack from Master (UserService) => UpdateUser")
          val updateResult = Await.result(addWorkPromise(workId), 10.seconds)
          logger.info(s"piping to HttpRouting with $updateResult")
          updateResult
      } recover {
        case _ =>
          logger.error(s"receiving KO from UserServiceMaster UpdateUser")
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