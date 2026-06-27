package hrf
//
// Arcs-only replacement for the vendored `hrf.scala` app shell.
//
// The upstream `object HRF` wires up ~10 different HRF games (root, cthw, dwam,
// vast, doms, inis, coup, sehi, suok, yarg, arcs). Only `arcs/` is vendored in
// this repo, so the upstream shell cannot compile here. This file provides the
// pieces the rest of the (vendored) browser code depends on — `object HRF` with
// its config/flag plumbing, `Quants`, `Switches`, `Callbacks` — plus a focused
// boot path that loads a journal in REPLAY mode and renders the Arcs board.
//
// It is intentionally tiny compared to upstream: no menus, no online/hotseat
// flow, no settings UI. The board renderer (Path B, see docs/RENDERING.md) drives
// this headless: the host page injects `#lobby` + `#replay` divs and we render
// the resulting game state at action `?at=N`.
//
import hrf.colmat._
import hrf.elem._
import hrf.html._
import hrf.meta._
import hrf.ui._
import hrf.web._
import hrf.loader._

import org.scalajs.dom

import scalajs.js.timers.setTimeout
import scalajs.js.annotation.JSExportTopLevel
import scala.collection.mutable


object HRF {
    val version = BuildInfo.version

    val imageDataVersion = "as-of-0.8.87"

    def now() = new scalajs.js.Date()

    val startAt = now()

    def uptime() : Int = (now().getTime() - startAt.getTime()).toInt

    val imageCache = new CachedBlobImageLoader("hrf-image-cache-" + imageDataVersion)
    val stringLoader = StringLoader

    // --- config / params -----------------------------------------------------
    // Read from the URL hash, query string, or the #settings title's data-* attrs
    // (mirrors upstream HRF.param so the vendored code sees the flags it expects).
    private val settings = getElem("settings").?

    def hash = dom.window.location.hash.drop(1)
    val search = dom.window.location.search.drop(1)

    private def settingsParam(p : String) = settings./~(_.getAttribute("data-" + p).?).but("")
    private def hashParam(p : String) = hash.split('|').$./(_.split('=')).%(_(0) == p).single./(_.drop(1).join("=")).map(java.net.URLDecoder.decode(_, "UTF-8"))
    private def urlParam(p : String) = search.split('&').$./(_.split('=')).%(_(0) == p).single./(_.drop(1).join("=")).map(java.net.URLDecoder.decode(_, "UTF-8"))

    private val params = mutable.Map[String, |[String]]()

    dom.window.onhashchange = e => params.clear()

    def param(p : String) = params.getOrElseUpdate(p, hashParam(p) || urlParam(p) || settingsParam(p))

    def flag(p : String) = param(p).but("-").but("false").but("no").any

    def paramInt(p : String) = param(p)./~(_.toIntOption)

    def paramList(p : String) = param(p)./~(_.split(' ').$)

    // --- live view persistence (M7 Phase 5 step 2) ---------------------------
    // Each board CanvasPane registers its (zoomBase, dX, dY) accessors here (a
    // build-time patch to grey.scala adds the call in the CanvasPaneX ctor).
    // `arcsSaveView()` — exported to JS below — snapshots them to sessionStorage
    // just before an SSE-driven refresh; on the next boot each pane restores its
    // own slot by registration index (panes are constructed in a fixed order for a
    // given game, so the index is stable across reloads). Net effect: the live
    // refresh keeps the viewer's zoom/pan instead of resetting to fit. Best-effort
    // throughout — any storage/parse failure just falls back to the default view.
    private val canvasGetters = mutable.ArrayBuffer[() => scalajs.js.Array[Double]]()

    def registerCanvas(get : () => scalajs.js.Array[Double], set : scalajs.js.Array[Double] => Unit) : Unit = {
        val i = canvasGetters.size
        canvasGetters += get
        try {
            val raw = dom.window.sessionStorage.getItem("arcs-view-" + i)
            if (raw != null) {
                val p = raw.split(',')
                if (p.length == 3) set(scalajs.js.Array(p(0).toDouble, p(1).toDouble, p(2).toDouble))
            }
        } catch { case _ : Throwable => }
    }

    def saveView() : Unit =
        try {
            var i = 0
            while (i < canvasGetters.size) {
                val v = canvasGetters(i)()
                dom.window.sessionStorage.setItem("arcs-view-" + i, v(0).toString + "," + v(1).toString + "," + v(2).toString)
                i += 1
            }
        } catch { case _ : Throwable => }

    var speed = paramInt("speed").|(640)

    val offline = flag("offline") || dom.window.location.protocol == "file:"

    val embedded = flag("embedded-assets")

    val replay = flag("replay")

    val versionOverride : |[String] = hashParam("version") || urlParam("version")

    // --- favicon glyph -------------------------------------------------------
    val defaultGlyph = getElem("icon").asInstanceOf[dom.html.Link].href

    def glyph(s : String) = getElem("icon").asInstanceOf[dom.html.Link].href = s

    // --- keyboard ------------------------------------------------------------
    def onKey(filter : dom.KeyboardEvent => Boolean)(process : => Unit) {
        val old = dom.document.onkeyup
        dom.document.onkeyup = (e) => {
            if (filter(e))
                process
            else
            if (scalajs.js.isUndefined(old).not && old != null)
                old(e)
        }
    }

    // --- the games we support (Arcs + Blighted Reach) ------------------------
    val metaUIs : $[(MetaGame, BaseUI)] = $(
        arcs.Meta   -> arcs.UI,
        arcs.MetaBR -> arcs.UI,
    )

    val metas = metaUIs.lefts

    // --- entry point ---------------------------------------------------------
    def main(args : Array[String]) {
        // Boot once the document is ready. (Upstream also waited on document.fonts,
        // but that FontFaceSet API isn't in the scalajs-dom facade; the headless
        // renderer waits for the board to actually paint before screenshotting, so
        // font-load timing is handled there instead.)
        if (dom.document.readyState == dom.DocumentReadyState.complete)
            ArcsReplay.boot()
        else
            dom.window.onload = (e) => ArcsReplay.boot()
    }
}


// Exposes `window.arcsSaveView()` to the injected freshness script, which calls
// it just before an SSE-driven refresh so the next boot can restore the viewer's
// zoom/pan (see HRF.registerCanvas / HRF.saveView). @JSExportTopLevel keeps it in
// the NoModule bundle even though nothing in Scala references it.
object ArcsViewExport {
    @JSExportTopLevel("arcsSaveView")
    def save() : Unit = HRF.saveView()
}


// Upstream's cooperative-scheduling helper; referenced by Runner.scala.
class Quants(duration : Int, count : Int, onQuant : () => Boolean) {
    private var last = HRF.uptime()
    private var n = 0

    def continue(f : () => Unit) {
        val now = HRF.uptime()

        if (now - last < duration && n < count) {
            n += 1
            f()
        }
        else {
            last = now
            n = 0

            if (onQuant())
                setTimeout(0)(continue(f))
        }
    }
}


// Referenced by Callbacks (below) and the game UI; replay needs none of it.
trait Switches {
    def canSwitches : Boolean
    def getSwitches : $[String]
    def enableSwitch(s : String) : Unit
    def disableSwitch(s : String) : Unit
}

object NoSwitches extends Switches {
    def canSwitches = false
    def getSwitches = $
    def enableSwitch(s : String) {}
    def disableSwitch(s : String) {}
}


// The game UI (arcs.UI) is constructed with a Callbacks; for a headless replay
// render every callback is a no-op (no saving, no replaying, no settings UI).
trait Callbacks {
    def switches : Switches
    def canPlayAgain : Boolean
    def playAgain() : Unit
    def saveReplay(onSave : => Unit) : Unit
    def saveReplayOnline(replaceBots : Boolean, mergeHumans : Boolean)(onSave : String => Unit) : Unit
    def settings : $[Setting]
    def editSettings(onEdit : => Unit) : Unit
}


// The actual boot: read the injected #lobby/#replay, build a replay journal,
// construct the Arcs UI, and run the engine to the requested action.
object ArcsReplay {
    def boot() {
        // Base styles the panes rely on (mirrors upstream HRFUI).
        StyleRegister.add($(xstyles.pane))
        StyleRegister.add($(xstyles.outer))
        StyleRegister.add($(xstyles.inner, xstyles.pane.log))

        val lobby = readLines("lobby")
        val story = readLines("replay")

        def field(prefix : String) = lobby.%(_.startsWith(prefix + " "))./(_.substring(prefix.length + 1))

        val metaName = field("meta").single.|("arcs-br")
        val meta = HRF.metas.%(_.name == metaName).single.|(arcs.MetaBR)

        renderMeta(meta)(field, story)
    }

    private def readLines(id : String) : $[String] =
        dom.document.getElementById(id).?./(_.textContent).|("").split('\n').$./(_.trim).but("")

    // Split out so we can bind the path-dependent `meta` types.
    private def renderMeta(meta : MetaGame)(field : String => $[String], story : $[String]) {
        import meta.gaming._

        val title   = field("title").join(" ").some.|("Replay")
        val seating = field("seating").join(" ").split(' ').$.but("")./~(meta.parseFaction)
        val options = field("options").join(" ").split(' ').$.but("")./~(meta.parseOption)

        val journal = new ReplayPhantomJournal[ExternalAction](
            meta, story.join("\n"), s => meta.parseActionExternal(s), HRF.paramInt("at") | 999999)

        // Asset sources + immediate preload, then run. `?assets=<base>` prefixes
        // the (otherwise origin-relative) webp paths, so the host page can point
        // at a local mirror (default: served from the same origin) or elsewhere.
        val assetBase = HRF.param("assets").|("")
        val assets  = meta.assets.%(a => a.condition(seating, options) || true)./~(_.get)
        val sources = assets./(a => a.name -> (assetBase + "webp2/" + meta.path + "/images/" + a.copy(ext = "webp").src)).toMap
        val preload = assets.%(_.lzy == Laziness.Immediate)./(a => a.name -> sources(a.name))
        val loader  = HRF.imageCache

        loader.wait(preload.rights) {
            // A preload asset can fail (e.g. a 404 on a missing mirror file); the loader
            // marks it Error and wait() still fires. Skip the failures so get() doesn't
            // throw — the board renders without that image rather than not rendering at all.
            val loaded = preload.%((key, url) => loader.has(url))./((key, url) => key -> loader.get(url)).toMap
            val resources = Resources(ImageResources(loaded, sources, HRF.imageCache), () => Map())

            val guir = new ElementAttachmentPoint(getElem("game-attachment-point"))

            val callbacks = new Callbacks {
                def switches = NoSwitches
                def canPlayAgain = false
                def playAgain() {}
                def saveReplay(onSave : => Unit) {}
                def saveReplayOnline(replaceBots : Boolean, mergeHumans : Boolean)(onSave : String => Unit) {}
                def settings = $
                def editSettings(onEdit : => Unit) { onEdit }
            }

            val gui = HRF.metaUIs.toMap.apply(meta).asInstanceOf[BaseUI { val mmeta : meta.type }]
            val renderer = gui.create(guir, seating.num, options, resources, title, callbacks)

            import meta.tagF

            // In replay everything comes from the journal; once it is exhausted
            // (at action `at`) idle rather than asking — the board is drawn by then.
            def auto(g : G, f : meta.gaming.F) : AskResult = WaitRemote

            Runner.run(meta)(seating, options, resources, renderer,
                (g, f) => f.as[meta.F]./(f => auto(g, f)).|!("unsuitable ask"), journal)
        }
    }
}
