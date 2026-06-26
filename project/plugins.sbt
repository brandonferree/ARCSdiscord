// Plugins for ARCS Discord.
//
// M3: Scala.js — compile HRF's browser UI sources to JS so the headless board
// renderer (Path B) can drive the real Arcs web client. See docs/RENDERING.md.
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.16.0")
//
// M4: assembly/native packaging for deploying the bot as a single artifact, e.g.
// addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0")
