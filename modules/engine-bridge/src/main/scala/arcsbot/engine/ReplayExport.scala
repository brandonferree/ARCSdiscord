package arcsbot.engine

/* =============================================================================
 * ReplayBundle — the engine->renderer boundary for Path B board rendering.
 *
 * HRF's browser UI renders a game when the host page carries, in hidden divs:
 *   #lobby  : meta/version/title/seating/options (HRF short-id wire form)
 *   #replay : one Serialize.write line per journaled action
 * (see hrf-web/src/.../hrf/Shell.scala). This is the plain-strings projection of
 * a game the renderer injects — nothing arcs.* crosses this line, keeping
 * engine-bridge the only module that touches the rules engine.
 * ===========================================================================*/

/** The `#lobby` + `#replay` payloads for one board state. `at` is the journal
  * index the board should be rendered at (defaults to "all actions" = latest). */
final case class ReplayBundle(lobby: Seq[String], replay: Seq[String], at: Int) {
  def lobbyText: String  = lobby.mkString("\n")
  def replayText: String = replay.mkString("\n")

  /** Inject this bundle into the host page (hrf-web/host/index.html), replacing
    * the `<!--LOBBY-->` / `<!--REPLAY-->` tokens. The renderer serves the result. */
  def injectInto(hostHtml: String): String =
    hostHtml
      .replace("<!--LOBBY-->", lobbyText)
      .replace("<!--REPLAY-->", replayText)
}

/** Tiny CLI to produce a bundle from a fresh game and write it to disk, so the
  * export can be eyeballed / fed to the renderer without a browser.
  *
  *   sbt "engineBridge/runMain arcsbot.engine.RenderExport [steps] [outDir]"
  *
  * Creates a default 4-faction Blighted Reach game, auto-advances up to `steps`
  * decisions (picking the first legal option each time — just to populate the
  * board), then writes lobby.txt + replay.txt. */
object RenderExport {
  /** A fresh 4-faction Blighted Reach game auto-advanced up to `steps` decisions
    * (picking the first legal option each time) just to populate a board. Shared
    * by this CLI and the renderer smoke test. */
  def playDemo(steps: Int): EngineSession = {
    val journal  = new Journal.InMemory("render-export")
    val factions = Seq("Red", "Yellow", "Blue", "White")
    val session  = EngineSession.create(journal, factions, EngineSession.DefaultOptionIds)

    var taken = 0
    var done  = session.pending().isInstanceOf[Outcome.GameOver]
    while (!done && taken < steps) {
      session.pending() match {
        case Outcome.Next(turn) =>
          session.apply(turn.seat, 0) match {
            case Outcome.Next(_)       => taken += 1
            case Outcome.GameOver(_)   => done = true
            case Outcome.Rejected(why) => Console.err.println(s"stop @$taken: $why"); done = true
          }
        case _ => done = true
      }
    }
    session
  }

  def main(args: Array[String]): Unit = {
    val steps  = args.lift(0).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(60)
    val outDir = java.nio.file.Paths.get(args.lift(1).getOrElse("hrf-web/render-out"))

    val session = playDemo(steps)
    val bundle  = session.replayBundle(title = "Arcs Render Export")

    java.nio.file.Files.createDirectories(outDir)
    def write(name: String, text: String): Unit =
      java.nio.file.Files.write(outDir.resolve(name), text.getBytes("UTF-8"))
    write("lobby.txt", bundle.lobbyText)
    write("replay.txt", bundle.replayText)

    println(s"journal has ${bundle.replay.length} action lines (at=${bundle.at})")
    println(s"seating/options:\n  ${bundle.lobby.mkString("\n  ")}")
    println(s"wrote ${outDir.resolve("lobby.txt")} and ${outDir.resolve("replay.txt")}")
  }
}
