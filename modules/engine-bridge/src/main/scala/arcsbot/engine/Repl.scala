package arcsbot.engine

/** A tiny harness for the engine bridge (M2 issue #8): drive an Arcs game purely
  * through the Discord-facing [[EngineSession]] API (no `arcs.*` here), proving
  * `create` / `pending` / `apply` / `load` work end-to-end without a browser.
  *
  * Modes:
  *   selftest (default) — auto-play a HostTest game (ends at act-1 setup) by
  *                        picking a random legal option each turn, then verify the
  *                        journal replays (load) back to the same game-over.
  *   sqltest            — same, persisted through `SqlJournal` (+ conflict/reload).
  *   m5probe [seed]     — auto-play a REAL full Act I→III campaign (`NoFate`) to
  *                        game-over and verify replay. No seed → seeds 1–8. This
  *                        is the M5 regression check (Fates / multi-act flow).
  *   play               — interactive: prints the prompt + numbered options and
  *                        reads a choice index from stdin.
  *
  * Run: sbt "engineBridge/runMain arcsbot.engine.Repl [selftest|sqltest|m5probe|play]"
  */
object Repl {

  private val Factions = Seq("Red", "White", "Blue", "Yellow")

  /** Options for the self-test: `HostTest` ends the game at act-1 setup
    * (game-blight.scala) so a full game completes without the M5 intermission /
    * hidden-info Fate selection — enough to exercise create/apply/load. */
  private val SelfTestOptions = Seq("HostTest", "Act1Only", "RandomPlayerOrder", "RandomizePlanetResources")

  def main(args: Array[String]): Unit =
    args.headOption.getOrElse("selftest") match {
      case "play"     => play()
      case "selftest" => selftest()
      case "sqltest"  => sqltest()
      case "m5probe"  => args.lift(1).flatMap(_.toIntOption) match {
        case Some(s) => m5probe(s)
        case None    => 1.to(8).foreach(m5probe)
      }
      case other      => println(s"unknown mode '$other' (use: selftest | sqltest | m5probe | play)")
    }

  // -- SQL journal test ------------------------------------------------------

  /** Exercise the SQL-backed journal (#5): play a full game persisting to an
    * embedded SQLite DB, verify optimistic-concurrency conflict on a stale
    * append, then reopen the journal on the same DB and replay it. */
  private def sqltest(): Unit = {
    Class.forName("org.sqlite.JDBC")
    val conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")
    try {
      val rng = new scala.util.Random(99)
      val journal = SqlJournal("game-1", conn)
      val session = EngineSession.create(journal, Factions, SelfTestOptions)

      var outcome = session.pending()
      var turns = 0
      var stop = false
      while (!stop) outcome match {
        case Outcome.Next(turn)       => turns += 1; outcome = session.apply(turn.seat, pickRandom(turn, rng))
        case Outcome.GameOver(_)      => stop = true
        case Outcome.Rejected(reason) => sys.error(s"sqltest rejection: $reason")
      }
      println(s"sql play OK: $turns decisions persisted, journal = ${journal.size} SQL rows")

      // Optimistic concurrency: appending at an already-used index must conflict.
      journal.append(0L, "DUPLICATE") match {
        case Left(c)  => println(s"conflict OK: stale append at idx 0 rejected ($c)")
        case Right(_) => sys.error("expected a conflict appending at idx 0")
      }

      // Reopen on the same DB and replay.
      val reopened = EngineSession.load(SqlJournal("game-1", conn), Factions, SelfTestOptions)
      reopened.pending() match {
        case Outcome.GameOver(_) => println("sql reload OK: reopened journal replayed to game-over")
        case other               => sys.error(s"sql reload MISMATCH: $other")
      }
      println("SQLTEST PASSED")
    } finally conn.close()
  }

  // -- M5 probe --------------------------------------------------------------

  /** Drive a REAL campaign (no HostTest) with random legal choices until the
    * bridge stops. Prints where it stops (GameOver vs Rejected) and the journal
    * length, so we can see exactly what continuation the M5 intermission /
    * Fate-selection produces. Run with BRIDGE_DEBUG=1 for the full stack trace. */
  private def m5probe(seed: Int = 7): Unit = {
    val opts = Seq("NoFate", "RandomPlayerOrder", "RandomizePlanetResources")
    val rng = new scala.util.Random(seed)
    val journal = new Journal.InMemory("m5probe")
    val session = EngineSession.create(journal, Factions, opts)

    var outcome = session.pending()
    var turns = 0
    var stop = false
    while (!stop) outcome match {
      case Outcome.Next(turn) =>
        val choices = turn.options.filter(_.kind == MoveOption.Choice)
        if (choices.isEmpty) {
          // No actionable option: upstream's end-of-act "under development"
          // terminal screen (YYSelectObjects with withRule(_ => false) + a
          // dev-only proceed). For production HRF this is where the game stops.
          println(s"m5probe: reached TERMINAL screen after $turns decisions, journal=${journal.size}")
          println(s"  seat=${turn.seat.factionId} prompt=${turn.prompt.take(60)} options=${turn.options.map(o => o.kind).distinct.mkString(",")}")
          stop = true
        } else {
          turns += 1
          if (turns > 50000) sys.error("not progressing")
          outcome = session.apply(turn.seat, choices(rng.nextInt(choices.size)).index)
        }
      case Outcome.GameOver(winners) =>
        val w = if (winners.isEmpty) "Humanity" else winners.map(_.factionId).mkString(", ")
        println(s"m5probe(seed=$seed): GAME OVER after $turns decisions, journal=${journal.size}, winner(s): $w")
        // Replay fidelity: a fresh load of this full-campaign journal (Fates and
        // all) must reconstruct to the same game-over.
        val replay = new Journal.InMemory("m5replay")
        journal.lines.foreach(l => replay.append(replay.size, l))
        EngineSession.load(replay, Factions, opts).pending() match {
          case Outcome.GameOver(w2) if w2.map(_.factionId).toSet == winners.map(_.factionId).toSet =>
            println(s"  replay OK -> same game-over ($w)")
          case other => sys.error(s"  replay MISMATCH: $other")
        }
        stop = true
      case Outcome.Rejected(reason) =>
        println(s"m5probe(seed=$seed): STOPPED after $turns decisions, journal=${journal.size}")
        println(s"reason: $reason")
        stop = true
    }
  }

  // -- self-test -------------------------------------------------------------

  private def selftest(): Unit = {
    val rng = new scala.util.Random(1234)
    val journal = new Journal.InMemory("selftest")
    val session = EngineSession.create(journal, Factions, SelfTestOptions)

    var outcome = session.pending()
    var turns = 0
    val cap = 20000
    var stop = false
    while (!stop) {
      outcome match {
        case Outcome.Next(turn) =>
          turns += 1
          if (turns > cap) sys.error(s"exceeded $cap turns — game not progressing")
          val idx = pickRandom(turn, rng)
          outcome = session.apply(turn.seat, idx)
        case Outcome.GameOver(winners) =>
          println(s"game over after $turns decisions; journal = ${journal.size} lines")
          println("winner(s): " + (if (winners.isEmpty) "Humanity" else winners.map(_.factionId).mkString(", ")))
          stop = true
        case Outcome.Rejected(reason) =>
          sys.error(s"unexpected rejection mid-game: $reason")
      }
    }

    // Replay verification: a fresh session loaded from the same journal +
    // seating/options must replay to the same game-over (deterministic — all
    // oracle results are journaled).
    val replayJournal = new Journal.InMemory("replay")
    journal.lines.foreach(l => replayJournal.append(replayJournal.size, l))
    val replayed = EngineSession.load(replayJournal, Factions, SelfTestOptions)
    replayed.pending() match {
      case Outcome.GameOver(winners) =>
        println(s"replay OK: load() reconstructed the journal to game-over (${winners.map(_.factionId).mkString(", ")})")
      case other =>
        sys.error(s"replay MISMATCH: expected GameOver, got $other")
    }

    // Mid-game replay: a journal prefix must replay to a live decision (or over)
    // without error.
    val prefixLen = (journal.size / 2).toInt.max(1)
    val prefix = new Journal.InMemory("prefix")
    journal.lines.take(prefixLen).foreach(l => prefix.append(prefix.size, l))
    val mid = EngineSession.load(prefix, Factions, SelfTestOptions)
    mid.pending() match {
      case Outcome.Next(t)      => println(s"mid-replay OK: prefix of $prefixLen lines -> decision for ${t.seat.factionId} (${t.options.size} options)")
      case Outcome.GameOver(_)  => println(s"mid-replay OK: prefix of $prefixLen lines -> game already over")
      case Outcome.Rejected(r)  => sys.error(s"mid-replay rejected: $r")
    }

    println("SELFTEST PASSED")
  }

  /** Prefer a real Choice (not Back/Cancel/Info, which don't commit progress);
    * fall back to any option. */
  private def pickRandom(turn: Turn, rng: scala.util.Random): Int = {
    val choices = turn.options.filter(_.kind == MoveOption.Choice)
    val pool = if (choices.nonEmpty) choices else turn.options
    pool(rng.nextInt(pool.size)).index
  }

  // -- interactive -----------------------------------------------------------

  private def play(): Unit = {
    val journal = new Journal.InMemory("repl")
    val session = EngineSession.create(journal, Factions, SelfTestOptions)
    val in = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))

    var outcome = session.pending()
    var stop = false
    while (!stop) {
      outcome match {
        case Outcome.Next(turn) =>
          println()
          println(s"== ${turn.seat.factionId} to act ==")
          if (turn.prompt.nonEmpty) println(turn.prompt)
          turn.options.foreach(o => println(f"  [${o.index}%2d] ${o.text} ${tag(o.kind)}"))
          print("choose index (or 'q' to quit): ")
          val line = Option(in.readLine()).map(_.trim).getOrElse("q")
          if (line == "q") stop = true
          else line.toIntOption match {
            case Some(i) => outcome = session.apply(turn.seat, i) match {
              case Outcome.Rejected(reason) => println(s"rejected: $reason"); outcome // re-show same turn
              case ok => ok
            }
            case None => println("enter a number")
          }
        case Outcome.GameOver(winners) =>
          println("GAME OVER — winner(s): " + (if (winners.isEmpty) "Humanity" else winners.map(_.factionId).mkString(", ")))
          stop = true
        case Outcome.Rejected(reason) =>
          println(s"rejected: $reason"); stop = true
      }
    }
  }

  private def tag(k: MoveOption.Kind): String = k match {
    case MoveOption.Choice => ""
    case MoveOption.Back   => "(back)"
    case MoveOption.Cancel => "(cancel)"
    case MoveOption.Info   => "(info)"
  }
}
