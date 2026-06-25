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
          |  val version = "0.8.140"  // vendored point-in-time HRF version
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

// Game state -> PNG. See docs/RENDERING.md.
lazy val renderer = (project in file("modules/renderer"))
  .dependsOn(engineBridge)
  .settings(name := "renderer")

// The Discord application (JDA). Knows nothing about Arcs rules.
lazy val bot = (project in file("modules/bot"))
  .dependsOn(engineBridge, renderer)
  .settings(
    name := "bot",
    libraryDependencies ++= Seq(
      // M4: "net.dv8tion" % "JDA" % "5.x.x"
    )
  )
