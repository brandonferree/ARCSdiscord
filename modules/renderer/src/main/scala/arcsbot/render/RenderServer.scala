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
  // A cheap monotonic-ish "revision" for a game (its journal length): changes
  // whenever a move is committed or undone, WITHOUT replaying the game. The
  // `/game/<id>` page bakes in the rev at load and polls `/rev/<id>`; when it
  // differs, the injected refresh button lights up (M7 Phase 5 freshness).
  @volatile private var revLookup: String => Option[Long] = _ => None
  // Per-request side-panel HTML for `/game/<id>`, injected before </body>. The bot
  // supplies this; given the game id and the request's session cookie (if any) it
  // returns the HTML to show THIS visitor — the authenticated seat-holder's hand,
  // or a "Login with Discord" prompt for everyone else. Default = nothing, so the
  // screenshot path and an un-wired server behave exactly as before. RenderServer
  // stays dumb about users/seats: it only forwards the cookie and injects the
  // string it gets back, so it can never leak private info on its own.
  @volatile private var sidePanelFn: (String, Option[String]) => String = (_, _) => ""
  // Extra request handlers the bot registers (e.g. the OAuth `/auth/*` routes),
  // tried by path prefix before the built-in routes. Keeps the single HttpServer
  // user-agnostic: the OAuth dance lives in the bot, wired in as a closure.
  @volatile private var handlers: List[(String, HttpHandler)] = Nil
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
  // A real thread pool (not the single-threaded default): the SSE `/events/<id>`
  // handler holds its thread open for the life of the connection, so it must not
  // run on a shared dispatch thread or it would starve every other request. Daemon
  // threads so they never keep the JVM alive. All handlers are read-only and the
  // game/rev lookups are synchronized in GameStore, so concurrent serving is safe.
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory {
    def newThread(r: Runnable): Thread = { val t = new Thread(r, "render-http"); t.setDaemon(true); t }
  }))

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

  /** Wire the cheap freshness signal for `/rev/<id>`: a game's current revision
    * (its journal length), without replaying it. The bot supplies this from its
    * GameStore so the renderer never sees a session. */
  def revs(lookup: String => Option[Long]): RenderServer = { revLookup = lookup; this }

  /** Wire the per-visitor side panel for `/game/<id>`. Given the game id and the
    * request's `arcs_sid` cookie (if present), the bot returns the HTML to inject
    * before `</body>` — the seat-holder's hand panel when the cookie authenticates
    * a seated player in THIS game, otherwise a login prompt. The renderer never
    * sees who that is; it just injects the returned string. */
  def sidePanel(provide: (String, Option[String]) => String): RenderServer = { sidePanelFn = provide; this }

  /** Register an extra handler for all paths under `prefix` (e.g. `/auth/`). These
    * are matched before the built-in routes, so the bot can own `/auth/login` and
    * `/auth/callback` (the OAuth dance) without the renderer knowing about Discord
    * or sessions. Last registration for a given prefix wins. */
  def handler(prefix: String, h: HttpHandler): RenderServer = { handlers = (prefix, h) :: handlers; this }

  def start(): RenderServer = { server.start(); this }
  def stop(): Unit = server.stop(0)
  def baseUrl: String = s"http://127.0.0.1:${server.getAddress.getPort}"

  private def route(ex: HttpExchange): Unit = {
    val path = ex.getRequestURI.getPath
    // Bot-registered handlers (OAuth) win over the built-in static routes.
    handlers.find { case (prefix, _) => path == prefix.stripSuffix("/") || path.startsWith(prefix) } match {
      case Some((_, h)) => h.handle(ex); return
      case None         =>
    }
    path match {
      case "/" | "/index.html" => send(ex, 200, "text/html; charset=utf-8", currentPage.getBytes("UTF-8"))
      case "/main.js"          => sendFile(ex, mainJs, "application/javascript")
      case "/main.js.map"      => sendFile(ex, mainJs.resolveSibling("main.js.map"), "application/json")
      case p if p.startsWith("/webp2/") => sendAsset(ex, p)
      case p if p.startsWith("/rev/")    => sendRev(ex, p.stripPrefix("/rev/"))
      case p if p.startsWith("/events/") => sendEvents(ex, p.stripPrefix("/events/"))
      case p if p.startsWith("/game/")   => sendGame(ex, p.stripPrefix("/game/"))
      case _                   => send(ex, 404, "text/plain", s"not found: $path".getBytes("UTF-8"))
    }
  }

  // Serve a specific game's board, replayed to its latest state, for a real
  // visitor's browser. `id` is everything after `/game/` (any trailing path or
  // query is already stripped by getPath); an unknown game is a clean 404.
  private def sendGame(ex: HttpExchange, id: String): Unit =
    gameLookup(id) match {
      case Some(bundle) =>
        // Inject the per-visitor side panel (hand or login prompt) before </body>.
        // sidePanelFn decides what this cookie is allowed to see — never the renderer.
        val panel = sidePanelFn(id, cookie(ex, RenderServer.SidCookie))
        val fresh = freshness(id, revLookup(id).getOrElse(-1L))
        val page  = bundle.injectInto(webTemplate).replace("</body>", panel + fresh + "\n</body>")
        send(ex, 200, "text/html; charset=utf-8", page.getBytes("UTF-8"))
      case None =>
        // Escape the reflected id: it's the raw (url-decoded) path segment, so an
        // unescaped reflection here is a reflected-XSS sink on the public origin.
        send(ex, 404, "text/html; charset=utf-8",
          s"<!doctype html><title>no game</title><body>No such game: ${escHtml(id)}".getBytes("UTF-8"))
    }

  // M7 Phase 5: the cheap freshness probe. Returns the game's current revision as
  // plain text (or 404), no-store so the poll always sees the live value. The open
  // `/game/<id>` page compares this against the rev baked in at load.
  private def sendRev(ex: HttpExchange, id: String): Unit =
    revLookup(id) match {
      case Some(rev) =>
        ex.getResponseHeaders.add("Cache-Control", "no-store")
        send(ex, 200, "text/plain; charset=utf-8", rev.toString.getBytes("UTF-8"))
      case None => send(ex, 404, "text/plain", "no game".getBytes("UTF-8"))
    }

  // M7 Phase 5 step 2: Server-Sent Events. Holds the connection open and pushes
  // `data: <rev>` the instant a game's revision changes (server-side checks the
  // cheap journal-length lookup each second), so a viewer updates immediately
  // instead of waiting out a client poll. A comment ping keeps idle proxies from
  // dropping the stream. Runs on a pool thread (see the executor above); returns
  // when the client disconnects (write throws) or the game disappears.
  private def sendEvents(ex: HttpExchange, id: String): Unit = {
    if (revLookup(id).isEmpty) { send(ex, 404, "text/plain", "no game".getBytes("UTF-8")); return }
    val h = ex.getResponseHeaders
    h.add("Content-Type", "text/event-stream; charset=utf-8")
    h.add("Cache-Control", "no-store")
    h.add("Connection", "keep-alive")
    ex.sendResponseHeaders(200, 0) // 0 = chunked/streaming, length unknown
    val os = ex.getResponseBody
    def write(s: String): Unit = { os.write(s.getBytes("UTF-8")); os.flush() }
    try {
      var last = revLookup(id).getOrElse(-1L)
      write(s"retry: 3000\ndata: $last\n\n")
      var ticks = 0
      var live  = true
      while (live) {
        Thread.sleep(1000)
        revLookup(id) match {
          case Some(cur) if cur != last => last = cur; write(s"data: $cur\n\n")
          case Some(_)                  => ticks += 1; if (ticks % 15 == 0) write(": ping\n\n")
          case None                     => write("event: gone\ndata: 0\n\n"); live = false
        }
      }
    } catch { case _: java.io.IOException | _: InterruptedException => () } // client gone / shutdown
  }

  // A small fixed control (refresh + auto toggle) plus a live SSE subscription
  // injected into every game page. It subscribes to `/events/<id>`; when the
  // pushed revision passes the one baked in at load, it reloads to the new board —
  // but first calls `arcsSaveView()` (exported by the Scala.js app) to stash each
  // board pane's zoom/pan in sessionStorage, which the next boot restores, so the
  // refresh keeps the viewer's view instead of resetting to fit. The "Auto" toggle
  // (localStorage) pauses auto-reload; then the button just lights up to click.
  // Falls back to a /rev poll if the browser lacks EventSource. (Phase 5 step 2.)
  private def freshness(id: String, rev0: Long): String = {
    val idJs = "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    s"""<script>(function(){
  var id=$idJs, rev0=$rev0, btn, auto, bs=
    'padding:6px 10px;background:rgba(20,16,10,.92);color:#e8dcc0;border:1px solid #5a4a30;'+
    'border-radius:6px;cursor:pointer;font:inherit';
  function autoOn(){ try{return localStorage.getItem('arcs-auto')!=='off';}catch(e){return true;} }
  function setAuto(v){ try{localStorage.setItem('arcs-auto',v?'on':'off');}catch(e){} auto.textContent='Auto: '+(v?'ON':'OFF'); }
  function saveView(){ try{ if(window.arcsSaveView) window.arcsSaveView(); }catch(e){} }
  function reload(){ saveView(); location.reload(); }
  function mk(){
    var wrap=document.createElement('div');
    wrap.style.cssText='position:fixed;left:8px;bottom:8px;z-index:1001;display:flex;gap:6px;'+
      'font-family:Consolas,monospace;font-size:12px';
    btn=document.createElement('button'); btn.id='arcs-refresh'; btn.textContent='↻ Refresh';
    btn.style.cssText=bs; btn.onclick=reload;
    auto=document.createElement('button'); auto.id='arcs-auto'; auto.style.cssText=bs;
    auto.onclick=function(){setAuto(!autoOn());};
    setAuto(autoOn());
    wrap.appendChild(btn); wrap.appendChild(auto); document.body.appendChild(wrap);
  }
  function update(n){
    if(isNaN(n)||n===rev0||!btn)return;
    if(autoOn()){ btn.textContent='↻ Updating…'; setTimeout(reload,250); }
    else { btn.textContent='↻ New moves — refresh'; btn.style.background='#7a5a1a'; btn.style.fontWeight='bold'; }
  }
  function connect(){
    if(window.EventSource){
      var es=new EventSource('/events/'+encodeURIComponent(id));
      es.onmessage=function(e){ update(parseInt(e.data,10)); };
    } else { // legacy fallback: poll
      setInterval(function(){
        fetch('/rev/'+encodeURIComponent(id),{cache:'no-store'}).then(function(r){return r.text();})
          .then(function(t){ update(parseInt(t,10)); }).catch(function(){});
      },5000);
    }
  }
  if(document.body){mk();}else{window.addEventListener('DOMContentLoaded',mk);}
  connect();
})();</script>"""
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

  // Read one cookie value from the request's Cookie header (null-safe, trims OWS).
  private def cookie(ex: HttpExchange, name: String): Option[String] =
    Option(ex.getRequestHeaders.getFirst("Cookie")).flatMap { raw =>
      raw.split(";").iterator.map(_.trim).collectFirst {
        case kv if kv.startsWith(name + "=") => kv.drop(name.length + 1)
      }
    }

  // Minimal HTML-escape for any user-controlled value reflected into an HTML body
  // (e.g. the requested game id on a 404). Keeps the renderer's reflections inert.
  private def escHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

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
  /** The HttpOnly session-cookie name the bot's `/auth/callback` sets and the
    * `/game/<id>` route reads to resolve a visitor's seat. Shared so both sides
    * agree on the name. */
  val SidCookie: String = "arcs_sid"

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
