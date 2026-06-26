package arcsbot.render

import arcsbot.engine.RenderExport
import java.io.ByteArrayInputStream
import java.nio.file.{Files, Paths}
import javax.imageio.ImageIO

/* =============================================================================
 * RenderSmoke — end-to-end Path B check (M3 Phase 4).
 *
 *   sbt "renderer/runMain arcsbot.render.RenderSmoke [steps]"
 *
 * Plays a demo game, renders the board through headless Chromium, writes
 * hrf-web/render-out/board.png, and asserts it decodes to a non-trivial image.
 * Requires the Scala.js bundle (`sbt hrfWeb/fastLinkJS`), the local asset mirror
 * (assets/webp2/...), and a Chromium that Playwright can launch (run
 * `sbt "renderer/runMain arcsbot.render.InstallBrowser"` or set
 * PLAYWRIGHT_BROWSERS_PATH; CI gates this with RENDER_SMOKE=1).
 * ===========================================================================*/
object RenderSmoke {
  def main(args: Array[String]): Unit = {
    val steps   = args.lift(0).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(60)
    val outPng  = Paths.get("hrf-web/render-out/board.png")
    Files.createDirectories(outPng.getParent)

    val session  = RenderExport.playDemo(steps)
    val renderer = new PathBRenderer(RenderServer.fromRepo())
    try {
      val r   = renderer.render(session, viewer = None)
      Files.write(outPng, r.png)
      val img = ImageIO.read(new ByteArrayInputStream(r.png))
      println(s"wrote $outPng — ${img.getWidth}x${img.getHeight}, ${r.png.length} bytes")
      require(img.getWidth > 200 && img.getHeight > 200,
        s"render too small: ${img.getWidth}x${img.getHeight}")

      // A blank page (e.g. the board never painted) decodes to a near-uniform
      // image and slips past the size check, so assert real visual content:
      // sample a grid and require many distinct colours. A blank render has ~1.
      val colors = scala.collection.mutable.HashSet.empty[Int]
      val step = math.max(1, math.min(img.getWidth, img.getHeight) / 64)
      var y = 0
      while (y < img.getHeight) {
        var x = 0
        while (x < img.getWidth) { colors += (img.getRGB(x, y) & 0xFFFFFF); x += step }
        y += step
      }
      println(s"distinct sampled colours: ${colors.size}")
      require(colors.size >= 64,
        s"render looks blank: only ${colors.size} distinct colours sampled")
      println("RenderSmoke OK")
    } finally renderer.close()
  }
}

/** One-shot helper to download the Chromium build Playwright needs. */
object InstallBrowser {
  def main(args: Array[String]): Unit = {
    com.microsoft.playwright.CLI.main(Array("install", "chromium"))
    println("chromium installed")
  }
}
