package arcsbot.discord

/* =============================================================================
 * DiscordOAuth — "Login with Discord" for the M7 web viewer (Phase 3).
 *
 * The web board (`/game/<id>`) is public and spectator-only. A player's HAND is
 * the one piece of private info (PrivateView), and it must be shown ONLY to the
 * authenticated holder of that seat. This module is the security gate: it runs
 * the OAuth2 `identify` flow so a visitor proves which Discord account they are,
 * mints a session cookie, and lets the game route resolve cookie -> Discord id
 * -> seat -> that seat's hand.
 *
 * Boundary: RenderServer owns the single HttpServer but stays user-agnostic. We
 * register the `/auth/` handlers and a side-panel closure on it (RenderServer.handler
 * / RenderServer.sidePanel); all the Discord/session logic lives here. Nothing
 * arcs.* crosses — the panel is built from PrivateView plain data.
 *
 * The ephemeral-tunnel vs redirect-URI collision is contained in ONE place: the
 * `base` we're constructed with. Discord requires the redirect URI to match a
 * value registered in the dev portal exactly, and a free quick-tunnel hostname
 * changes every restart — so we drive the whole dance off `OAUTH_BASE` (default
 * http://localhost:<port>, which Discord allows for local dev). The login link
 * and the callback both live under that one origin; point it at a permanent
 * hostname later and nothing else changes.
 * ===========================================================================*/

import arcsbot.engine.{PrivateCard, PrivateView}
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import net.dv8tion.jda.api.utils.data.DataObject

import java.net.{URI, URLDecoder, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** @param base        the public origin the OAuth dance runs on (no trailing
  *                     slash), e.g. `http://localhost:8787`. The redirect URI is
  *                     `base + "/auth/callback"` and MUST be registered verbatim
  *                     in the Discord dev portal.
  * @param clientId     Discord application Client ID.
  * @param clientSecret Discord application Client Secret (from `.env`, never committed). */
final class DiscordOAuth(base: String, clientId: String, clientSecret: String) {
  import DiscordOAuth._

  private val redirectUri = base.stripSuffix("/") + "/auth/callback"
  private val http        = HttpClient.newHttpClient()
  private val rng         = new SecureRandom()

  // sid (cookie value) -> Discord user id. In-memory: sessions don't survive a
  // bot restart, which is fine — the visitor just re-clicks "Login with Discord".
  private val sessions = new ConcurrentHashMap[String, String]()
  // CSRF state -> the game id the login started from (so callback returns there).
  private val states   = new ConcurrentHashMap[String, String]()

  /** Resolve a session cookie to its Discord user id (None if absent/unknown). */
  def resolve(sid: String): Option[String] = Option(sessions.get(sid))

  /** Where a spectator clicks to authenticate for a given game. */
  def loginUrl(gameId: String): String = s"$base/auth/login?game=${enc(gameId)}"

  /** The handler RenderServer routes the `/auth/` paths to. */
  val handler: HttpHandler = (ex: HttpExchange) =>
    try ex.getRequestURI.getPath match {
      case "/auth/login"    => login(ex)
      case "/auth/callback" => callback(ex)
      case other            => text(ex, 404, s"no auth route: $other")
    } catch { case e: Throwable => text(ex, 500, s"auth error: ${e.getMessage}") }
    finally ex.close()

  // /auth/login?game=<id> -> 302 to Discord's authorize page (identify scope).
  private def login(ex: HttpExchange): Unit = {
    val game  = query(ex).getOrElse("game", "")
    val state = token()
    states.put(state, game)
    val authorize =
      "https://discord.com/api/oauth2/authorize" +
        s"?client_id=${enc(clientId)}&response_type=code&scope=identify" +
        s"&redirect_uri=${enc(redirectUri)}&state=${enc(state)}&prompt=none"
    redirect(ex, authorize, setCookie = None)
  }

  // /auth/callback?code=&state= -> exchange code, fetch the user id, set the
  // session cookie, and bounce back to the game the login started from.
  private def callback(ex: HttpExchange): Unit = {
    val q    = query(ex)
    val code = q.getOrElse("code", "")
    val game = Option(states.remove(q.getOrElse("state", ""))).getOrElse("")
    if (code.isEmpty || game.isEmpty) { text(ex, 400, "Login failed: bad or expired request. Re-click the login link."); return }

    val userId = fetchUserId(code)
    userId match {
      case None => text(ex, 502, "Login failed: Discord did not return your identity. Try again.")
      case Some(uid) =>
        val sid = token()
        sessions.put(sid, uid)
        // HttpOnly so page scripts can't read it; Secure only over https (omit on
        // localhost http). SameSite=Lax is fine — the callback is a top-level nav.
        val secure = if (base.startsWith("https")) "; Secure" else ""
        val cookie = s"${arcsbot.render.RenderServer.SidCookie}=$sid; HttpOnly; Path=/; SameSite=Lax$secure"
        redirect(ex, s"$base/game/${enc(game)}", setCookie = Some(cookie))
    }
  }

  // Exchange the auth code for a token, then GET /users/@me for the id.
  private def fetchUserId(code: String): Option[String] = {
    val form =
      s"client_id=${enc(clientId)}&client_secret=${enc(clientSecret)}" +
        s"&grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(redirectUri)}"
    val tokenReq = HttpRequest.newBuilder(URI.create("https://discord.com/api/oauth2/token"))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(form)).build()
    val tokenRes = http.send(tokenReq, HttpResponse.BodyHandlers.ofString())
    if (tokenRes.statusCode() / 100 != 2) return None
    val accessToken = DataObject.fromJson(tokenRes.body()).getString("access_token", null)
    if (accessToken == null) return None

    val meReq = HttpRequest.newBuilder(URI.create("https://discord.com/api/users/@me"))
      .header("Authorization", s"Bearer $accessToken").GET().build()
    val meRes = http.send(meReq, HttpResponse.BodyHandlers.ofString())
    if (meRes.statusCode() / 100 != 2) return None
    Option(DataObject.fromJson(meRes.body()).getString("id", null))
  }

  private def token(): String = {
    val b = new Array[Byte](18); rng.nextBytes(b)
    java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(b)
  }

  private def query(ex: HttpExchange): Map[String, String] =
    Option(ex.getRequestURI.getRawQuery).getOrElse("").split("&").iterator
      .filter(_.nonEmpty)
      .map(_.split("=", 2))
      .map(a => URLDecoder.decode(a(0), UTF_8) -> (if (a.length > 1) URLDecoder.decode(a(1), UTF_8) else ""))
      .toMap

  private def redirect(ex: HttpExchange, to: String, setCookie: Option[String]): Unit = {
    ex.getResponseHeaders.add("Location", to)
    setCookie.foreach(ex.getResponseHeaders.add("Set-Cookie", _))
    ex.sendResponseHeaders(302, -1)
  }

  private def text(ex: HttpExchange, code: Int, body: String): Unit = {
    val b = body.getBytes(UTF_8)
    ex.getResponseHeaders.add("Content-Type", "text/plain; charset=utf-8")
    ex.sendResponseHeaders(code, b.length.toLong)
    val os = ex.getResponseBody; os.write(b); os.close()
  }
}

object DiscordOAuth {
  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  /** Build from the environment, or None if not configured (spectator-only).
    * Requires `DISCORD_CLIENT_ID` + `DISCORD_CLIENT_SECRET`; the OAuth origin is
    * `OAUTH_BASE` (default `http://localhost:<port>`). */
  def fromEnv(port: Int): Option[DiscordOAuth] =
    for {
      id     <- sys.env.get("DISCORD_CLIENT_ID").map(_.trim).filter(_.nonEmpty)
      secret <- sys.env.get("DISCORD_CLIENT_SECRET").map(_.trim).filter(_.nonEmpty)
    } yield {
      val base = sys.env.get("OAUTH_BASE").map(_.trim).filter(_.nonEmpty)
        .getOrElse(s"http://localhost:$port")
      new DiscordOAuth(base.stripSuffix("/"), id, secret)
    }
}

/* -----------------------------------------------------------------------------
 * HandPanel — renders the per-viewer side panel HTML (asyncti4-style fixed div).
 *
 * `forViewer` is the SECURITY GATE in pure form: the only branch that emits hand
 * cards is one where a session cookie resolved to a Discord id AND that id holds
 * a seat in this game (`handFor` returns the seat's own PrivateView). Everyone
 * else — no cookie, stale cookie, or a spectator who holds no seat — gets the
 * login prompt, which contains no card data. Kept dependency-free and pure so
 * `OAuthSecurityCheck` can assert it directly.
 * -------------------------------------------------------------------------- */
object HandPanel {

  /** Decide and render the side panel for one request.
    * @param sid      the request's session cookie value, if any.
    * @param loginUrl where a spectator clicks to authenticate (this game).
    * @param resolve  sid -> Discord user id (the session map).
    * @param handFor  Discord user id -> that user's hand in THIS game, or None if
    *                 they hold no seat here. */
  def forViewer(
      sid: Option[String],
      loginUrl: String,
      resolve: String => Option[String],
      handFor: String => Option[PrivateView]
  ): String =
    sid.flatMap(resolve).flatMap(handFor) match {
      case Some(view) => panel(view)
      case None        => login(loginUrl)
    }

  // A floating panel pinned to the BOTTOM-RIGHT corner, sized to its contents, so
  // it tucks into the empty space there instead of covering the Court row along
  // the top (which the old full-height right rail did). Cards grow leftward/up
  // from the corner as the hand fills.
  private val shell =
    "position:fixed;right:8px;bottom:8px;z-index:1000;box-sizing:border-box;" +
      "max-width:380px;max-height:calc(100vh - 16px);overflow-y:auto;" +
      "padding:8px 10px;background:rgba(20,16,10,.92);color:#e8dcc0;" +
      "font-family:Consolas,monospace;font-size:12px;border:1px solid #5a4a30;" +
      "border-radius:6px;box-shadow:0 2px 12px rgba(0,0,0,.55)"

  private def panel(v: PrivateView): String = {
    val cards =
      if (v.cards.isEmpty) "<div style='opacity:.6'>No cards in hand.</div>"
      else s"""<div style="display:flex;flex-wrap:wrap;gap:5px;justify-content:flex-end">${v.cards.map(card).mkString}</div>"""
    s"""<div id="arcs-hand-panel" style="$shell">""" +
      s"""<div style="font-weight:bold;margin-bottom:6px;text-align:right">Your hand &mdash; ${esc(v.seat.factionId)} (${v.count})</div>""" +
      cards +
      "</div>"
  }

  // Render each card as its HRF art (the same `/webp2/arcs/images/action/...`
  // assets the board uses). The card name rides in alt/title, so a missing image
  // still shows the label and hovering shows the full name. `data-arcs-hand` marks
  // real private data — OAuthSecurityCheck asserts it is absent from any
  // non-authenticated panel.
  private def card(c: PrivateCard): String =
    s"""<img data-arcs-hand="1" src="${esc(cardImage(c))}" alt="${esc(c.label)}" title="${esc(c.label)}" """ +
      """width="112" loading="lazy" style="display:block;border-radius:5px;border:1px solid #000">"""

  // Action-card art is named `<suit>-<strength>.webp` (construction-3.webp …);
  // Event cards have a single `event.webp`. Suit names ("Construction"…) lowercase
  // straight onto the filenames.
  private def cardImage(c: PrivateCard): String = {
    val suit = c.suit.toLowerCase
    if (c.strength <= 0 || suit == "event") "/webp2/arcs/images/action/event.webp"
    else s"/webp2/arcs/images/action/$suit-${c.strength}.webp"
  }

  private def login(url: String): String =
    s"""<div id="arcs-hand-panel" style="$shell">""" +
      """<div style="font-weight:bold;margin-bottom:6px;text-align:right">Spectating</div>""" +
      s"""<div style="text-align:right"><a href="${esc(url)}" style="color:#9ad">Login with Discord</a></div>""" +
      """<div style="opacity:.6;margin-top:6px;max-width:220px;text-align:right">to see your hand if you hold a seat in this game.</div>""" +
      "</div>"

  private def esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
