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
import arcsbot.render.BoardRenderer

object Bot {

  /** Entry point. M4:
    *   val jda = JDABuilder.createLight(token).addEventListeners(new GameCommands(...)).build()
    *   register slash commands: /arcs new|join|options|start|board|moves|do|undo|log
    */
  def main(args: Array[String]): Unit = {
    println("arcs-discord bot scaffold — see docs/ROADMAP.md (Milestone 4).")
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

  private def present(gameId: String, session: EngineSession, turn: Turn): Vector[BotEffect] = {
    val publicBoard = renderer.render(session, viewer = None)
    val activeUser  = seats.userForSeat(gameId, turn.seat)
    Vector(
      BotEffect.PostBoard(gameId, publicBoard),
      // interactive controls go to the active player only (ephemeral/DM) so
      // hidden info isn't leaked and only they can act:
      BotEffect.PresentMoves(gameId, turn.seat, activeUser, turn.prompt, turn.options),
      BotEffect.PingActive(gameId, turn.seat, activeUser)
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
  final case class Ephemeral(userId: String, message: String) extends BotEffect
  final case class Error(gameId: String, message: String) extends BotEffect
}
