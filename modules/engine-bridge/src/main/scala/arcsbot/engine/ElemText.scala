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
    dedupe(collapse(e.text))

  /** Collapse an immediately-repeated word (optionally space-separated, case-
    * insensitive) into one. HRF renders many tokens as an icon `Image` next to its
    * name `Text`, and both flatten to the same word — so "Place in Arrow 5 FuelFuel"
    * and "Yellow City city and ships" come through doubled. Looped so triples (rare)
    * also collapse. The `{2,}` guard avoids touching incidental single-letter
    * repeats, and `\p{L}` leaves symbols/numbers (▲ ● 5) untouched. */
  private def dedupe(s: String): String = {
    // The repeated word starts at a word boundary (`\b`) and is matched non-greedily;
    // we deliberately do NOT require a trailing boundary so a run like "FuelFuelkeys"
    // (icon "Fuel" + name "Fuel" glued to "keys") still collapses to "Fuelkeys". An
    // optional single space covers the "City city" form. `\p{L}` leaves symbols and
    // numbers (▲ ● 5) alone.
    val r = s.replaceAll("(?i)\\b(\\p{L}{2,}?)\\s?\\1", "$1")
    if (r == s) s else dedupe(r)
  }

  private def collapse(s: String): String =
    s.replace(' ', ' ')      // non-breaking space -> space
     .replaceAll("[ \\t\\f\\x0B]+", " ")
     .replaceAll(" *\\n *", "\n")
     .trim
}
