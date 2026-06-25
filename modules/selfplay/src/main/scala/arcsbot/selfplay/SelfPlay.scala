package arcsbot.selfplay

import hrf.colmat._

/** Headless self-play of a full Arcs: The Blighted Reach campaign game.
  *
  * M1 proof that the vendored HRF engine runs off-browser: builds an `arcs.Game`
  * (via the campaign host's batch / `MetaBR` options), loops `performContinue`,
  * auto-resolves Roll/Shuffle/Random and single-option Asks, and uses the
  * generic bot AI for real choices. Every external action is verified to
  * round-trip through `Serialize` (write -> parse -> write) and collected into a
  * journal, which is printed at the end.
  *
  * Reuses `arcs.CampaignHost` for all the engine plumbing (gaming binding, oracle
  * resolution in `askFaction`, the bot AI in `askBot`, the start action and
  * serializer). We only reimplement the drive loop so we can capture and print
  * the journal (upstream `BaseHost.main` plays many games and only dumps the log
  * on error).
  */
object SelfPlay extends arcs.CampaignHost {
  import gaming._

  override def times = 1

  override def main(args: Array[String]): Unit = {
    // One deterministic seating (RNG inside roll/shuffle still varies the game).
    val game = batch.head()

    var continue: Continue = StartContinue
    var a: Action = start
    var journal: List[String] = Nil
    var steps = 0
    var mismatches = 0

    while (game.isOver.not && a.is[HostGameOverAction].not) {
      steps += 1

      a match {
        case ext: ExternalAction if ext.isSoft.not =>
          val s   = serializer.write(ext.unwrap)
          val rt  = serializer.write(serializer.parseAction(s))
          if (s != rt) {
            mismatches += 1
            println(s"[round-trip MISMATCH] $s  !=  $rt")
          }
          journal = s :: journal
        case _ =>
      }

      if (steps > limit) sys.error(s"exceeded step limit $limit (game not terminating)")

      continue = game.performContinue(|(continue), a, !true).continue
      a = askFaction(game, continue)
    }

    val log = journal.reverse
    println("===== JOURNAL (" + log.size + " action lines) =====")
    log.foreach(println)
    println("===== END JOURNAL =====")

    val w = a.as[HostGameOverAction]./(_.winners).|(winners(a)(game))
    println()
    println("steps:        " + steps)
    println("journal lines:" + log.size)
    println("round-trip:   " + (if (mismatches == 0) "OK (all actions stable)" else mismatches + " MISMATCHES"))
    println("winner(s):    " + (if (w.any) w./(nameWinner).mkString(", ") else "Humanity"))

    if (mismatches != 0) sys.exit(1)
  }
}
