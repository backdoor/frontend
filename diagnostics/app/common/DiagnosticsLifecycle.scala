package common

import play.api.GlobalSettings
import play.api.mvc.{Handler, RequestHeader}
import conf.Switches

trait DiagnosticsLifecycle extends GlobalSettings with Logging {

  private def scheduleJobs() {
    Jobs.schedule("DiagnosticsLoadJob", "0 * * * * ?") {
      model.diagnostics.alpha.LoadJob.run()
      model.diagnostics.javascript.LoadJob.run()
      model.diagnostics.abtests.UploadJob.run()
      model.diagnostics.analytics.UploadJob.run()
    }
  }

  private def descheduleJobs() {
    Jobs.deschedule("DiagnosticsLoadJob")
  }

  override def onStart(app: play.api.Application) {
    super.onStart(app)
    descheduleJobs()
    scheduleJobs()
  }

  override def onStop(app: play.api.Application) {
    descheduleJobs()
    super.onStop(app)
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    if(Switches.DiagnosticsRequestLogging.isSwitchedOn) {
      log.info(RequestLog(request))
    }
    if(Switches.DiagnosticsJavascriptErrorLogging.isSwitchedOn && request.uri.startsWith("/js.gif")) {
      log.info(diagnostics.JavascriptRequestLog(request))
    }
    super.onRouteRequest(request)
  }
}
