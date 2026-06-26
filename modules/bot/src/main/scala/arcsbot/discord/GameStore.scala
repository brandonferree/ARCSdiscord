package arcsbot.discord

import arcsbot.engine._
import scala.collection.mutable

/* =============================================================================
 * GameStore — game lifecycle for the Discord bot.
 *
 * Single source of truth for tables (a game = a Discord channel + role), seat
 * claims (faction -> Discord user), and the live EngineSession once started.
 *
 * Persistence is pluggable:
 *   - metadata (channel/name/role/seats/options/started) -> GameRepository
 *   - the action log                                     -> journalFor(gameId)
 * Defaults are in-memory (tests / the dry run). Pass GameRepository.Sql + a
 * SqlJournal factory for durable games that survive a bot restart; call
 * `reload()` once at startup to rebuild live sessions from the store.
 *
 * Live EngineSessions are never persisted directly — they're reconstructed by
 * replaying the journal (EngineSession.load). Coarse-locked because JDA
 * dispatches events on many threads.
 * ===========================================================================*/
final class GameStore(
    repo: GameRepository = new GameRepository.InMemory,
    journalFor: String => Journal = gid => new Journal.InMemory(gid),
    optionIds: Seq[String] = EngineSession.DefaultOptionIds
) {

  /** A game in progress: Discord identity, seat claims (claim order = seating
    * order), and the engine session once started. The in-memory handle; every
    * mutation is mirrored to `repo`. */
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

  val validFactions: Seq[String] = Seq("Red", "Yellow", "Blue", "White")
  // ARCS_DEV=1 is a solo-testing affordance: it lets a single Discord account hold
  // every seat and lowers the start threshold to one, so one tester can drive a
  // whole game (all move-control DMs land in that tester's DMs). Off in production.
  val devMode    = sys.env.get("ARCS_DEV").contains("1")
  val minPlayers = if (devMode) 1 else 3
  val maxPlayers = 4

  private val byGame    = mutable.Map.empty[String, Table]
  private val byChannel = mutable.Map.empty[String, String] // channelId -> gameId
  private val lock      = new AnyRef

  def table(gameId: String): Option[Table]              = lock.synchronized(byGame.get(gameId))
  def tableForChannel(channelId: String): Option[Table] =
    lock.synchronized(byChannel.get(channelId).flatMap(byGame.get))

  /** Rebuild the in-memory cache from the repository — call once at startup so a
    * restarted bot resumes games. Started games have their EngineSession replayed
    * from the journal; failures are reported and skipped (the game stays in the
    * store as un-resumable rather than crashing boot). */
  def reload(): Seq[String] = lock.synchronized {
    val warnings = Vector.newBuilder[String]
    repo.all.foreach { rec =>
      val session =
        if (!rec.started) None
        else try Some(EngineSession.load(journalFor(rec.gameId), rec.seats.map(_._1), rec.optionIds))
        catch { case e: Throwable => warnings += s"could not resume ${rec.gameId}: ${e.getMessage}"; None }
      val t = new Table(rec.gameId, rec.name, rec.channelId, rec.roleId,
        mutable.LinkedHashMap.from(rec.seats), session)
      byGame(rec.gameId) = t
      byChannel(rec.channelId) = rec.gameId
    }
    warnings.result()
  }

  def createTable(channelId: String, name: String): Either[String, Table] = lock.synchronized {
    if (byChannel.contains(channelId)) Left("This channel already hosts a game. Use `/arcs start` or pick another channel.")
    else {
      val gameId = "g-" + java.util.UUID.randomUUID().toString.take(8)
      val t = new Table(gameId, name, channelId, None, mutable.LinkedHashMap.empty, None)
      byGame(gameId) = t
      byChannel(channelId) = gameId
      persist(t)
      Right(t)
    }
  }

  def setRole(gameId: String, roleId: String): Unit = lock.synchronized {
    byGame.get(gameId).foreach { t => t.roleId = Some(roleId); persist(t) }
  }

  def join(gameId: String, userId: String, factionId: String): Either[String, Table] = lock.synchronized {
    byGame.get(gameId) match {
      case None => Left("No game here. Create one with `/arcs new` first.")
      case Some(t) if t.started => Left("This game has already started.")
      case Some(t) =>
        validFactions.find(_.equalsIgnoreCase(factionId)) match {
          case None                                    => Left(s"Unknown faction '$factionId' (pick ${validFactions.mkString("/")}).")
          case Some(f) if t.seats.contains(f)          => Left(s"$f is already taken by <@${t.seats(f)}>.")
          case Some(_) if !devMode && t.seats.values.toSet(userId) => Left("You already hold a seat in this game.")
          case Some(_) if t.seats.size >= maxPlayers   => Left(s"This game is full ($maxPlayers players).")
          case Some(f)                                 => t.seats(f) = userId; persist(t); Right(t)
        }
    }
  }

  def start(gameId: String): Either[String, EngineSession] = lock.synchronized {
    byGame.get(gameId) match {
      case None                          => Left("No game here.")
      case Some(t) if t.started          => Left("This game has already started.")
      case Some(t) if t.seats.size < minPlayers =>
        Left(s"Need at least $minPlayers seated players (have ${t.seats.size}). Use `/arcs join <faction>`.")
      case Some(t) =>
        try {
          val session = EngineSession.create(journalFor(gameId), t.factionIds, optionIds)
          t.session = Some(session)
          persist(t)
          Right(session)
        } catch { case e: Throwable => Left("Couldn't start: " + Option(e.getMessage).getOrElse(e.toString)) }
    }
  }

  private def persist(t: Table): Unit =
    repo.upsert(GameRecord(t.gameId, t.channelId, t.name, t.roleId, t.seats.toSeq, optionIds, t.started))

  // -- registry views (what TurnDriver consumes) -----------------------------

  val tables: TableRegistry = new TableRegistry {
    def gameIdForChannel(channelId: String) = lock.synchronized(byChannel.get(channelId))
    def channelForGame(gameId: String)      = table(gameId).map(_.channelId)
    def roleForGame(gameId: String)         = table(gameId).flatMap(_.roleId)
  }

  val seats: SeatRegistry = new SeatRegistry {
    def userForSeat(gameId: String, seat: Seat) = table(gameId).flatMap(_.seats.get(seat.factionId))
    def seatForUser(gameId: String, userId: String) =
      table(gameId).flatMap { t =>
        val held = t.seats.collect { case (f, u) if u == userId => Seat(f) }.toSeq
        held match {
          case Seq()    => None
          case Seq(one) => Some(one)
          // Solo dev mode: one account holds several seats, so resolve to whichever
          // seat is actually on the clock (else clicks would always act as the first
          // faction and the engine would reject them). Falls back to the first seat.
          case many =>
            val active = t.session.flatMap(_.pending() match {
              case Outcome.Next(turn) => Some(turn.seat)
              case _                  => None
            })
            active.filter(many.contains).orElse(many.headOption)
        }
      }
  }

  def sessionOf(gameId: String): EngineSession =
    table(gameId).flatMap(_.session)
      .getOrElse(throw new NoSuchElementException(s"game $gameId not started"))
}
