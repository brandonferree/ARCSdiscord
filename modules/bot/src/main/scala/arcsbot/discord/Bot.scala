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
    val renderer: BoardRenderer =
      if (sys.env.get("RENDER_STUB").contains("1")) BoardRenderer.Stub
      else new PathBRenderer(RenderServer.fromRepo())
    val driver = new TurnDriver(store.sessionOf, renderer, store.tables, store.seats)

    val jda = net.dv8tion.jda.api.JDABuilder
      .createLight(token)
      .addEventListeners(new GameCommands(store, driver))
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

  private def present(gameId: String, session: EngineSession, turn: Turn): Vector[BotEffect] = {
    val publicBoard = renderer.render(session, viewer = None)
    val activeUser  = seats.userForSeat(gameId, turn.seat)
    // Only present options a player can actually choose; Back/Cancel come through
    // as their own buttons (TurnDriver leaves filtering of Info to the bridge).
    Vector(
      BotEffect.PostBoard(gameId, publicBoard),
      // The move controls carry the @-ping for the active player, so a single
      // message both notifies and presents (no separate PingActive needed here).
      BotEffect.PresentMoves(gameId, turn.seat, activeUser, turn.prompt, turn.options)
    )
  }
}

/** What the JDA layer should actually do. Keeps the loop testable without a
  * live Discord connection. */
sealed trait BotEffect
object BotEffect {
  final case class PostBoard(gameId: String, render: arcsbot.render.Render) extends BotEffect
  final case class PresentMoves(gameId: String, seat: Seat, userId: Option[String], prompt: String, options: Seq[MoveOption]) extends BotEffect
  final case class PingActive(gameId: String, seat: Seat, userId: Option[String]) extends BotEffect
  final case class AnnounceWinners(gameId: String, winners: Seq[Seat]) extends BotEffect
  /** A plain public message posted to the table channel (e.g. an undo notice). */
  final case class Notice(gameId: String, message: String) extends BotEffect
  final case class Ephemeral(userId: String, message: String) extends BotEffect
  final case class Error(gameId: String, message: String) extends BotEffect
}
