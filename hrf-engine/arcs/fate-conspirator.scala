package arcs
//
//
//
//
import hrf.colmat._
import hrf.logger._
//
//
//
//

import hrf.tracker4._
import hrf.tracker4.implicits._
import hrf.elem._

import arcs.elem._


case object Conspirator extends Fate("Conspirator", "fate23", 3) {
    override val expansion = ConspiratorExpansion
}


case object ControlTheProceedings extends Objective("f23-01b", "Control the Proceedings")

case object Conspiracies extends Ability("f23-03", "Conspiracies")
case object ScoringConspiracies extends Law("f23-04", "Scoring Conspiracies")
case object FoilingConspiracies extends Law("f23-05", "Foiling Conspiracies")


case class ConspireAmbitionAction(self : Faction, ambition : Ambition, color : Faction, zero : Boolean, then : ForcedAction) extends ForcedAction // with SkipValidate // TODO validate
case class RefillToConspireMainAction(self : Faction, c : GuildCard, market : Int, then : ForcedAction) extends ForcedAction with Soft
case class RefillToConspireAction(self : Faction, c : GuildCard, market : Int, then : ForcedAction) extends ForcedAction

case class MayGuessConspiracyAction(self : Faction, e : Faction, n : Int, then : ForcedAction) extends ForcedAction
case class GuessConspiracyAction(self : Faction, e : Faction, a : Ambition, i : Int, c : Faction, then : ForcedAction) extends ForcedAction


object ConspiratorExpansion extends FateExpansion(Conspirator) {
    val deck = $(
        GuildCard("f23-02", Farseers),
    )

    val indices = $("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "octopus")

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // CONSPIRATOR III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(ControlTheProceedings)

            f.progress = game.factions.num @@ {
                case 2 => 12
                case 3 => 9
                case 4 => 6
            }

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(Farseers)) --> f.loyal

            f.log("took", f.loyal.last)

            f.abilities :+= Conspiracies

            f.log("gained", f.abilities.last)

            game.laws :+= ScoringConspiracies

            f.log("set", game.laws.last)

            game.laws :+= FoilingConspiracies

            f.log("set", game.laws.last)

            Then(then)

        case DeclareAmbitionMainAction(f, effect, ambitions, marker, zero, _, extra, then) if f.abilities.has(Conspiracies) =>
            val conspired = game.conspired.values.$.flatten
            val colors = $(Blue, Red, White, Yellow)

            Ask(f).group("Conspire Ambition".hl, effect./("with" -> _))
                .some(ambitions) { a =>
                    colors.%(c => game.conspired.values.$.flatten.count(Conspired(c)) < 2)./(c => ConspireAmbitionAction(f, a, c, zero, then).as(a, dt.Arrow, c))
                }
                .add(extra)

        case ConspireAmbitionAction(f, a, c, zero, then) =>
            f.log("conspired", a, "ambition")

            game.conspired += a -> (game.conspired.get(a).|($) ++ $(Conspired(c)))

            if (zero && (f.hasGuild(SecretOrder) && a.in(Keeper, Empath)).not)
                f.zeroed = true

            AmbitionDeclaredAction(f, a, $, then)

        case ReplenishMarketAction(then) if factions.exists(f => f.used.has(Conspiracies)) =>
            val f = factions.%(_.used.has(Conspiracies)).only

            val l = f.loyal.of[GuildCard].%(_.keys < 999)

            val m = game.market.%(_.none).starting

            val next = ClearEffectAction(f, Conspiracies, ReplenishMarketAction(then))

            if (l.any && m.any)
                Ask(f).group("Refill court with", Conspiracies)
                    .each(l)(c => RefillToConspireMainAction(f, c, m.get.index, next).as(c))
                    .skip(next)
            else
                Then(next)

        case RefillToConspireMainAction(f, c, m, then) =>
            DeclareAmbitionMainAction(f, |(c.effect), game.ambitions.%!(game.declared.contains), AmbitionMarker(0, 0), false, false, $(CancelAction), RefillToConspireAction(f, c, m, then))

        case RefillToConspireAction(f, c, m, then) =>
            f.loyal --> c --> Market(m)

            f.log("refilled court with", c)

            f.recalculateSlots()

            Then(then)

        case MayGuessConspiracyAction(f, e, 0, then) =>
            Then(then)

        case MayGuessConspiracyAction(f, e, guesses, then) =>
            game.revealed = Map()

            Ask(f).group(f, "may foil conspiracies")
                .some(game.ambitions) { a =>
                    val l = game.conspired.get(a).|($)
                    l.indexed./~((_, i) => $(Blue, Red, White, Yellow)./(c => GuessConspiracyAction(f, e, a, i, c, MayGuessConspiracyAction(f, e, guesses - 1, then)).as(a, (l.num > 1).?(indices(i))./("(" + _ + ")"), dt.Arrow, c)))
                }
                .done(then)

        case GuessConspiracyAction(f, e, a, i, c, then) =>
            val l = game.conspired(a)

            l(i) @@ {
                case Conspired(`c`) =>
                    f.log("guessed correctly", c, "as", (l.num > 1).?(indices(i)), a, "conspiracy")

                    game.conspired += a -> game.conspired(a).patch(i, $, 1)
                    game.revealed += a -> (game.revealed.get(a).|($) :+ Revealed(c))

                case _ =>
                    f.log("guessed incorrectly", c, "as", (l.num > 1).?(indices(i)), a, "conspiracy")

                    e.advance(1, $("from the guess"))
            }

            then


        // ...
        case _ => UnknownContinue
    }
}
