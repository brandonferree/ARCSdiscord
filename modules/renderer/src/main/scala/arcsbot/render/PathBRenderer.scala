package arcsbot.render

import arcsbot.engine.{EngineSession, ReplayBundle, Seat}
import com.microsoft.playwright._
import com.microsoft.playwright.options.LoadState
import scala.collection.mutable.ArrayBuffer

/* =============================================================================
 * PathBRenderer — render a game to PNG by driving HRF's real browser UI headless.
 *
 * Pipeline (docs/RENDERING.md, Path B):
 *   EngineSession -> ReplayBundle -> inject into host page -> RenderServer
 *     -> headless Chromium loads it (HRF replays the journal, paints the board)
 *     -> screenshot the board element -> PNG.
 *
 * One long-lived browser + page + server are reused across renders (launching
 * Chromium per render is the expensive part). Not thread-safe: serialise calls.
 * ===========================================================================*/
final class PathBRenderer(
    server: RenderServer,
    viewport: (Int, Int) = (1680, 1200),
    deviceScale: Double = 2.0,
    boardSelector: String = "#game-attachment-point",
    readyTimeoutMs: Double = 25000,
    settleMs: Double = 1500
) extends BoardRenderer with AutoCloseable {

  server.start()

  private val playwright = Playwright.create()
  // Bundled Chromium works on Linux/CI, but on Windows that build can hit a
  // side-by-side activation failure ("spawn UNKNOWN"). Set RENDER_BROWSER_CHANNEL
  // (e.g. "chrome" or "msedge") to drive the system-installed browser instead.
  private val launchOpts = {
    val o = new BrowserType.LaunchOptions().setHeadless(true)
    sys.env.get("RENDER_BROWSER_CHANNEL").map(_.trim).filter(_.nonEmpty)
      .fold(o)(o.setChannel)
  }
  // Playwright.create() spawns a node driver with non-daemon threads, so if launch
  // or newPage throws (e.g. the bundled Chromium fails to start) and we don't close
  // it, the JVM never exits. Clean up the partially-built browser on failure.
  private val browser =
    try playwright.chromium().launch(launchOpts)
    catch { case e: Throwable => playwright.close(); server.stop(); throw e }
  private val page =
    try browser.newPage(new Browser.NewPageOptions()
      .setViewportSize(viewport._1, viewport._2)
      .setDeviceScaleFactor(deviceScale))
    catch { case e: Throwable => browser.close(); playwright.close(); server.stop(); throw e }

  // Verbose by default while we bring Path B up; set RENDER_QUIET=1 to silence.
  private val verbose = sys.env.get("RENDER_QUIET").forall(_ != "1")
  private val logs = ArrayBuffer.empty[String]
  private def note(s: String): Unit = logs.synchronized { logs += s; if (verbose) println("  page> " + s) }
  page.onConsoleMessage((m: ConsoleMessage) => note(s"[${m.`type`()}] ${m.text()}"))
  page.onPageError((e: String) => note(s"[pageerror] $e"))
  page.onResponse((r: Response) => if (r.status() >= 400) note(s"[http ${r.status()}] ${r.url()}"))
  page.onRequestFailed((rq: Request) =>
    note(s"[reqfailed] ${rq.url()} — ${Option(rq.failure()).getOrElse("?")}"))

  page.setDefaultTimeout(readyTimeoutMs)
  page.setDefaultNavigationTimeout(readyTimeoutMs)

  def render(session: EngineSession, viewer: Option[Seat]): Render =
    shoot(session.replayBundle(title = "Arcs Board"), "board.png")

  def renderTableau(session: EngineSession, seat: Seat): Render =
    // First cut: the full board (per-seat tableau cropping is a later refinement).
    shoot(session.replayBundle(title = s"Arcs Tableau ${seat.factionId}"),
          s"tableau-${seat.factionId}.png")

  private def shoot(bundle: ReplayBundle, filename: String): Render = {
    server.page(bundle.injectInto(server.hostTemplate))
    logs.synchronized(logs.clear())

    val url = s"${server.baseUrl}/?replay&meta=arcs-br&at=${bundle.at}"
    if (verbose) println(s"[render] navigating $url")
    page.navigate(url)

    // HRF's image loader retries forever, so the network never goes idle — don't
    // wait on it. Wait for the board canvas to appear, then a fixed settle, then
    // shoot the whole page (element-stability waits hang on the animating canvas).
    try page.waitForSelector(s"$boardSelector canvas",
          new Page.WaitForSelectorOptions().setTimeout(readyTimeoutMs))
    catch { case e: Throwable =>
      if (verbose) println(diag("WARN: no board canvas appeared; screenshotting anyway"))
    }
    page.waitForTimeout(settleMs)

    val png = page.screenshot(new Page.ScreenshotOptions()
      .setFullPage(true).setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED))
    if (verbose) println(s"[render] captured ${png.length} bytes")
    Render(filename, png)
  }

  private def diag(headline: String): String = {
    val tail = logs.synchronized(logs.takeRight(30).mkString("\n  "))
    s"$headline\nbrowser console (last 30):\n  $tail"
  }

  def close(): Unit = {
    try page.close() catch { case _: Throwable => () }
    try browser.close() catch { case _: Throwable => () }
    try playwright.close() catch { case _: Throwable => () }
    try server.stop() catch { case _: Throwable => () }
  }
}
