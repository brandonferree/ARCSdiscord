package arcsbot.discord

import arcsbot.engine.{PrivateCard, PrivateView, Seat}

/* =============================================================================
 * OAuthSecurityCheck — asserts the Phase 3 security gate.
 *
 * The whole reason Phase 3 exists: a PrivateView (a player's hidden hand) must be
 * served ONLY to the authenticated holder of that seat — never to a spectator,
 * never cross-seat. The gate lives in HandPanel.forViewer, which is pure, so we
 * exercise it directly with stubbed session/seat lookups. No live game, no
 * network, no Discord — runs in CI.
 *
 *   sbt "bot/runMain arcsbot.discord.OAuthSecurityCheck"
 *
 * Marker `data-arcs-hand` tags real card elements; its absence proves no hand
 * data leaked into a panel.
 * ===========================================================================*/
object OAuthSecurityCheck {

  private val LEAK = "data-arcs-hand"

  def main(args: Array[String]): Unit = {
    val loginUrl = "http://localhost:8787/auth/login?game=g1"

    // A logged-in player ("good" cookie -> user u1) who holds the Red seat and has
    // a card in hand. Everyone else resolves to None.
    val resolve: String => Option[String] = Map("good" -> "u1").get
    val redHand = PrivateView(Seat("Red"), Seq(PrivateCard("Administration 5", "Administration", 5, 1)))
    val handFor: String => Option[PrivateView] = Map("u1" -> redHand).get

    var failures = 0
    def check(name: String, ok: Boolean): Unit = {
      println(s"${if (ok) "PASS" else "FAIL"}  $name")
      if (!ok) failures += 1
    }

    // 1. No cookie at all -> login prompt, no hand data.
    val anon = HandPanel.forViewer(None, loginUrl, resolve, handFor)
    check("anonymous (no cookie) leaks no hand", !anon.contains(LEAK))
    check("anonymous sees the login link", anon.contains("/auth/login"))

    // 2. A stale / forged cookie that resolves to nobody -> login prompt, no hand.
    val stale = HandPanel.forViewer(Some("bogus"), loginUrl, resolve, handFor)
    check("unknown cookie leaks no hand", !stale.contains(LEAK))

    // 3. A real Discord user who holds NO seat in this game -> login prompt, no hand.
    val resolveSpectator: String => Option[String] = Map("good" -> "u2").get // u2 isn't seated
    val seated = HandPanel.forViewer(Some("good"), loginUrl, resolveSpectator, handFor)
    check("authenticated non-seat-holder leaks no hand", !seated.contains(LEAK))

    // 4. The authenticated seat-holder -> their OWN hand, with the card label.
    val mine = HandPanel.forViewer(Some("good"), loginUrl, resolve, handFor)
    check("authenticated seat-holder sees their hand", mine.contains(LEAK))
    check("...and it is THEIR card", mine.contains("Administration 5"))

    if (failures == 0) println("\nOAuthSecurityCheck: all gates hold.")
    else { Console.err.println(s"\nOAuthSecurityCheck: $failures FAILED."); sys.exit(1) }
  }
}
