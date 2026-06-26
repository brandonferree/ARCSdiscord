package arcsbot.engine

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

/* =============================================================================
 * ReplayCheck — does a journal replay cleanly through the JVM engine?
 *
 *   sbt "engineBridge/runMain arcsbot.engine.ReplayCheck [replay.txt] [at]"
 *
 * Loads the given journal (default hrf-web/render-out/replay.txt) and replays it
 * via EngineSession.load, which drives the SAME hrf-engine rules the browser does.
 * If the JVM replay reaches the requested `at` cleanly but the browser render
 * throws (e.g. `key not found: <faction>` in BlightExpansion), the divergence is
 * in HRF's browser Runner/ReplayPhantomJournal, not the journal or the rules.
 * ===========================================================================*/
object ReplayCheck {
  def main(args: Array[String]): Unit = {
    val path = Paths.get(args.lift(0).getOrElse("hrf-web/render-out/replay.txt"))
    val lines = Files.readAllLines(path).asScala.toVector.map(_.trim).filter(_.nonEmpty)
    println(s"loaded ${lines.length} journal lines from $path")

    val journal = new Journal.InMemory("replay-check")
    lines.zipWithIndex.foreach { case (line, i) =>
      journal.append(i.toLong, line) match {
        case Right(_)  => ()
        case Left(c)   => sys.error(s"append conflict at $i: $c")
      }
    }

    // Same setup playDemo / RenderExport uses (seating order is in the journal).
    val factions = Seq("Red", "Yellow", "Blue", "White")
    val options  = EngineSession.DefaultOptionIds

    try {
      val session = EngineSession.load(journal, factions, options)
      println(s"JVM replay reached the end cleanly. pending = ${session.pending()}")
      println("=> journal + rules are fine on the JVM; the browser crash is Runner/replay-specific.")
    } catch {
      case e: Throwable =>
        println(s"JVM replay ALSO threw: ${e.getClass.getName}: ${e.getMessage}")
        println("=> the bug reproduces on the JVM => it's a core replay/rules issue, not browser-only.")
        e.getStackTrace.take(12).foreach(s => println("    at " + s))
    }
  }
}
