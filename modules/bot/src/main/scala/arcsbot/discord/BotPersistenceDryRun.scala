package arcsbot.discord

import arcsbot.engine._
import arcsbot.render.BoardRenderer
import java.sql.{Connection, DriverManager}
import java.nio.file.Files

/* =============================================================================
 * BotPersistenceDryRun — proves SQL-backed games survive a bot restart.
 *
 *   sbt "bot/runMain arcsbot.discord.BotPersistenceDryRun"
 *
 * Session 1: on a SQLite file, create a table, seat 4 players, start, play ~80
 * decisions, snapshot the pending turn + journal size, close the connection.
 * Session 2: open a FRESH GameStore on the same file, reload(), assert the game
 * resumes to the identical pending state, then finish it to a winner. No Discord.
 * ===========================================================================*/
object BotPersistenceDryRun {

  def main(args: Array[String]): Unit = {
    Class.forName("org.sqlite.JDBC")
    val rng   = new scala.util.Random(args.lift(0).flatMap(_.toIntOption).getOrElse(7))
    val dbFile = Files.createTempFile("arcs-persist", ".db")
    Files.deleteIfExists(dbFile) // let SQLite create it fresh
    val url   = "jdbc:sqlite:" + dbFile.toString.replace('\\', '/')
    val users = Map("Red" -> "u-red", "Yellow" -> "u-yellow", "Blue" -> "u-blue", "White" -> "u-white")

    try {
      // -- session 1: create, seat, start, play partway --------------------
      val conn1  = DriverManager.getConnection(url)
      val store1 = sqlStore(conn1)
      val gameId = store1.createTable("chan-1", "persist").fold(e => sys.error(s"createTable: $e"), _.gameId)
      Seq("Red", "Yellow", "Blue", "White").foreach(f =>
        store1.join(gameId, users(f), f).fold(e => sys.error(s"join $f: $e"), _ => ()))
      store1.start(gameId).fold(e => sys.error(s"start: $e"), _ => ())

      val driver1 = new TurnDriver(store1.sessionOf, BoardRenderer.Stub, store1.tables, store1.seats)
      val played1 = play(driver1, gameId, rng, maxSteps = 80, driver1.advance(gameId))
      if (played1._3) sys.error("game ended within 80 decisions — raise the cutoff to test mid-game resume")

      val before     = store1.sessionOf(gameId).pending()
      val beforeJrnl = store1.sessionOf(gameId).journal.size
      val beforeTurn = before match {
        case Outcome.Next(t) => t
        case other           => sys.error(s"expected a pending turn before restart, got $other")
      }
      println(s"session 1: played ${played1._2} decisions; journal=$beforeJrnl; " +
        s"pending ${beforeTurn.seat.factionId} (${beforeTurn.options.size} options)")
      conn1.close() // "shut the bot down"

      // -- session 2: reopen, reload, verify resume, finish ----------------
      val conn2  = DriverManager.getConnection(url)
      val store2 = sqlStore(conn2)
      val warns  = store2.reload()
      assert(warns.isEmpty, s"reload warnings: ${warns.mkString("; ")}")
      assert(store2.tableForChannel("chan-1").exists(_.gameId == gameId), "channel mapping not restored")
      assert(store2.seats.seatForUser(gameId, users("Blue")).contains(Seat("Blue")), "seat claims not restored")

      val after = store2.sessionOf(gameId).pending() match {
        case Outcome.Next(t) => t
        case other           => sys.error(s"resumed game not at a pending turn: $other")
      }
      assert(store2.sessionOf(gameId).journal.size == beforeJrnl, "journal size changed across restart")
      assert(after.seat == beforeTurn.seat, s"resumed seat ${after.seat} != ${beforeTurn.seat}")
      assert(after.options.size == beforeTurn.options.size, "resumed option count differs")
      println(s"session 2: resumed at ${after.seat.factionId} (${after.options.size} options) — state matches")

      val driver2 = new TurnDriver(store2.sessionOf, BoardRenderer.Stub, store2.tables, store2.seats)
      val (finalEffects, _, over) = play(driver2, gameId, rng, maxSteps = 50000, driver2.advance(gameId))
      if (!over) sys.error("game did not finish after resume")
      val winners = finalEffects.collectFirst { case BotEffect.AnnounceWinners(_, w) => w }.getOrElse(Nil)
      conn2.close()
      println(s"BotPersistenceDryRun: resumed game ran to GAME OVER; winner(s): " +
        (if (winners.isEmpty) "Humanity" else winners.map(_.factionId).mkString(", ")))
      println("BOTPERSISTENCE PASSED")
    } finally Files.deleteIfExists(dbFile)
  }

  private def sqlStore(conn: Connection): GameStore =
    new GameStore(
      repo       = GameRepository.Sql(conn),
      journalFor = gid => SqlJournal(gid, conn)
    )

  /** Play up to `maxSteps` decisions from `effects0`, choosing a random legal
    * option for the active seat each turn. Returns (lastEffects, decisionsPlayed,
    * gameOver?). Stops early at the cutoff (mid-game) or at game over. */
  private def play(driver: TurnDriver, gameId: String, rng: scala.util.Random,
                   maxSteps: Int, effects0: Vector[BotEffect]): (Vector[BotEffect], Int, Boolean) = {
    var effects = effects0
    var steps   = 0
    var over    = false
    var go      = true
    while (go) {
      effects.collectFirst { case pm: BotEffect.PresentMoves => pm } match {
        case Some(pm) if steps < maxSteps =>
          val choices = pm.options.filter(_.kind == MoveOption.Choice)
          val pool    = if (choices.nonEmpty) choices else pm.options.filter(_.kind != MoveOption.Info)
          if (pool.isEmpty) sys.error(s"no actionable option for ${pm.seat.factionId}")
          effects = driver.choose(gameId, pm.userId.get, pool(rng.nextInt(pool.size)).index)
          steps += 1
        case Some(_) => go = false // hit the step cutoff mid-game
        case None =>
          if (effects.exists(_.isInstanceOf[BotEffect.AnnounceWinners])) over = true
          go = false
      }
    }
    (effects, steps, over)
  }
}
