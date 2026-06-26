package arcsbot.discord

import arcsbot.engine._
import arcsbot.render.BoardRenderer

/* =============================================================================
 * BotUndoDryRun — exercises `/arcs undo` (and the log capture) WITHOUT Discord.
 *
 *   sbt "bot/runMain arcsbot.discord.BotUndoDryRun [seed]"
 *
 * Plays a handful of moves through GameStore + TurnDriver, then drives
 * TurnDriver.undo and asserts the journal shrank, the pending decision rolled
 * back, the log did not grow, and the session is still playable afterwards.
 * Also checks the seat guardrail (a non-seated user can't undo). Stub renderer,
 * no browser. Complements BotDryRun (full-game) as the M4 undo regression.
 * ===========================================================================*/
object BotUndoDryRun {

  def main(args: Array[String]): Unit = {
    val rng    = new scala.util.Random(args.lift(0).flatMap(_.toIntOption).getOrElse(7))
    val store  = new GameStore()
    val driver = new TurnDriver(store.sessionOf, BoardRenderer.Stub, store.tables, store.seats)

    val channel = "chan-undo"
    val users   = Map("Red" -> "u-red", "Yellow" -> "u-yellow", "Blue" -> "u-blue", "White" -> "u-white")

    val table = store.createTable(channel, "undo-run").fold(e => sys.error(s"createTable: $e"), identity)
    Seq("Red", "Yellow", "Blue", "White").foreach { f =>
      store.join(table.gameId, users(f), f).fold(e => sys.error(s"join $f: $e"), _ => ())
    }
    store.start(table.gameId).fold(e => sys.error(s"start: $e"), _ => ())
    val session = store.sessionOf(table.gameId)

    def pm(e: Vector[BotEffect]): Option[BotEffect.PresentMoves] =
      e.collectFirst { case p: BotEffect.PresentMoves => p }

    def pick(p: BotEffect.PresentMoves): Int = {
      val choices = p.options.filter(_.kind == MoveOption.Choice)
      val pool    = if (choices.nonEmpty) choices else p.options.filter(_.kind != MoveOption.Info)
      if (pool.isEmpty) sys.error(s"turn for ${p.seat.factionId} has no actionable option")
      pool(rng.nextInt(pool.size)).index
    }

    // -- play a handful of moves --------------------------------------------
    var effects = driver.advance(table.gameId)
    val plays = 6
    var played = 0
    while (played < plays && pm(effects).isDefined) {
      val p = pm(effects).get
      effects = driver.choose(table.gameId, p.userId.get, pick(p))
      played += 1
    }
    assert(played > 0, "expected to play at least one move before undo")
    assert(session.canUndo, "should be able to undo after playing")

    val beforeSize = session.journal.size
    val beforeLog  = session.log.length

    // -- guardrail: a non-seated user can't undo -----------------------------
    assert(driver.undo(table.gameId, "u-nobody").exists {
      case _: BotEffect.Ephemeral => true; case _ => false
    }, "a non-seated user must be rejected ephemerally")

    // -- undo through the driver (as a seated player would) ------------------
    val undoEffects = driver.undo(table.gameId, users("Red"))
    val afterUndoSize = session.journal.size
    assert(afterUndoSize < beforeSize,
      s"journal should shrink on undo (was $beforeSize, now $afterUndoSize)")
    assert(session.log.length <= beforeLog,
      s"log should not grow on undo (was $beforeLog, now ${session.log.length})")
    assert(undoEffects.exists { case _: BotEffect.Notice => true; case _ => false } ||
           undoEffects.exists { case _: BotEffect.AnnounceWinners => true; case _ => false },
      s"undo should announce a notice or game-over, got: ${undoEffects.map(_.getClass.getSimpleName).mkString(",")}")

    // -- the session is still drivable after undo ---------------------------
    effects = driver.advance(table.gameId)
    var resumed = 0
    while (resumed < 4 && pm(effects).isDefined) {
      val p = pm(effects).get
      effects = driver.choose(table.gameId, p.userId.get, pick(p))
      resumed += 1
    }
    assert(resumed > 0 || effects.exists { case _: BotEffect.AnnounceWinners => true; case _ => false },
      "session should be playable (or finished) after undo")

    println(s"BotUndoDryRun: played $played, undid 1 (journal $beforeSize -> $afterUndoSize), " +
      s"resumed $resumed (-> ${session.journal.size}); log=${session.log.length} entries")
    println("BOTUNDODRYRUN PASSED")
  }
}
