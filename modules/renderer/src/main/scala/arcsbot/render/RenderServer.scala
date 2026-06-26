package arcsbot.render

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}

/* =============================================================================
 * RenderServer — a tiny localhost static server for the headless board renderer.
 *
 * HRF's image loader uses the Cache API, which only works in a secure context;
 * http://localhost qualifies (file:// does not), so we serve over loopback. We
 * serve three things, all from THIS repo — no hrf.im at runtime:
 *   /            -> the host page with the current game's ReplayBundle injected
 *   /main.js     -> the Scala.js bundle emitted by `sbt hrfWeb/fastLinkJS`
 *   /webp2/...    -> the local Arcs art mirror under assets/
 * ===========================================================================*/
final class RenderServer(
    hostHtml: Path,
    mainJs: Path,
    assetsRoot: Path,
    port: Int = 0
) {
  @volatile private var currentPage: String = "<!doctype html><title>no render</title>"
  private val template: String = new String(Files.readAllBytes(hostHtml), "UTF-8")

  private val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0)
  server.createContext("/", new HttpHandler {
    def handle(ex: HttpExchange): Unit =
      try route(ex) catch { case e: Throwable => fail(ex, e) } finally ex.close()
  })
  server.setExecutor(null)

  /** Set the page served at `/` for the next navigation (host page + injected
    * lobby/replay). Returns this server for chaining. */
  def page(html: String): RenderServer = { currentPage = html; this }

  /** The host-page template (hrf-web/host/index.html) for ReplayBundle.injectInto. */
  def hostTemplate: String = template

  def start(): RenderServer = { server.start(); this }
  def stop(): Unit = server.stop(0)
  def baseUrl: String = s"http://127.0.0.1:${server.getAddress.getPort}"

  private def route(ex: HttpExchange): Unit = {
    val path = ex.getRequestURI.getPath
    path match {
      case "/" | "/index.html" => send(ex, 200, "text/html; charset=utf-8", currentPage.getBytes("UTF-8"))
      case "/main.js"          => sendFile(ex, mainJs, "application/javascript")
      case "/main.js.map"      => sendFile(ex, mainJs.resolveSibling("main.js.map"), "application/json")
      case p if p.startsWith("/webp2/") => sendAsset(ex, p)
      case _                   => send(ex, 404, "text/plain", s"not found: $path".getBytes("UTF-8"))
    }
  }

  // Serve an art file, guarding against path traversal outside the mirror.
  private def sendAsset(ex: HttpExchange, urlPath: String): Unit = {
    val rel  = urlPath.stripPrefix("/")
    val file = assetsRoot.resolve(rel).normalize()
    if (!file.startsWith(assetsRoot.normalize()) || !Files.isRegularFile(file))
      send(ex, 404, "text/plain", s"no asset: $urlPath".getBytes("UTF-8"))
    else
      sendFile(ex, file, contentType(file))
  }

  private def contentType(p: Path): String = {
    val n = p.getFileName.toString
    if (n.endsWith(".webp")) "image/webp"
    else if (n.endsWith(".png")) "image/png"
    else if (n.endsWith(".js")) "application/javascript"
    else "application/octet-stream"
  }

  private def sendFile(ex: HttpExchange, file: Path, ct: String): Unit =
    if (!Files.isRegularFile(file))
      send(ex, 404, "text/plain", s"missing: $file".getBytes("UTF-8"))
    else
      send(ex, 200, ct, Files.readAllBytes(file))

  private def send(ex: HttpExchange, code: Int, ct: String, body: Array[Byte]): Unit = {
    ex.getResponseHeaders.add("Content-Type", ct)
    ex.sendResponseHeaders(code, body.length.toLong)
    val os = ex.getResponseBody; os.write(body); os.close()
  }

  private def fail(ex: HttpExchange, e: Throwable): Unit =
    send(ex, 500, "text/plain", s"render-server error: ${e.getMessage}".getBytes("UTF-8"))
}

object RenderServer {
  /** Locate the repo-relative pieces from a base dir (default: the process CWD,
    * which sbt sets to the build root for `renderer/run`). */
  def fromRepo(base: Path = Paths.get(".").toAbsolutePath.normalize(), port: Int = 0): RenderServer =
    new RenderServer(
      hostHtml   = base.resolve("hrf-web/host/index.html"),
      mainJs     = base.resolve("hrf-web/target/scala-2.13/hrf-web-fastopt/main.js"),
      assetsRoot = base.resolve("assets"),
      port       = port
    )
}
