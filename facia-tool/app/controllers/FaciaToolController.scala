package controllers

import frontsapi.model._
import frontsapi.model.UpdateList
import jobs.FrontPressJob
import play.api.mvc.{AnyContent, Action, Controller}
import play.api.libs.json._
import common.{FaciaToolMetrics, ExecutionContexts, Logging}
import conf.{Switches, Configuration}
import tools.FaciaApi
import services.{ConfigAgent, ContentApiWrite}
import play.api.libs.ws.Response
import scala.concurrent.Future
import conf.Switches.ContentApiPutSwitch
import services.S3FrontsApi
import model.{NoCache, Cached}


object FaciaToolController extends Controller with Logging with ExecutionContexts {
  implicit val updateListRead = Json.reads[UpdateList]
  implicit val collectionMetaRead = Json.reads[CollectionMetaUpdate]
  implicit val trailWrite = Json.writes[Trail]
  implicit val blockWrite = Json.writes[Block]

  def collectionsEditor() = ExpiringAuthentication { request =>
    val identity = Identity(request).get
    Cached(60) { Ok(views.html.collections(Configuration.environment.stage, Option(identity))) }
  }

  def configEditor() = ExpiringAuthentication { request =>
    val identity = Identity(request).get
    Cached(60) { Ok(views.html.config(Configuration.environment.stage, Option(identity))) }
  }

  def listCollections = AjaxExpiringAuthentication { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    NoCache { Ok(Json.toJson(S3FrontsApi.listCollectionIds)) }
  }

  def getConfig = AjaxExpiringAuthentication { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    NoCache {
      S3FrontsApi.getMasterConfig map { json =>
        Ok(json).as("application/json")
      } getOrElse NotFound
    }
  }

  def readBlock(id: String) = AjaxExpiringAuthentication { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    NoCache {
      S3FrontsApi.getBlock(id) map { json =>
        Ok(json).as("application/json")
      } getOrElse NotFound
    }
  }

  def getConfig(id: String) = AjaxExpiringAuthentication { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    Cached(60) {
      S3FrontsApi.getConfig(id) map {json =>
        Ok(json).as("application/json")
      } getOrElse NotFound
    }
  }

  def publishCollection(id: String) = AjaxExpiringAuthentication { request =>
    val identity = Identity(request).get
    FaciaToolMetrics.DraftPublishCount.increment()
    val block = FaciaApi.publishBlock(id, identity)
    block.foreach(b => pressCollectionId(b.id))
    notifyContentApi(id)
    NoCache(Ok)
  }

  def discardCollection(id: String) = AjaxExpiringAuthentication { request =>
    val identity = Identity(request).get
    FaciaApi.discardBlock(id, identity)
    NoCache(Ok)
  }

  def updateCollectionMeta(id: String): Action[AnyContent] = AjaxExpiringAuthentication { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    NoCache {
      request.body.asJson flatMap(_.asOpt[CollectionMetaUpdate]) map {
        case update: CollectionMetaUpdate => {
          val identity = Identity(request).get
          UpdateActions.updateCollectionMeta(id, update, identity)
          notifyContentApi(id)
          Ok
        }
        case _ => NotFound
      } getOrElse NotFound
    }
  }

  def collectionEdits(): Action[AnyContent] = AjaxExpiringAuthentication { request =>
    FaciaToolMetrics.ApiUsageCount.increment()
    NoCache {
      request.body.asJson flatMap (_.asOpt[Map[String, UpdateList]]) map {
        case update: Map[String, UpdateList] => {
          val identity: Identity = Identity(request).get
          val updatedCollections: Map[String, Block] = update.collect {
            case ("update", updateList) => UpdateActions.updateCollectionList(updateList.id, updateList, identity)
            case ("remove", updateList) => UpdateActions.updateCollectionFilter(updateList.id, updateList, identity)
          }.flatten.map(b => (b.id, b)).toMap

          pressCollectionIds(updatedCollections.keySet)

          if (updatedCollections.nonEmpty)
            Ok(Json.toJson(updatedCollections)).as("application/json")
          else
            NotFound
        }
        case _ => NotFound
      } getOrElse NotFound
    }
  }

  def notifyContentApi(id: String): Option[Future[Response]] =
    if (ContentApiPutSwitch.isSwitchedOn)
      ConfigAgent.getConfig(id)
        .map {config => ContentApiWrite.writeToContentapi(config)}
    else None

  def pressCollectionId(id: String): Unit = pressCollectionIds(Set(id))
  def pressCollectionIds(ids: Set[String]): Unit =
    if (Switches.FaciaToolPressSwitch.isSwitchedOn) {
      FrontPressJob.pressByCollectionIds(ids)
    }

}
