package arcsbot.engine

import hrf.elem._

/** Flattens HRF's rich-text `Elem` (the type `UserAction.question`/`option`
  * return) to plain text for Discord, without the browser renderer.
  *
  * Every `Elem` node already implements a recursive `.text` (sealed trait
  * `Elem.text`, hrf-engine/elem.scala) — `Text.text = s`, `Span.text = e.text`,
  * `Image.text = description | <name-from-image-id>`, etc. So the canonical
  * flattening is just `.text`; this object only normalizes whitespace and is the
  * single Discord-facing entry point so callers never import `hrf.elem`.
  */
object ElemText {

  /** Plain-text rendering of an Elem, with runs of whitespace collapsed and
    * trimmed (Arcs option text is built from many spans + token images). */
  def render(e: Elem): String =
    collapse(e.text)

  private def collapse(s: String): String =
    s.replace(' ', ' ')      // non-breaking space -> space
     .replaceAll("[ \\t\\f\\x0B]+", " ")
     .replaceAll(" *\\n *", "\n")
     .trim
}
