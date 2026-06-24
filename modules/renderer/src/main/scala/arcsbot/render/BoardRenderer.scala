package arcsbot.render

import arcsbot.engine.{EngineSession, Seat}

/* =============================================================================
 * BoardRenderer — game state -> PNG, server-side.
 *
 * STATUS: interface + scaffold. See docs/RENDERING.md for the two paths:
 *   Path B (M3, fast cut): drive a headless browser over HRF's own Arcs UI and
 *     screenshot it. Pixel-perfect, zero layout work, heavyweight.
 *   Path A (M6, durable):  native JVM compositor (java.awt / skija) over a
 *     state projection + exported Arcs art tiles. Like async TI4's map renderer.
 *
 * Keep this interface stable so the implementation can swap underneath.
 * ===========================================================================*/

/** A rendered image ready to attach to a Discord message. */
final case class Render(filename: String, png: Array[Byte])

trait BoardRenderer {

  /** Render the public board (map + face-up court + public tableaus).
    * `viewer = None` => public-only; `viewer = Some(seat)` => also include that
    * seat's private info (hand, blind plays — game.scala:258/:260), for DMs. */
  def render(session: EngineSession, viewer: Option[Seat]): Render

  /** Optional: render just one player's tableau, for compact mobile views.
    * async TI4 splits the map and per-player areas into separate images. */
  def renderTableau(session: EngineSession, seat: Seat): Render
}

object BoardRenderer {

  /** Placeholder so the loop can be wired before real rendering exists (M3).
    * Emits a tiny valid 1x1 PNG; replace with Path B then Path A. */
  object Stub extends BoardRenderer {
    private val onePxPng: Array[Byte] = Array(
      0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a, 0x00,0x00,0x00,0x0d,0x49,0x48,0x44,0x52,
      0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01, 0x08,0x06,0x00,0x00,0x00,0x1f,0x15,0xc4,
      0x89,0x00,0x00,0x00,0x0a,0x49,0x44,0x41, 0x54,0x78,0x9c,0x63,0x00,0x01,0x00,0x00,
      0x05,0x00,0x01,0x0d,0x0a,0x2d,0xb4,0x00, 0x00,0x00,0x00,0x49,0x45,0x4e,0x44,0xae,
      0x42,0x60,0x82
    ).map(_.toByte)

    def render(session: EngineSession, viewer: Option[Seat]): Render =
      Render("board.png", onePxPng)
    def renderTableau(session: EngineSession, seat: Seat): Render =
      Render(s"tableau-${seat.factionId}.png", onePxPng)
  }
}
