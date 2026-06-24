// ARCS Discord — multi-module build.
//
// STATUS: scaffold. This compiles the module *shells* but the real work of
// M1 is putting the HRF Arcs engine on the JVM classpath (see
// docs/ARCHITECTURE.md -> "Getting HRF onto the JVM classpath"). Until then the
// stubs under modules/engine-bridge that reference `arcs.*` / `hrf.*` are
// commented commentary, not active code.

ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "arcsbot"
ThisBuild / version      := "0.0.1-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(engineBridge, renderer, bot)
  .settings(name := "arcs-discord")

// Drives the HRF Arcs engine headless and owns the journal.
// The ONLY module that should depend on the HRF engine.
lazy val engineBridge = (project in file("modules/engine-bridge"))
  .settings(
    name := "engine-bridge",
    libraryDependencies ++= Seq(
      // M1: add the HRF Arcs engine as a JVM dependency, e.g. one of:
      //   "im.hrf" %% "hrf-arcs" % "<version>"          // if published
      // or via a git submodule cross-built to JVM, or vendored sources.
      // "org.scala-lang" % "scala-reflect" % scalaVersion.value, // for JVM action reflection
    )
  )

// Game state -> PNG. Kept separate so the implementation (headless browser vs.
// native compositor) can be swapped. See docs/RENDERING.md.
lazy val renderer = (project in file("modules/renderer"))
  .dependsOn(engineBridge)
  .settings(
    name := "renderer"
    // M3 Path B: a headless-browser driver (e.g. Playwright for JVM) to
    // screenshot HRF's own Arcs UI. M6 Path A: java.awt / skija compositor.
  )

// The Discord application (JDA). Knows nothing about Arcs rules.
lazy val bot = (project in file("modules/bot"))
  .dependsOn(engineBridge, renderer)
  .settings(
    name := "bot",
    libraryDependencies ++= Seq(
      // M4: add the Discord library, e.g.
      //   "net.dv8tion" % "JDA" % "5.x.x"
    )
  )
