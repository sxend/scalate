package org.fusesource.scalate.jersey

import java.io.OutputStream
import java.net.MalformedURLException
import javax.servlet.ServletContext
import com.sun.jersey.api.view.Viewable
import com.sun.jersey.spi.template.ViewProcessor
import com.sun.jersey.server.impl.container.servlet.RequestDispatcherWrapper
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.sun.jersey.api.core.{HttpContext, ResourceConfig}
import org.fusesource.scalate.util.Logging
import com.sun.jersey.api.container.ContainerException
import javax.ws.rs.core.Context

/**
 * @version $Revision : 1.1 $
 */
class SSPTemplateProcessor(@Context resourceConfig: ResourceConfig) extends ViewProcessor[String] with Logging {
  import SSPTemplateProcessor._
  
  @Context
  var servletContext: ServletContext = _
  @Context
  var hc: HttpContext = _
  @Context
  var request: HttpServletRequest = _
  @Context
  var response: HttpServletResponse = _

  val basePath = resourceConfig.getProperties().get("org.fusesource.config.property.SSPTemplatesBasePath") match {
    case path: String => if (path(0) == '/') path else "/" + path
    case _            => ""
  }


  def resolve(requestPath: String): String = {
    if (servletContext == null) {
      warning("No servlet context")
      return null
    }

    try {
      val path = if (basePath.length > 0) basePath + requestPath else requestPath
      
      tryFindPath(path) match {
        case Some(answer) => answer
        case None => 
          /* 
            before Jersey 1.2 paths were often searched as 
              com/acme/foo/SomeClass/index.ssp
            however we prefer to use this naming convention
              com/acme/foo/SomeClass.index.ssp
            so lets add a little hook in here
           */
          val idx = path.lastIndexOf('/')
          if (idx > 1) {
            val newPath = path.substring(0, idx) + "." + path.substring(idx + 1)
            tryFindPath(newPath).getOrElse(null) 
          }
          else {
            null
          }
      }
    } catch {
      case e: MalformedURLException =>
        warning("Tried to load template using Malformed URL. " + e.getMessage)
        null
    }
  }

  def tryFindPath(path: String) = templateSuffixes.map { path + _ }.find { t =>
      fine("Trying to find template: " + t)
      servletContext.getResource(t) ne null
    }

  def writeTo(resolvedPath: String, viewable: Viewable, out: OutputStream): Unit = {
    // Ensure headers are committed
    out.flush()

    val dispatcher = servletContext.getRequestDispatcher(resolvedPath)
    if (dispatcher == null) {
      throw new ContainerException("No request dispatcher for: " + resolvedPath)
    }

    try {
      val wrapper = new RequestDispatcherWrapper(dispatcher, basePath, hc, viewable)
      wrapper.forward(request, response)
      //wrapper.forward(requestInvoker.get(), responseInvoker.get())
    } catch {
      case e: Exception => throw new ContainerException(e)
    }
  }

}

object SSPTemplateProcessor {
  private val templateSuffixes = List("", ".ssp", ".scaml")
}