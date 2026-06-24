package arcsbot.engine

/* =============================================================================
 * EngineSession — the bridge to the HRF Arcs rules engine.
 *
 * STATUS: documented scaffold. The HRF-touching body is written as commentary
 * (`/* HRF: ... */`) because it requires the HRF Arcs engine on the JVM
 * classpath (Milestone 1, see docs/ARCHITECTURE.md). The public types below
 * (Turn, MoveOption, Outcome) are real and Discord-facing; the bot depends only
 * on these, never on `arcs.*` / `hrf.*` directly.
 *
 * Read docs/HRF-ENGINE.md before implementing. The whole job of this class is to
 * be an *interactive* version of HRF's `BaseHost.main` (haunt-roll-fail
 * host.scala:136) where the "ask a faction" step (host.scala:31 askFaction)
 * routes to Discord instead of to an AI bot.
 * ===========================================================================*/

/** A faction/seat, by its HRF id ("Red"/"Yellow"/"Blue"/"White"; arcs/meta.scala:358). */
final case class Seat(factionId: String)

/** One legal option in the current decision, already flattened to display text.
  * `index` is the option's position in the engine's current `Ask.actions` list;
  * the bot echoes it back to choose. `kind` distinguishes normal choices from
  * Back/Cancel (base.scala:284/:286) so the UI can render an "undo step" button. */
final case class MoveOption(index: Int, text: String, kind: MoveOption.Kind)
object MoveOption {
  sealed trait Kind
  case object Choice extends Kind
  case object Back   extends Kind
  case object Cancel extends Kind
  case object Info   extends Kind
}

/** The engine has stopped and needs `seat`'s player to pick one of `options`.
  * `prompt` is the flattened `Ask`/question text. */
final case class Turn(seat: Seat, prompt: String, options: Seq[MoveOption])

/** Result of trying to apply a chosen option. */
sealed trait Outcome
object Outcome {
  /** Applied; here is the next thing needed (another decision, same or different seat). */
  final case class Next(turn: Turn) extends Outcome
  /** The game ended. */
  final case class GameOver(winners: Seq[Seat]) extends Outcome
  /** The chosen option wasn't legal in the current state (stale UI, race). */
  final case class Rejected(reason: String) extends Outcome
}

/** Owns one Arcs game: its journal, its reconstructed engine state, and its
  * current pending decision. Construct via [[EngineSession.load]]. */
final class EngineSession private (val journal: Journal /*, private val game: arcs.Game */) {

  /** Drive the engine through all automatic/oracle continuations from the
    * current state until it either ends or stops at a player decision.
    *
    * HRF: this is the loop of `performContinue` + `askFaction` from
    * host.scala, EXCEPT that the multi-option `Ask(faction, actions)` case is
    * NOT auto-resolved — it's returned to Discord. The Roll/Shuffle/Random/
    * Force/Then/Log/Milestone cases ARE resolved here, and any resulting
    * OracleAction is appended to the journal so replays stay deterministic
    * (host.scala:51-114 shows exactly how each case resolves). */
  def pending(): Outcome = {
    /* HRF (M1):
     *   var continue = currentContinue
     *   loop:
     *     continue match {
     *       case GameOver(ws, _, _)        => return Outcome.GameOver(ws.map(toSeat))
     *       case Ask(f, List(only))        => applyForced(only); recompute; continue loop
     *       case Ask(f, actions)           => return Outcome.Next(toTurn(f, actions))
     *       case Roll/Shuffle/Random(...)  => val res = resolveWithSeededRng(...)
     *                                         journal.append(size, Serialize.write(res))
     *                                         continue = game.performContinue(..., res, false).continue
     *       case Force/Then(a)             => continue = game.performContinue(..., a, false).continue
     *       case Log/Milestone/Delayed(..) => record; unwrap; continue loop
     *     }
     */
    Outcome.Rejected("not yet implemented — Milestone 1 (HRF engine on JVM)")
  }

  /** Apply the player's chosen option, validating it against the live decision.
    *
    * HRF: confirm `optionIndex` maps to a `UserAction` currently in
    * `Ask.actions`; if not -> Rejected. Otherwise
    *   journal.append(size, Serialize.write(chosen.unwrap))   // arcs/serialize.scala
    *   game.performContinue(Some(continue), chosen, validating=false)
    * then drive forward via `pending()`. */
  def apply(seat: Seat, optionIndex: Int): Outcome = {
    val _ = (seat, optionIndex)
    Outcome.Rejected("not yet implemented — Milestone 2")
  }

  /** Undo to a journal index (truncate + replay). Gate behind consent in the bot
    * (HRF etiquette: ask before undoing dice/reveals — meta.scala:204). */
  def undoTo(idx: Long): Outcome = {
    journal.truncate(idx)
    /* HRF (M2): rebuild `game` by replaying journal.lines through the engine. */
    pending()
  }
}

object EngineSession {

  /** Load (or resume) a game by replaying its journal through the HRF engine.
    *
    * HRF (M1/M2):
    *   1. parse the first line -> StartGameAction (carries factions + options);
    *      create `arcs.Game(setup, options)` (arcs/game.scala:1287).
    *   2. fold the remaining lines: for each, `Serialize.parseAction(line)`
    *      (arcs/serialize.scala) and `game.performContinue(...)` to advance.
    *   3. the resulting `Continue` is the live decision point. */
  def load(journal: Journal): EngineSession =
    new EngineSession(journal)

  /** Create a brand-new Arcs: The Blighted Reach game.
    *
    * HRF: meta is `arcs.MetaBR` (name "arcs-br", arcs/meta.scala:312). Validate
    * the seating with `MetaBR.validateFactionSeatingOptions` (arcs/meta.scala:336),
    * build the StartGameAction from the chosen factions + campaign options, and
    * write it as journal line 0. */
  def create(journal: Journal, factionIds: Seq[String], options: Seq[String]): EngineSession = {
    val _ = (factionIds, options)
    /* HRF (M1): journal.append(0, Serialize.write(startAction)) */
    new EngineSession(journal)
  }
}
