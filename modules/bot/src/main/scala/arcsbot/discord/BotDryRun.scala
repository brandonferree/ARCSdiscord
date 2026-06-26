package arcsbot.discord

import arcsbot.engine._
import arcsbot.render.BoardRenderer

/* =============================================================================
 * BotDryRun — exercises the M4 turn loop end-to-end WITHOUT Discord.
 *
 *   sbt "bot/runMain arcsbot.discord.BotDryRun"
 *
 * Drives GameStore + TurnDriver (the pure, JDA-free core) through a full game:
 * create table -> 4 players join -> start -> play every decision via the same
 * BotEffect.PresentMoves the JDA layer would render, choosing through
 * TurnDriver.choose with seat enforcement. Asserts the loop reaches a winner and
 * that the guardrails (no seat / wrong turn) reject correctly. Uses the 1×1 stub
 * renderer so no browser is needed. This is the M4 regression check.
 * ===========================================================================*/
object BotDryRun {

  def main(args: Array[String]): Unit = {
    val rng    = new scala.util.Random(args.lift(0).flatMap(_.toIntOption).getOrElse(7))
    val store  = new GameStore()
    val driver = new TurnDriver(store.sessionOf, BoardRenderer.Stub, store.tables, store.seats)

    val channel = "chan-1"
    val users   = Map("Red" -> "u-red", "Yellow" -> "u-yellow", "Blue" -> "u-blue", "White" -> "u-white")

    // -- lifecycle -----------------------------------------------------------
    val table = store.createTable(channel, "dry-run").fold(e => sys.error(s"createTable: $e"), identity)
    require(store.tableForChannel(channel).exists(_.gameId == table.gameId), "table not registered for channel")

    // join in a fixed seating order
    Seq("Red", "Yellow", "Blue", "White").foreach { f =>
      store.join(table.gameId, users(f), f).fold(e => sys.error(s"join $f: $e"), _ => ())
    }
    require(store.seats.seatForUser(table.gameId, users("Blue")).contains(Seat("Blue")), "seat registry wrong")

    // guardrail: a faction already taken
    assert(store.join(table.gameId, "u-intruder", "Red").isLeft, "duplicate faction claim should fail")

    store.start(table.gameId).fold(e => sys.error(s"start: $e"), _ => ())
    assert(store.start(table.gameId).isLeft, "double start should fail")

    // -- play the whole game through the bot's effects -----------------------
    var effects = driver.advance(table.gameId)
    assert(effects.exists(_.isInstanceOf[BotEffect.PostBoard]), "first advance should post a board")

    var turns  = 0
    var winner = Option.empty[Seq[Seat]]
    var checkedGuards = false
    val cap = 50000
    var done = false

    while (!done) {
      currentMoves(effects) match {
        case Some(pm) =>
          // One-time guardrail checks against the live turn:
          if (!checkedGuards) {
            checkedGuards = true
            val active = pm.userId.getOrElse(sys.error("active seat has no user"))
            val other  = users.values.find(_ != active).get
            assert(driver.choose(table.gameId, "u-nobody", 0).exists {
              case _: BotEffect.Ephemeral => true; case _ => false
            }, "a non-seated user must be rejected ephemerally")
            assert(driver.choose(table.gameId, other, 0).exists {
              case _: BotEffect.Ephemeral => true; case _ => false
            }, "a player acting out of turn must be rejected ephemerally")
          }

          val choices = pm.options.filter(_.kind == MoveOption.Choice)
          val pool    = if (choices.nonEmpty) choices else pm.options.filter(_.kind != MoveOption.Info)
          if (pool.isEmpty) sys.error(s"turn for ${pm.seat.factionId} has no actionable option")
          val pick = pool(rng.nextInt(pool.size)).index
          turns += 1
          if (turns > cap) sys.error(s"exceeded $cap turns")
          effects = driver.choose(table.gameId, pm.userId.get, pick)

        case None =>
          effects.collectFirst { case BotEffect.AnnounceWinners(_, w) => w } match {
            case Some(w) => winner = Some(w); done = true
            case None    =>
              effects.collectFirst { case BotEffect.Error(_, m) => m }
                .foreach(m => sys.error(s"unexpected Error effect: $m"))
              val eph = effects.collect { case BotEffect.Ephemeral(_, m) => m }.mkString(" | ")
              sys.error(s"loop stalled at turn $turns with effects: " +
                s"${effects.map(_.getClass.getSimpleName).mkString(",")} — ephemeral: $eph")
          }
      }
    }

    val w = winner.get
    println(s"BotDryRun: GAME OVER after $turns decisions; winner(s): " +
      (if (w.isEmpty) "Humanity" else w.map(_.factionId).mkString(", ")))
    println("BOTDRYRUN PASSED")
  }

  /** The latest PresentMoves in an effect batch, if the game is awaiting a move. */
  private def currentMoves(effects: Vector[BotEffect]): Option[BotEffect.PresentMoves] =
    effects.collectFirst { case pm: BotEffect.PresentMoves => pm }
}
