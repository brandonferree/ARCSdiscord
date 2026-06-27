// ARCS Discord — multi-module build.
//
// `hrfEngine` compiles the vendored HRF Arcs rules engine for the JVM (M1).
// The bot modules (engine-bridge, renderer, bot) are the new code.
//
// CURRENT STATE:
//  - The scaffold modules (engine-bridge, renderer, bot) compile standalone;
//    `sbt compile` (root aggregate) builds them and stays green.
//  - `hrfEngine` is present but NOT yet aggregated or depended on, because
//    getting the vendored sources to compile on the JVM is Milestone 1
//    (tuning the excludeFilter below). Work it with `sbt hrfEngine/compile`.
//  - When `hrfEngine/compile` is green: add `hrfEngine` to `root.aggregate`
//    and `engineBridge.dependsOn(hrfEngine)`, then uncomment the HRF imports
//    in modules/engine-bridge. See docs/M1.md.

ThisBuild / scalaVersion := "2.13.16"   // matches upstream HRF (common.sbt)
ThisBuild / organization := "arcsbot"
ThisBuild / version      := "0.0.1-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(hrfEngine, selfplay, engineBridge, renderer, bot)
  .settings(name := "arcs-discord")

// --- Vendored HRF Arcs engine, cross-compiled to the JVM -------------------
// Sources live under hrf-engine/ (top-level *.scala = the `hrf` core; arcs/ =
// the game). License: MIT (hrf-engine/LICENSE). We point the Scala source root
// at the module base dir (like upstream HRF) and exclude the browser/Scala.js
// files, keeping the `*-jvm.scala` shims that provide the JVM equivalents of
// the scalajs.* APIs the core references (e.g. reflect-jvm.scala shims
// scala.scalajs.reflect, used by base.scala/serialize.scala).
lazy val hrfEngine = (project in file("hrf-engine"))
  .settings(
    name := "hrf-engine",
    // top-level *.scala AND the arcs/ subdir (recursive under the base dir):
    Compile / scalaSource := baseDirectory.value,
    // Exclude the Scala.js / DOM / browser-UI sources, keeping the *-jvm.scala
    // shims. Criterion: every excluded file imports a browser package
    // (org.scalajs.dom / hrf.html / hrf.canvas / hrf.web / sprites) or is a
    // browser-only build/util script. Basename match — note "ui.scala" drops
    // BOTH top-level and arcs/ui.scala (both DOM). NOT excluded (JVM-clean and
    // required by arcs/): selects2.scala (hrf.base.SelectSubset),
    // new-new-new-tracker.scala (hrf.tracker4), styles.scala (top-level hrf.elem
    // styles + arcs/styles.scala which defines the arcs.elem package object).
    Compile / unmanagedSources / excludeFilter :=
      "reflect-js.scala" || "log-js.scala" ||
      "canvas.scala" || "html.scala" || "sprites.scala" ||
      "ui.scala" || "web.scala" || "loader.scala" ||
      "timeline.scala" ||   // browser variant; timeline-jvm.scala is the JVM stub
      "journal.scala" ||    // package hrf, imports hrf.web (browser); unused headless
      "panes.scala" || "panes-again.scala" ||
      "voice.scala" || "quine.scala" || "runner.scala" || "hrf.scala" ||
      "grey.scala" || "grey-map.scala" ||
      "tracker.scala" || "new-tracker.scala" || "new-new-tracker.scala" ||
      "convert-images.scala" || "extract-logs.scala",
    // Upstream HRF generates `hrf.BuildInfo` via sbt-buildinfo (base.scala /
    // host-jvm.scala read .version/.name). We don't vendor it, so synthesize a
    // minimal equivalent here — keeps hrf-engine/ pristine.
    Compile / sourceGenerators += Def.task {
      val f = (Compile / sourceManaged).value / "hrf" / "BuildInfo.scala"
      IO.write(f,
        """package hrf
          |object BuildInfo {
          |  val name = "hrf-arcs"
          |  val version = "test-0.8.140"  // vendored HRF version; "test" prefix enables MetaBR.development (full Act I-III campaign, lifts mandatory Act1Only)
          |}
          |""".stripMargin)
      Seq(f)
    }.taskValue,
    libraryDependencies ++= Seq(
      "com.lihaoyi"            %% "fastparse"                 % "3.0.2",
      "com.lihaoyi"            %% "pprint"                    % "0.7.0",
      "com.lihaoyi"            %% "fansi"                     % "0.4.0",
      "pt.kcry"                %% "blake3"                    % "3.1.2",
      "org.scala-lang.modules" %% "scala-collection-contrib" % "0.3.0",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"
    )
  )

// --- Vendored HRF Arcs engine, cross-compiled to Scala.js (M3) --------------
// Path B board renderer drives HRF's REAL browser UI headless. To do that we
// compile the vendored *browser* sources (the DOM/canvas/sprites/ui code the
// JVM build excludes) to JavaScript. This is the inverse of `hrfEngine`'s
// excludeFilter: keep the browser sources, drop the *-jvm.scala shims and the
// headless `BaseHost` (host.scala / arcs/host.scala use parallel collections).
//
// Two vendored files are NOT compiled and are replaced by our own arcs-only
// shell (hrf-web/src): `hrf.scala` (its app shell references 10 other HRF games
// that aren't vendored — only arcs/ is) and `quine.scala` (offline-bundle saver
// we don't need). Our shell lives under hrf-web/src and provides `object hrf.HRF`
// + `Callbacks`/`Quants` specialised to Arcs replay. See docs/RENDERING.md.
//
// NOT aggregated into root yet (mirrors how M1 staged hrfEngine): iterate with
// `sbt hrfWeb/compile` until green, then wire it into the renderer pipeline.
lazy val hrfWeb = (project in file("hrf-web"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "hrf-web",
    // Our arcs-only shell lives in hrf-web/src/main/scala; ALSO pull in the
    // vendored browser sources from hrf-engine/ (kept pristine).
    Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "hrf-engine",
    // Inverse of the JVM excludeFilter (build.sbt `hrfEngine`). Drop, by basename:
    //  - *-jvm.scala shims (browser equivalents are the .scala/-js.scala files)
    //  - host.scala / arcs/host.scala (headless BaseHost; parallel collections)
    //  - hrf.scala / quine.scala (replaced by our arcs-only shell)
    //  - old tracker variants (keep only new-new-new-tracker.scala, as JVM does)
    //  - convert-images.scala (JVM build tooling)
    // ...and anything under a `target/` dir (the hrf-engine source root we add
    // below recurses into hrf-engine/target, which holds the JVM build's
    // generated hrf/BuildInfo.scala — would clash with ours).
    Compile / unmanagedSources / excludeFilter := new SimpleFileFilter({ f =>
      val excludedNames = Set(
        "reflect-jvm.scala", "log-jvm.scala", "grey-jvm.scala",
        "timeline-jvm.scala", "host-jvm.scala", "host.scala",
        "hrf.scala", "quine.scala",
        "tracker.scala", "new-tracker.scala", "new-new-tracker.scala",
        "convert-images.scala") ++
        // Patched copies of these are generated below: grey/grey-map/runner for
        // scalajs-dom 2.x facade compat, base.scala for the void-replay Then-follow
        // fix. The originals are excluded so we compile the patched versions.
        Set("grey.scala", "grey-map.scala", "runner.scala", "base.scala")
      excludedNames(f.getName) ||
        f.getAbsolutePath.replace('\\', '/').contains("/target/")
    }),
    // Build-time patch: keep hrf-engine/ byte-for-byte pristine while fixing the
    // handful of DOM assignments scalajs-dom 2.x can't type. We copy the three
    // affected files into a managed dir with targeted replacements:
    //  - `style.touchAction = ` / `.filter = `  -> assign via js.Dynamic
    //  - `recv.on(touch*|wheel) = f`            -> hrf.ui.touchy(recv).on... = f
    // Re-applies automatically whenever HRF is re-vendored. See hrf/ui/DomCompat.
    Compile / sourceGenerators += Def.task {
      val outDir = (Compile / sourceManaged).value / "hrf-patched"
      val base   = (ThisBuild / baseDirectory).value / "hrf-engine"

      def handlers(s : String) =
        s.replaceAll("""([\w.]+)\.(ontouchstart|ontouchmove|onwheel)(\s*=)""",
                     "hrf.ui.touchy($1).$2$3")
      def touchAction(s : String) =
        s.replace(".style.touchAction = ", ".style.asInstanceOf[scalajs.js.Dynamic].touchAction = ")
      def cssFilter(s : String) =
        s.replace(".filter = ", ".asInstanceOf[scalajs.js.Dynamic].filter = ")
      // M7 Phase 5 step 2: register each board CanvasPane's zoom/pan with the shell
      // so an SSE-driven refresh can save+restore the viewer's view (see Shell.scala
      // hrf.HRF.registerCanvas / saveView). Injected into the CanvasPaneX ctor right
      // after a stable local; zoomBase/dX/dY and draw() are all in scope there.
      def registerCanvasView(s : String) =
        s.replace(
          "        var lastInteraction = -1000",
          "        var lastInteraction = -1000\n" +
          "        hrf.HRF.registerCanvas(() => scalajs.js.Array[Double](zoomBase, dX, dY), (a) => { zoomBase = a(0); dX = a(1); dY = a(2); draw() })")
      // Replay reconstructs a past board state via performVoid -> mapForceLog, which
      // followed Force continuations but not Then/Milestone. Campaign setup (e.g.
      // ArcsBlightedReachStartAction, which populates game.states) is reached via
      // Then, so the void-replay game had empty faction states and threw
      // `key not found: <faction>` at the first FactionState access (a fate crisis).
      // Make the void mapForceLog follow Then + Milestone too, matching the JVM
      // replay (engine-bridge EngineSession.replayStep). Targets the void mapForceLog
      // only — the other Force case (the real perform) has a different body.
      // Match the line content only (no trailing newline) so it is robust to
      // CRLF/LF; the original line terminator follows the inserted Milestone line.
      def voidReplayThen(s : String) =
        s.replace(
          "            case Force(a) => mapForceLog(performRawRecord(a, true).continue)",
          "            case Force(a) => mapForceLog(performRawRecord(a, true).continue)\n" +
          "            case Then(a) => mapForceLog(performRawRecord(a, true).continue)\n" +
          "            case Milestone(_, a) => mapForceLog(performRawRecord(a, true).continue)")

      // The browser's main replay loop performs Force continuations even while
      // journal actions are still pending (UIContinue(Force(a), aa)), but Then /
      // Milestone are only handled when the pending list is Nil (live play, where
      // they get recorded). With actions pending they fall to the catch-all, which
      // performs the next journal action and SKIPS the forced one. HRF's own
      // journals record Then actions; our engine-bridge journal is external-only
      // (forced steps regenerated), so the campaign setup ArcsBlightedReachStartAction
      // (reached via Then, populates game.states) was skipped, leaving empty faction
      // states -> `key not found: <faction>` at the first FactionState access.
      // Perform Then/Milestone inline during replay, mirroring Force. Inserted before
      // the catch-all; the earlier Nil-only Then/Milestone cases still win for live play.
      def replayThenForced(s : String) =
        s.replace(
          "                case UIContinue(c, aa) =>",
          "                case UIContinue(c @ Then(then), aa) =>\n" +
          "                    dirty = true\n" +
          "                    UIPerform(then, aa)\n\n" +
          "                case UIContinue(c @ Milestone(_, then), aa) =>\n" +
          "                    dirty = true\n" +
          "                    UIPerform(then, aa)\n\n" +
          "                case UIContinue(c, aa) =>")

      // The per-record validation cross-check (UIRecord -> check.validate) builds a
      // fresh "check" game by replaying the recorded actions in generateGame /
      // generateGameVoid, draining forced continuations in a local while loop. That
      // loop followed Log + (DelayedContinue) Force but NOT Then/Milestone -- the same
      // skip replayThenForced fixed in the main loop. So the check game's campaign
      // setup (ArcsBlightedReachStartAction, reached via Then, populates game.states)
      // was skipped, leaving empty faction states, and check.validate threw
      // `key not found: <faction>` (e.g. Yellow). It is swallowed as ErrorContinue, so
      // the board still rendered from the (correct) main game, but every per-record
      // validation spuriously failed and spammed the render log + a crash file. Follow
      // Then/Milestone inline, mirroring the existing Force case (bare + DelayedContinue
      // forms). The target line is byte-identical in generateGame and generateGameVoid,
      // so this single replace patches both. Match line content only (CRLF/LF robust).
      def checkGameThenForced(s : String) =
        s.replace(
          "                    case DelayedContinue(_, Force(then)) => c = g.performContinue(|(g.continue), then, false).nest; true",
          "                    case DelayedContinue(_, Force(then)) => c = g.performContinue(|(g.continue), then, false).nest; true\n" +
          "                    case DelayedContinue(_, Then(then)) => c = g.performContinue(|(g.continue), then, false).nest; true\n" +
          "                    case DelayedContinue(_, Milestone(_, then)) => c = g.performContinue(|(g.continue), then, false).nest; true\n" +
          "                    case Then(then) => c = g.performContinue(|(g.continue), then, false).nest; true\n" +
          "                    case Milestone(_, then) => c = g.performContinue(|(g.continue), then, false).nest; true")

      def patch(name : String, f : String => String) : File = {
        val dst = outDir / name
        IO.write(dst, f(IO.read(base / name)))
        dst
      }

      Seq(
        patch("grey.scala",     s => registerCanvasView(handlers(touchAction(s)))),
        patch("grey-map.scala", s => handlers(touchAction(s))),
        patch("runner.scala",   s => checkGameThenForced(replayThenForced(cssFilter(s)))),
        patch("base.scala",     voidReplayThen)
      )
    }.taskValue,
    // Same synthesized hrf.BuildInfo as the JVM build (keeps hrf-engine pristine).
    Compile / sourceGenerators += Def.task {
      val f = (Compile / sourceManaged).value / "hrf" / "BuildInfo.scala"
      IO.write(f,
        """package hrf
          |object BuildInfo {
          |  val name = "hrf-arcs"
          |  val version = "test-0.8.140"  // vendored HRF version; "test" prefix enables MetaBR.development (full Act I-III campaign, lifts mandatory Act1Only)
          |}
          |""".stripMargin)
      Seq(f)
    }.taskValue,
    // Upstream HRF compiles with these language features enabled (postfix ops in
    // runner.scala, the structural `BaseUI { val mmeta }` refinement, etc.).
    scalacOptions ++= Seq(
      "-language:postfixOps",
      "-language:implicitConversions",
      "-language:reflectiveCalls",
      "-language:existentials",
      "-language:higherKinds"
    ),
    // Boot from our Arcs-only shell. NoModule output so the host page can load the
    // emitted main.js with a plain <script> tag (HRF reads a `#script` element).
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("hrf.HRF"),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.NoModule) },
    libraryDependencies ++= Seq(
      // HRF's browser canvas/touch code uses a handful of members no stock
      // scalajs-dom facade carries together (MouseEvent.offsetX, touchAction,
      // Element.ontouchstart/onwheel). We supply those via a `package object ui`
      // shim (hrf-web/src .../ui/DomCompat.scala) so the vendored files stay
      // pristine; 2.8.0 already provides dom.CSSStyleRule etc. that web.scala needs.
      "org.scala-js"           %%% "scalajs-dom"               % "2.8.0",
      "com.lihaoyi"            %%% "fastparse"                 % "3.0.2",
      "com.lihaoyi"            %%% "pprint"                    % "0.7.0",
      "com.lihaoyi"            %%% "fansi"                     % "0.4.0",
      "org.scala-lang.modules" %%% "scala-collection-contrib" % "0.3.0"
    )
  )

// Headless self-play CLI (M1 proof): drives a full Arcs: The Blighted Reach
// game off-browser and prints the round-trippable journal. Reuses the vendored
// arcs.CampaignHost (bot AI + oracle resolution).
lazy val selfplay = (project in file("modules/selfplay"))
  .dependsOn(hrfEngine)
  .settings(
    name := "selfplay",
    libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"
  )

// --- New code --------------------------------------------------------------

// Drives the HRF Arcs engine headless and owns the journal.
// The ONLY module that should depend on the HRF engine.
lazy val engineBridge = (project in file("modules/engine-bridge"))
  .dependsOn(hrfEngine)
  .settings(
    name := "engine-bridge",
    libraryDependencies ++= Seq(
      // JDBC SQLite driver for the SQL-backed journal (also works against
      // Postgres via a different Connection; SQL is standard).
      "org.xerial" % "sqlite-jdbc" % "3.45.3.0"
    )
  )

// Game state -> PNG. See docs/RENDERING.md. Path B drives HRF's real browser UI
// (compiled by `hrfWeb`) in headless Chromium via Playwright and screenshots the
// board. The static server + asset mirror keep it self-hosted (no hrf.im at runtime).
lazy val renderer = (project in file("modules/renderer"))
  .dependsOn(engineBridge)
  .settings(
    name := "renderer",
    libraryDependencies += "com.microsoft.playwright" % "playwright" % "1.47.0",
    // Default the renderer's repo-relative asset/build paths to this build's root.
    Compile / run / fork := true,
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value
  )

// The Discord application (JDA). Knows nothing about Arcs rules.
lazy val bot = (project in file("modules/bot"))
  .dependsOn(engineBridge, renderer)
  .settings(
    name := "bot",
    libraryDependencies ++= Seq(
      // Audio is excluded — the bot never touches voice, and opus-java drags in
      // native libs we don't want on the classpath.
      ("net.dv8tion" % "JDA" % "5.0.0").exclude("club.minnced", "opus-java"),
      "org.slf4j"    % "slf4j-simple" % "2.0.9"
    ),
    // The bot is a long-running server: fork so JDA's non-daemon threads keep the
    // JVM alive (an in-process `run` exits as soon as main returns). Pin the forked
    // working dir to the build root so RenderServer.fromRepo() resolves hrf-web/
    // and assets/ — mirrors the renderer module. mainClass disambiguates the
    // several *DryRun mains so `bot/run` is non-interactive.
    Compile / run / fork := true,
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value,
    Compile / mainClass := Some("arcsbot.discord.Bot")
  )
