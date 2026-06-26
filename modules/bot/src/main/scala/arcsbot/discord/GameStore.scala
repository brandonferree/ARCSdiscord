package arcsbot.discord

import arcsbot.engine._
import scala.collection.mutable

/* =============================================================================
 * GameStore — in-memory game lifecycle for the M4 vertical slice.
 *
 * Single source of truth for tables (a game = a Discord channel + role), seat
 * claims (faction -> Discord user), and the live EngineSession once started.
 * Games are lost on restart; SQL persistence (SqlJournal + a `games` table) is a
 * later milestone. Coarse-locked because JDA dispatches events on many threads.
 *
 * Knows nothing about Arcs rules beyond the faction ids — all engine work goes
 * through `arcsbot.engine` (the only module that imports `arcs.*`).
 * ===========================================================================*/
final class GameStore(
    optionIds: Seq[String] = EngineSession.DefaultOptionIds,
    newJournal: String => Journal = gid => new Journal.InMemory(gid)
) {

  /** A game in progress: its Discord identity, seat claims (in claim order,
    * which becomes the seating order), and the engine session once started. */
  final class Table(
      val gameId: String,
      val name: String,
      val channelId: String,
      var roleId: Option[String],
      val seats: mutable.LinkedHashMap[String, String], // factionId -> userId, claim order
      var session: Option[EngineSession]
  ) {
    def factionIds: Seq[String] = seats.keys.toSeq
    def started: Boolean        = session.isDefined
  }

  /** Faction ids that can be claimed (HRF `arcs/meta.scala` factions). */
  val validFactions: Seq[String] = Seq("Red", "Yellow", "Blue", "White")
  val minPlayers = 3
  val maxPlayers = 4

  private val byGame    = mutable.Map.empty[String, Table]
  private val byChannel = mutable.Map.empty[String, String] // channelId -> gameId
  private val lock      = new AnyRef

  def table(gameId: String): Option[Table]              = lock.synchronized(byGame.get(gameId))
  def tableForChannel(channelId: String): Option[Table] =
    lock.synchronized(byChannel.get(channelId).flatMap(byGame.get))

  /** Register a new table for `channelId`. One game per channel. */
  def createTable(channelId: String, name: String): Either[String, Table] = lock.synchronized {
    if (byChannel.contains(channelId)) Left("This channel already hosts a game. Use `/arcs start` or pick another channel.")
    else {
      val gameId = "g-" + java.util.UUID.randomUUID().toString.take(8)
      val t = new Table(gameId, name, channelId, None, mutable.LinkedHashMap.empty, None)
      byGame(gameId) = t
      byChannel(channelId) = gameId
      Right(t)
    }
  }

  def setRole(gameId: String, roleId: String): Unit = lock.synchronized {
    byGame.get(gameId).foreach(_.roleId = Some(roleId))
  }

  /** Claim a seat. Validates the faction id, that the seat is free, that the
    * user isn't already seated, and that the game hasn't started. */
  def join(gameId: String, userId: String, factionId: String): Either[String, Table] = lock.synchronized {
    byGame.get(gameId) match {
      case None => Left("No game here. Create one with `/arcs new` first.")
      case Some(t) if t.started => Left("This game has already started.")
      case Some(t) =>
        val faction = validFactions.find(_.equalsIgnoreCase(factionId))
        faction match {
          case None                                       => Left(s"Unknown faction '$factionId' (pick ${validFactions.mkString("/")}).")
          case Some(f) if t.seats.contains(f)             => Left(s"$f is already taken by <@${t.seats(f)}>.")
          case Some(_) if t.seats.values.toSet(userId)    => Left("You already hold a seat in this game.")
          case Some(_) if t.seats.size >= maxPlayers      => Left(s"This game is full ($maxPlayers players).")
          case Some(f)                                    => t.seats(f) = userId; Right(t)
        }
    }
  }

  /** Validate the seating + build the EngineSession, driving to the first
    * decision. `EngineSession.create` validates the faction combination and
    * throws on an invalid setup, which we surface as a Left. */
  def start(gameId: String): Either[String, EngineSession] = lock.synchronized {
    byGame.get(gameId) match {
      case None                          => Left("No game here.")
      case Some(t) if t.started          => Left("This game has already started.")
      case Some(t) if t.seats.size < minPlayers =>
        Left(s"Need at least $minPlayers seated players (have ${t.seats.size}). Use `/arcs join <faction>`.")
      case Some(t) =>
        try {
          val session = EngineSession.create(newJournal(gameId), t.factionIds, optionIds)
          t.session = Some(session)
          Right(session)
        } catch { case e: Throwable => Left("Couldn't start: " + Option(e.getMessage).getOrElse(e.toString)) }
    }
  }

  // -- registry views (what TurnDriver consumes) -----------------------------

  val tables: TableRegistry = new TableRegistry {
    def gameIdForChannel(channelId: String) = lock.synchronized(byChannel.get(channelId))
    def channelForGame(gameId: String)      = table(gameId).map(_.channelId)
    def roleForGame(gameId: String)         = table(gameId).flatMap(_.roleId)
  }

  val seats: SeatRegistry = new SeatRegistry {
    def userForSeat(gameId: String, seat: Seat) = table(gameId).flatMap(_.seats.get(seat.factionId))
    def seatForUser(gameId: String, userId: String) =
      table(gameId).flatMap(_.seats.collectFirst { case (f, u) if u == userId => Seat(f) })
  }

  /** Session lookup for `TurnDriver` (throws if the game isn't started — callers
    * only advance/choose on started games). */
  def sessionOf(gameId: String): EngineSession =
    table(gameId).flatMap(_.session)
      .getOrElse(throw new NoSuchElementException(s"game $gameId not started"))
}
