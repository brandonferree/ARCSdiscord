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
  .aggregate(engineBridge, renderer, bot)   // M1: add `hrfEngine`
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
    // Exclude Scala.js / DOM / browser-UI sources (basename match also drops
    // arcs/ui.scala and arcs/styles.scala). This list is the M1 starting point
    // and may need tuning as `sbt hrfEngine/compile` surfaces stragglers.
    Compile / unmanagedSources / excludeFilter :=
      "reflect-js.scala" || "log-js.scala" ||
      "canvas.scala" || "html.scala" || "sprites.scala" ||
      "ui.scala" || "styles.scala" || "web.scala" || "loader.scala" ||
      "panes.scala" || "panes-again.scala" || "selects2.scala" ||
      "voice.scala" || "quine.scala" || "runner.scala" || "hrf.scala" ||
      "grey.scala" || "grey-map.scala" || "settings.scala" ||
      "tracker.scala" || "new-tracker.scala" ||
      "new-new-tracker.scala" || "new-new-new-tracker.scala" ||
      "convert-images.scala" || "extract-logs.scala",
    libraryDependencies ++= Seq(
      "com.lihaoyi"            %% "fastparse"                 % "3.0.2",
      "com.lihaoyi"            %% "pprint"                    % "0.7.0",
      "com.lihaoyi"            %% "fansi"                     % "0.4.0",
      "pt.kcry"                %% "blake3"                    % "3.1.2",
      "org.scala-lang.modules" %% "scala-collection-contrib" % "0.3.0",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"
    )
  )

// --- New code --------------------------------------------------------------

// Drives the HRF Arcs engine headless and owns the journal.
// The ONLY module that should depend on the HRF engine.
lazy val engineBridge = (project in file("modules/engine-bridge"))
  // M1: .dependsOn(hrfEngine) once hrfEngine compiles
  .settings(
    name := "engine-bridge"
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
