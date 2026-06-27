package arcsbot.discord

/* =============================================================================
 * Bot — the JDA Discord application. Knows NOTHING about Arcs rules.
 *
 * STATUS: scaffold. The JDA wiring is commentary until Milestone 4. This file
 * documents the command surface and the turn loop. See docs/DISCORD-UX.md.
 *
 * The bot only ever speaks in terms of:
 *   - a game/table  -> a Discord channel + role (TableRegistry)
 *   - a seat        -> a Discord user  (SeatRegistry)
 *   - a Turn        -> buttons/select for the active player + a board image
 * It pushes player choices into EngineSession and renders whatever comes back.
 * ===========================================================================*/

import arcsbot.engine._
import arcsbot.render.{BoardRenderer, PathBRenderer, RenderServer}

object Bot {

  /** Entry point. Reads the bot token from `DISCORD_TOKEN`; if `DISCORD_GUILD` is
    * set, registers `/arcs` as an (instant) guild command, otherwise globally
    * (can take up to ~1h to appear). The board renderer is Path B (headless
    * Chromium) by default — set `RENDER_STUB=1` to use the 1×1 stub (no browser),
    * and on Windows set `RENDER_BROWSER_CHANNEL=chrome`. Set `ARCS_DB=<path>` to
    * persist games in a SQLite file (resumed on restart); unset = in-memory. */
  def main(args: Array[String]): Unit = {
    val token = sys.env.getOrElse("DISCORD_TOKEN", {
      Console.err.println("DISCORD_TOKEN not set. Export your bot token and re-run.")
      sys.exit(2)
    })

    val store = sys.env.get("ARCS_DB") match {
      case Some(path) =>
        Class.forName("org.sqlite.JDBC")
        val conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path)
        val s = new GameStore(GameRepository.Sql(conn), gid => SqlJournal(gid, conn))
        val warns = s.reload()
        warns.foreach(w => Console.err.println("[reload] " + w))
        println(s"Persisting games to SQLite at $path.")
        s
      case None =>
        println("In-memory games (set ARCS_DB=<path> to persist across restarts).")
        new GameStore()
    }
    // One RenderServer backs both the headless screenshotter (served at `/`) and
    // the M7 web viewer (served at `/game/<id>` to real browsers). Share it so a
    // visitor's full-res, zoomable board comes from the same bundle the bot draws.
    // RENDER_PORT pins the listen port (default 0 = ephemeral) so a Cloudflare
    // Tunnel can point at a stable port across restarts; loopback-only either way.
    val webPort = sys.env.get("RENDER_PORT").flatMap(p => scala.util.Try(p.trim.toInt).toOption).getOrElse(0)
    val server = RenderServer.fromRepo(port = webPort)
    val renderer: BoardRenderer =
      if (sys.env.get("RENDER_STUB").contains("1")) { server.start(); BoardRenderer.Stub }
      else new PathBRenderer(server) // its ctor start()s the server
    // `/game/<id>` -> that game's live board replayed to latest. Unknown or
    // not-yet-started games fall through to a 404. The visitor's own browser does
    // the replay, so this is just the lobby/replay projection — no arcs.* leaks.
    server.games(id => store.table(id).flatMap(t => t.session.map(_.replayBundle(title = t.name))))
    // M7 Phase 5: cheap freshness signal — a game's journal length, no replay. The
    // `/game/<id>` page polls `/rev/<id>` and lights its refresh button when this
    // moves (a committed move or an undo).
    server.revs(id => store.table(id).flatMap(_.session).map(_.journal.size))
    // M7 Phase 2: where players reach the viewer. PUBLIC_BASE_URL (e.g. a
    // Cloudflare Tunnel hostname, no trailing slash) is the shareable base; unset
    // falls back to the loopback server, which only works on this machine.
    val publicBase  = sys.env.get("PUBLIC_BASE_URL").map(_.trim).filter(_.nonEmpty).map(_.stripSuffix("/"))
    val viewerBase  = publicBase.getOrElse(server.baseUrl)
    def viewerUrl(gameId: String): String = s"$viewerBase/game/$gameId"
    println(
      if (publicBase.isDefined) s"Web board viewer public at $viewerBase/game/<id>."
      else s"Web board viewer (local only) on ${server.baseUrl}/game/<id> — set PUBLIC_BASE_URL to share.")

    // M7 Phase 3: "Login with Discord" gate that unlocks the private hand panel on
    // the web viewer. Only active when DISCORD_CLIENT_ID/SECRET are set; otherwise
    // the viewer stays spectator-only exactly as before. The OAuth dance + session
    // map live in DiscordOAuth (bot-side); RenderServer just routes `/auth/*` to it
    // and injects whatever side-panel HTML the closure returns — it never sees a
    // user, a seat, or a hand.
    DiscordOAuth.fromEnv(webPort) match {
      case Some(oauth) =>
        server.handler("/auth/", oauth.handler)
        // sid cookie -> Discord id -> the seat THIS user holds in THIS game -> that
        // seat's own hand. This is the ONLY path that yields hand data; a spectator
        // (no/stale cookie, or holds no seat here) resolves to None and gets the
        // login prompt instead. Cross-seat leakage is impossible: handFor only ever
        // reads the hand of the seat the authenticated user holds.
        val handFor: (String, String) => Option[arcsbot.engine.PrivateView] = (gameId, userId) =>
          store.seats.seatForUser(gameId, userId).flatMap(seat =>
            store.table(gameId).flatMap(_.session).map(_.privateView(seat)))
        server.sidePanel((gameId, sid) =>
          HandPanel.forViewer(sid, oauth.loginUrl(gameId), oauth.resolve, uid => handFor(gameId, uid)))
        println("Discord login enabled — a seated player can see their own hand on the web viewer.")
      case None =>
        println("Discord login NOT configured (set DISCORD_CLIENT_ID/SECRET) — web viewer is spectator-only.")
    }

    val driver = new TurnDriver(store.sessionOf, renderer, store.tables, store.seats)

    val jda = net.dv8tion.jda.api.JDABuilder
      .createLight(token)
      .addEventListeners(new GameCommands(store, driver, viewerUrl, publicBase.isDefined))
      .build()
    jda.awaitReady()

    Option(System.getenv("DISCORD_GUILD")).flatMap(g => Option(jda.getGuildById(g))) match {
      case Some(guild) =>
        guild.updateCommands().addCommands(GameCommands.commandData).queue()
        println(s"Registered /arcs as a guild command in ${guild.getName}.")
      case None =>
        jda.updateCommands().addCommands(GameCommands.commandData).queue()
        println("Registered /arcs globally (may take up to ~1h to appear).")
    }
    println("arcs-discord bot is up.")
  }
}

/** Maps Discord <-> game concepts. Backed by SQL in production
  * (see docs/ARCHITECTURE.md -> Persistence). */
trait TableRegistry {
  def gameIdForChannel(channelId: String): Option[String]
  def channelForGame(gameId: String): Option[String]
  def roleForGame(gameId: String): Option[String]
}

trait SeatRegistry {
  /** Which Discord user holds a seat in this game. */
  def userForSeat(gameId: String, seat: Seat): Option[String]
  /** Which seat (if any) this Discord user holds in this game. */
  def seatForUser(gameId: String, userId: String): Option[Seat]
}

/** The core turn loop, framed as pure intentions the JDA layer executes.
  * Implemented for real in M4; here it documents the contract. */
final class TurnDriver(
  sessions: String => EngineSession,   // gameId -> loaded session
  renderer: BoardRenderer,
  tables: TableRegistry,
  seats: SeatRegistry
) {

  /** Advance a game to its next decision and tell the caller what to post.
    * Called after game creation and after every applied move. */
  def advance(gameId: String): Vector[BotEffect] = {
    val session = sessions(gameId)
    session.pending() match {
      case Outcome.Next(turn)        => present(gameId, session, turn)
      case Outcome.GameOver(winners) => Vector(BotEffect.AnnounceWinners(gameId, winners))
      case Outcome.Rejected(reason)  => Vector(BotEffect.Error(gameId, reason))
    }
  }

  /** Render the current board on demand (for `/arcs board`). */
  def renderCurrent(gameId: String): arcsbot.render.Render =
    renderer.render(sessions(gameId), viewer = None)

  /** Handle a player clicking/typing a choice. */
  def choose(gameId: String, userId: String, optionIndex: Int): Vector[BotEffect] = {
    val session = sessions(gameId)
    seats.seatForUser(gameId, userId) match {
      case None       => Vector(BotEffect.Ephemeral(userId, "You don't hold a seat in this game."))
      case Some(seat) =>
        session.apply(seat, optionIndex) match {
          case Outcome.Next(turn)        => present(gameId, session, turn)
          case Outcome.GameOver(winners) => Vector(BotEffect.AnnounceWinners(gameId, winners))
          case Outcome.Rejected(reason)  => Vector(BotEffect.Ephemeral(userId, s"Rejected: $reason"))
        }
    }
  }

  /** Roll back the requesting player's most recent committed move and re-present
    * the resulting decision. Seat-enforced (any seated player may undo for now;
    * opponent-consent etiquette is a later refinement). */
  def undo(gameId: String, userId: String): Vector[BotEffect] = {
    val session = sessions(gameId)
    seats.seatForUser(gameId, userId) match {
      case None => Vector(BotEffect.Ephemeral(userId, "You don't hold a seat in this game."))
      case Some(_) =>
        if (!session.canUndo) Vector(BotEffect.Ephemeral(userId, "There's nothing to undo yet."))
        else session.undoLast() match {
          case Outcome.Next(turn) =>
            BotEffect.Notice(gameId, s"⮌ <@$userId> undid the last move.") +: present(gameId, session, turn)
          case Outcome.GameOver(winners) => Vector(BotEffect.AnnounceWinners(gameId, winners))
          case Outcome.Rejected(reason)  => Vector(BotEffect.Ephemeral(userId, s"Undo failed: $reason"))
        }
    }
  }

  // Last-seen (act, chapter) per game, for detecting act transitions. Updated on
  // every presented decision; survives within a process (rebuilt silently after a
  // restart on the first decision seen, so reload never re-announces old acts).
  private val progress = scala.collection.mutable.Map.empty[String, (Int, Int)]

  private def present(gameId: String, session: EngineSession, turn: Turn): Vector[BotEffect] = {
    val publicBoard = renderer.render(session, viewer = None)
    val activeUser  = seats.userForSeat(gameId, turn.seat)
    // Only present options a player can actually choose; Back/Cancel come through
    // as their own buttons (TurnDriver leaves filtering of Info to the bridge).
    intermission(gameId, session) ++ Vector(
      BotEffect.PostBoard(gameId, publicBoard),
      // The move controls carry the @-ping for the active player, so a single
      // message both notifies and presents (no separate PingActive needed here).
      BotEffect.PresentMoves(gameId, turn.seat, activeUser, turn.prompt, turn.options)
    )
  }

  /** Emit a one-shot `Intermission` effect when the campaign has crossed into a
    * new act since this game's last decision. No effect on first sighting (game
    * just started, or bot just reloaded) or when the act goes backwards (undo);
    * those only resync the tracker. The board+moves follow, so the act banner
    * lands just before a fresh board for the new act. */
  private def intermission(gameId: String, session: EngineSession): Vector[BotEffect] = {
    val (act, chapter) = session.actChapter
    val prev = progress.get(gameId)
    progress(gameId) = (act, chapter)
    prev match {
      // Acts II and III are the campaign intermissions; entering Act I (0 -> 1) is
      // the game's opening setup, announced by `/arcs start`, not an intermission.
      case Some((prevAct, _)) if act > prevAct && act >= 2 => Vector(BotEffect.Intermission(gameId, act, chapter))
      case _                                               => Vector.empty
    }
  }
}

/** What the JDA layer should actually do. Keeps the loop testable without a
  * live Discord connection. */
sealed trait BotEffect
object BotEffect {
  final case class PostBoard(gameId: String, render: arcsbot.render.Render) extends BotEffect
  final case class PresentMoves(gameId: String, seat: Seat, userId: Option[String], prompt: String, options: Seq[MoveOption]) extends BotEffect
  /** A campaign act boundary just began (`act` is the new act, 2 or 3). The JDA
    * layer posts a role-pinged banner ahead of the new act's board. */
  final case class Intermission(gameId: String, act: Int, chapter: Int) extends BotEffect
  final case class PingActive(gameId: String, seat: Seat, userId: Option[String]) extends BotEffect
  final case class AnnounceWinners(gameId: String, winners: Seq[Seat]) extends BotEffect
  /** A plain public message posted to the table channel (e.g. an undo notice). */
  final case class Notice(gameId: String, message: String) extends BotEffect
  final case class Ephemeral(userId: String, message: String) extends BotEffect
  final case class Error(gameId: String, message: String) extends BotEffect
}
