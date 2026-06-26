package arcsbot.render

import arcsbot.engine.ReplayBundle
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}

/* =============================================================================
 * RenderServer — a tiny static server with two jobs.
 *
 * HRF's image loader uses the Cache API, which only works in a secure context;
 * http://localhost qualifies (file:// does not), so we serve over loopback. We
 * serve, all from THIS repo — no hrf.im at runtime:
 *   /            -> the host page with the CURRENT screenshot game injected
 *                   (used by PathBRenderer's headless-Chromium screenshot path)
 *   /game/<id>   -> the host page for a SPECIFIC game, replayed to latest, served
 *                   to a real visitor's browser (M7 web viewer — full-res + zoom)
 *   /main.js     -> the Scala.js bundle emitted by `sbt hrfWeb/fastLinkJS`
 *   /webp2/...    -> the local Arcs art mirror under assets/
 *
 * The `/` route serves over 127.0.0.1 for the headless screenshotter; the
 * `/game/<id>` route is what gets exposed publicly (Phase 2, behind a tunnel) so
 * players load the crisp board themselves instead of a re-compressed PNG. The
 * game lookup is injected (`games`) so this module never depends on the bot's
 * GameStore — the bot hands it a `gameId -> Option[ReplayBundle]` closure.
 * ===========================================================================*/
final class RenderServer(
    hostHtml: Path,
    mainJs: Path,
    assetsRoot: Path,
    port: Int = 0,
    bindHost: String = "127.0.0.1"
) {
  @volatile private var currentPage: String = "<!doctype html><title>no render</title>"
  @volatile private var gameLookup: String => Option[ReplayBundle] = _ => None
  private val template: String = new String(Files.readAllBytes(hostHtml), "UTF-8")

  // A web-serving variant of the host page. The screenshot path is served at `/`,
  // so its relative `main.js` and `webp2/` URLs resolve from the root; but a game
  // page lives at `/game/<id>`, where those same relatives would resolve under
  // `/game/`. Rewrite the script src to an absolute `/main.js` and pin the asset
  // base to the origin root via `data-assets="/"` (HRF.param falls back to the
  // #settings data-* attrs), so the page works at any path with no query string.
  // `at` is left unset on purpose: ArcsReplay defaults it to ~latest (Shell.scala),
  // so a bare `/game/<id>` link always renders the newest state.
  private val webTemplate: String =
    template
      .replace("src=\"main.js\"", "src=\"/main.js\"")
      .replace("data-server=\"\"", "data-server=\"\" data-assets=\"/\"")

  private val server: HttpServer = HttpServer.create(new InetSocketAddress(bindHost, port), 0)
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

  /** Wire the `/game/<id>` route: given a gameId, return its current
    * [[ReplayBundle]] (replayed to latest) or `None` for an unknown/unstarted
    * game (served as 404). The bot supplies this from its GameStore; keeping it a
    * closure means the renderer module never sees the bot's types. */
  def games(lookup: String => Option[ReplayBundle]): RenderServer = { gameLookup = lookup; this }

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
      case p if p.startsWith("/game/")  => sendGame(ex, p.stripPrefix("/game/"))
      case _                   => send(ex, 404, "text/plain", s"not found: $path".getBytes("UTF-8"))
    }
  }

  // Serve a specific game's board, replayed to its latest state, for a real
  // visitor's browser. `id` is everything after `/game/` (any trailing path or
  // query is already stripped by getPath); an unknown game is a clean 404.
  private def sendGame(ex: HttpExchange, id: String): Unit =
    gameLookup(id) match {
      case Some(bundle) =>
        send(ex, 200, "text/html; charset=utf-8", bundle.injectInto(webTemplate).getBytes("UTF-8"))
      case None =>
        send(ex, 404, "text/html; charset=utf-8",
          s"<!doctype html><title>no game</title><body>No such game: $id".getBytes("UTF-8"))
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
