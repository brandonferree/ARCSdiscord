package arcsbot.engine

/* =============================================================================
 * PrivateView — the per-seat private-info projection for the M7 web viewer.
 *
 * Arcs (like TI4, whose asyncti4 hand-panel this mirrors) keeps almost nothing
 * hidden on the board itself: ships, cities, resources, trophies and the Court
 * are all public and already drawn by the shared Path B board render. The one
 * genuinely private thing a player holds is their **hand of action cards**, so
 * that — and only that — is what this panel shows for the viewer's own seat.
 *
 * This is plain data: nothing arcs.* crosses the bridge boundary, exactly like
 * [[ReplayBundle]]. EngineSession flattens the live `FactionState.hand` into
 * these case classes; the web layer renders them into the side panel and never
 * touches the rules engine.
 *
 * SECURITY: a PrivateView reveals hidden information. It must only ever be served
 * to the authenticated holder of `seat` (Phase 3 Discord OAuth2) — never to an
 * anonymous spectator.
 * ===========================================================================*/

/** One action card in a player's hand, flattened to display data. `suit` is the
  * suit name ("Administration"/"Aggression"/"Construction"/"Mobilization"/
  * "Event"…); `strength` and `pips` are the card's printed numbers (0 for an
  * Event card). `label` is the human name ("Administration 5", "Event"). */
final case class PrivateCard(label: String, suit: String, strength: Int, pips: Int)

/** A seat's private information for the web hand-panel: the cards in hand,
  * sorted for stable display (by suit then strength). `count` is a convenience
  * for the "N cards" header. Empty when the game hasn't started or the seat
  * holds no cards. */
final case class PrivateView(seat: Seat, cards: Seq[PrivateCard]) {
  def count: Int = cards.size
}
