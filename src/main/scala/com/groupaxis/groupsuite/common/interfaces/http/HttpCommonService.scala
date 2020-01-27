package com.groupaxis.groupsuite.common.interfaces.http

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props, Status}
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.pattern.{ask, pipe}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.Timeout
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import com.groupaxis.groupsuite.common.application.services._
import com.groupaxis.groupsuite.common.infrastructor.jdbc._
import com.groupaxis.groupsuite.common.write.domain.model.applicationLog.{ApplicationLogDAO, ApplicationLogEntity, ApplicationLogEntityUpdate}
import com.groupaxis.groupsuite.common.write.domain.model.applicationLog.ApplicationLogMessages._
import com.groupaxis.groupsuite.common.write.domain.model.mapping.{MappingDAO, MappingEntity, MappingEntityUpdate}
import com.groupaxis.groupsuite.common.write.domain.model.mapping.MappingMessages._
import com.groupaxis.groupsuite.common.write.domain.model.parameter.{ParameterDAO, ParameterEntity}
import com.groupaxis.groupsuite.common.write.domain.model.parameter.ParameterMessages._
import com.groupaxis.groupsuite.common.write.domain.model.permission.PermissionDAO
import com.groupaxis.groupsuite.common.write.domain.model.permission.PermissionMessages._
import com.groupaxis.groupsuite.common.write.domain.model.user.{UserDAO, UserEntityUpdate}
import com.groupaxis.groupsuite.common.write.domain.model.user.UserMessages._
import com.groupaxis.groupsuite.persistence.datastore.jdbc.Database
import com.groupaxis.groupsuite.synchronizator.http.GPHttpHelper
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import org.joda.time.DateTime


object HttpCommonService {

  final val Name = "http-common-routing-service"

  def props(config: Config, database: Database): Props = Props(new HttpCommonService(config, database))

}

class HttpCommonService(config: Config, database: Database) extends Actor with Logging with HttpResource {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout: Timeout = 50.seconds

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ArithmeticException => Resume
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case e: Exception =>
        logger.warn("[HttpRoutingService] Exception has been received, so restarting the actor " + e.getMessage)
        e.printStackTrace()
        Restart
    }

  val decider: Supervision.Decider = (ex) => Supervision.Resume

  import io.circe.generic.auto._

  implicit val mat: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))
  val DBDriver = slick.driver.PostgresDriver
  val userDAO: UserDAO = new UserDAO(DBDriver)
  val userRepo: JdbcUserWriteRepository = new JdbcUserWriteRepository(userDAO, database, 10.seconds)
  val userService = context.actorOf(UserWriteService.props(userRepo), UserWriteService.Name)

  val permissionDAO: PermissionDAO = new PermissionDAO(DBDriver)
  val permissionRepo: JdbcPermissionWriteRepository = new JdbcPermissionWriteRepository(permissionDAO, database, 10.seconds)
  val permissionService = context.actorOf(PermissionWriteService.props(permissionRepo), PermissionWriteService.Name)

  val parameterDAO: ParameterDAO = new ParameterDAO(DBDriver)
  val parameterRepo: JdbcParameterWriteRepository = new JdbcParameterWriteRepository(parameterDAO, database, 10.seconds)
  val parameterService = context.actorOf(ParameterWriteService.props(parameterRepo), ParameterWriteService.Name)

  val applicationLogDAO: ApplicationLogDAO = new ApplicationLogDAO(DBDriver)
  val applicationLogRepo: JdbcApplicationLogWriteRepository = new JdbcApplicationLogWriteRepository(applicationLogDAO, database, 10.seconds)
  val applicationService = context.actorOf(ApplicationLogWriteService.props(applicationLogRepo), ApplicationLogWriteService.Name)

  val mappingDAO: MappingDAO = new MappingDAO(DBDriver)
  val mappingRepo: JdbcMappingWriteRepository = new JdbcMappingWriteRepository(mappingDAO, database, 10.seconds)
  val mappingService = context.actorOf(MappingWriteService.props(mappingRepo), MappingWriteService.Name)

  val settings = CorsSettings.defaultSettings.copy(allowedMethods = scala.collection.immutable.Seq(GET, POST, PUT, DELETE, HEAD, OPTIONS))

  def routes: Route = logRequestResult("authenticationController") {
    path("") {
      redirect("common/users", StatusCodes.PermanentRedirect)
    } ~
      pathPrefix("common" / "users") {
        cors(settings) {
          path(Segment) { id: String =>
            get {
              logger.info(s"here! common/user find $id request")
              complete((userService ? FindUserByUsername(id)).map(r => r.asInstanceOf[Either[String, UserFound]]))
            } ~
              post {
                entity(as[UserEntityUpdate]) { user =>
                  completeWithLocationHeader[String, UserCreated, Either[String, UserCreated]](
                    resourceId = (userService ? CreateUser(id, user)).map(r => r.asInstanceOf[Either[String, UserCreated]]),
                    ifDefinedStatus = 201, ifEmptyStatus = 409)
                }
              } ~
              put {
                entity(as[UserEntityUpdate]) { user =>
                  complete((userService ? UpdateUser(id, user)).map(r => r.asInstanceOf[Either[String, UserUpdated]]))
                }

              }
          } ~
            pathEnd {
              get {
                logger.info(s"here! common/users request")
                complete((userService ? FindAllUsers()).mapTo[Either[String, UsersFound]])
              }
            }
        }
      }
  }

  def permissionRoutes: Route = logRequestResult("permissionController") {
    //path("") {
    //  redirect("common/permissions", StatusCodes.PermanentRedirect)
    //} ~
      pathPrefix("common" / "permissions") {
        cors(settings) {
          pathEnd {
            get {
              logger.info(s"here! common/permissions request")
              complete((permissionService ? FindAllPermissions()).mapTo[Either[String, PermissionsFound]])
            }
          }
        }
      }
  }

  def parameterRoutes: Route = logRequestResult("parameterController") {
    pathPrefix("common" / "parameter") {
      cors(settings) {
        path(Segment) { keyName: String =>
          get {
            logger.info(s"here! common/parameter request")
            complete((parameterService ? FindParameterByKey(keyName)).mapTo[Either[String, ParameterFound]])
          } ~
          post {
            entity(as[ParameterEntity]) { parameter =>
              logger.info(s"here! common/parameter request")
              complete((parameterService ? UpdateParameter(parameter)).mapTo[Either[String, ParameterUpdated]])
            }
          }
        }
      }
    } ~
      pathPrefix("common" / "parameters") {
        cors(settings) {
          pathEnd {
            get {
              logger.info(s"here! common/parameters request")
              complete((parameterService ? FindParameters()).mapTo[Either[String, ParametersFound]])
            }
          }
        }
      }
  }

  def applicationLogRoutes: Route = logRequestResult("applicationLogController") {
    pathPrefix("common" / "logs"){
      cors(settings) {
        pathEnd{
          get{
            logger.info(s"here! common/logs request")
            complete((applicationService ? FindAllApplicationLogs).mapTo[Either[String,ApplicationLogsFound]])
          }
          /*~
          delete{
            logger.info(s"here! common/logs request")
            complete((applicationService ? ClearApplicationLogs).mapTo[Either[String,Int]])
          }*/
        }~
        path(IntNumber){days: Int=>
          delete{
            logger.info(s"here! common/logs request")
            complete((applicationService ? ClearApplicationLogs(days)).mapTo[Either[String,Int]])
          }
        }
      }
    }  ~
    pathPrefix("common" / "log"){
      cors(settings) {
        path(Segment){ id: String =>
          id match{
            case "deltaExport" =>
              get {
                complete((applicationService ? FindApplicationLogDeltaExportInfo()).mapTo[Either[String,ApplicationLogFound]])
              }
            case _=>
              get {
                complete((applicationService ? FindApplicationLogById(Some(id.toInt))).mapTo[Either[String,ApplicationLogFound]])
              }
          }
        } ~
        pathEnd{
          get{
            extractRequestContext { requestContext =>
              val request: HttpRequest = requestContext.request
              val user = GPHttpHelper.headerValue(request, "user").get
              val startTime = GPHttpHelper.headerValue(request, "startTime").get
              complete((applicationService ? FindApplicationLogByUserAndTime(user,new DateTime(startTime))).mapTo[Either[String,ApplicationLogFound]])
            }
          } ~
            post{
            entity(as[ApplicationLogEntityUpdate]) { log =>
              complete((applicationService ? UpdateApplicationLog(log)).mapTo[Either[String,Int]])
            }
          } ~
          put{
            entity(as[ApplicationLogEntity]) { log =>
              complete((applicationService ? CreateApplicationLog(log)).mapTo[Either[String,ApplicationLogCreated]])
            }
          }~
          delete{
            entity(as[ApplicationLogEntity]) { log =>
              complete((applicationService ? DeleteApplicationLog(log)).mapTo[Either[String,Int]])
            }
          }
        }
      }
    }
  }

  def mappingRoutes: Route = logRequestResult("mappingController") {
    pathPrefix("common" / "mappings") {
      cors(settings) {
        pathEnd {
          get {
            logger.info(s"here! common/mappings request")
            complete((mappingService ? FindAllMappings()).mapTo[Either[String, MappingsFound]])
          }
        } ~
        path(Segment){ system =>
          get {
            logger.info(s"here! common/mappings request")
            complete((mappingService ? FindMappingsBySystem(system)).mapTo[Either[String, MappingsFound]])
          }
        }
      }
    }~
    pathPrefix("common" / "mapping" / Segment / "system") { keyword: String =>
      cors(settings) {
        path(Segment){ system =>
          get{
            logger.info(s"here! common/mapping request")
            complete((mappingService ? FindMappingByKeywordAndSystem(keyword, system)).mapTo[Either[String, MappingFound]])
          } ~
          post {
            entity(as[MappingEntityUpdate]) { mapping =>
              logger.info(s"here! common/mapping request")
              complete((mappingService ? UpdateMapping(mapping)).mapTo[Either[String, MappingUpdated]])
            }
          }~
          put{
            entity(as[MappingEntityUpdate]){ mapping =>
              logger.info(s"here! common/mapping request")
              complete((mappingService ? CreateMapping(mapping)).mapTo[Either[String, MappingCreated]])
            }
          } ~
          delete{
            logger.info(s"here! common/mapping request")
            complete((mappingService ? DeleteMappingByKeywordAndSystem(keyword, system)).mapTo[Either[String, Int]])
          }
        }
      }
    }
  }

  def heartBeatRoutes: Route = logRequestResult("heartBeatController") {
    pathPrefix("common" / "heartbeat") {
      cors(settings) {
        pathEnd {
          get {
            logger.info(s"here! common/heartbeat request")
            complete(StatusCodes.OK)
          }
        }
      }
    }
  }


  Http(context.system).bindAndHandle(parameterRoutes ~ permissionRoutes ~ routes ~ applicationLogRoutes ~ mappingRoutes ~ heartBeatRoutes, config.getString("http.interface"), config.getInt("http.port"))
    .pipeTo(self)

  override def receive = binding

  private def binding: Receive = {
    case serverBinding@Http.ServerBinding(address) =>
      logger.info(s"Listening on $address")
    case Status.Failure(cause) =>
      logger.error(s"Can't bind to address:port ${cause.getMessage}")
      context.stop(self)
      context.system.terminate()
  }

}

