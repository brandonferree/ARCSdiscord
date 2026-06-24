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


case object Judge extends Fate("Judge", "fate24", 3) {
    override val expansion = JudgeExpansion
}

case object BalanceTheReach extends Objective("f24-01b", "Balance the Reach")

case object VowOfFairness extends Lore("f24-02", "Vow of Fairness")
case object TheArbiter extends GuildEffect("The Arbiter", Relic, 999)
case object JudgesChosen extends Ability("f24-04", "Judge's Chosen")
case object JudgesOwnChosen extends Ability("f24-04", "Judge's Chosen")
case object JudgeSetsAgenda extends Edict("f24-05", "Judge Sets Agenda", "15")


case class JugdeAgendaAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class AmbitionMarkerMainAction(self : Faction, m : AmbitionMarker, then : ForcedAction) extends ForcedAction with Soft
case class AmbitionMarkerDeclareAction(self : Faction, m : AmbitionMarker, then : ForcedAction) extends ForcedAction

case class AssignChosenMainAction(self : Faction, then : ForcedAction) extends ForcedAction
case class AssignChosenAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction

case class ArbiterGiveResourceMainAction(self : Faction, r : ResourceToken, then : ForcedAction) extends ForcedAction with Soft
case class ArbiterGiveResourceAction(self : Faction, r : ResourceToken, e : Faction, then : ForcedAction) extends ForcedAction

case class ArbiterGiveTrophyMainAction(self : Faction, u : Figure, then : ForcedAction) extends ForcedAction with Soft
case class ArbiterGiveTrophyAction(self : Faction, u : Figure, e : Faction, then : ForcedAction) extends ForcedAction

case class ArbiterGiveCaptiveMainAction(self : Faction, u : Figure, then : ForcedAction) extends ForcedAction with Soft
case class ArbiterGiveCaptiveAction(self : Faction, u : Figure, e : Faction, then : ForcedAction) extends ForcedAction


object JudgeExpansion extends FateExpansion(Judge) {
    val deck = $(
        VowOfFairness,
        GuildCard("f24-03", TheArbiter),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // JUDGE III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(BalanceTheReach)

            f.progress = 6

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate) --> VowOfFairness --> f.lores

            f.log("got", f.lores.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(TheArbiter)) --> f.loyal

            f.log("took", f.loyal.last)

            f.abilities :+= JudgesOwnChosen

            f.log("gained", f.abilities.last)

            game.edicts :+= JudgeSetsAgenda

            f.log("added", game.edicts.last, "edict")

            Then(then)

        case ResolveEdictAction(priority, then) if priority == JudgeSetsAgenda.priority =>
            val next = ResolveNextEdictAction(priority, then)

            val f = factions.%(f => f.fates.has(Judge)).only

            if (f.abilities.has(JudgesOwnChosen).not)
                f.abilities :+= JudgesOwnChosen

            factions.foreach { e =>
                if (e.abilities.has(JudgesChosen))
                    e.abilities :-= JudgesChosen
            }

            JugdeAgendaAction(f, next)

        case JugdeAgendaAction(f, then) =>
            Ask(f).group("Declare ambition with", JudgeSetsAgenda)
                .each(game.ambitionable)(m => AmbitionMarkerMainAction(f, m, then).as(Image("ambition-values-" + m.high + "-" + m.low, styles.qbuilding)))
                .skip(AssignChosenMainAction(f, then))

        case AmbitionMarkerMainAction(f, m, then) =>
            DeclareAmbitionMainAction(f, |(JudgeSetsAgenda), game.ambitions, m, false, false, $(CancelAction), AmbitionMarkerDeclareAction(f, m, AssignChosenMainAction(f, then)))

        case DeclareAmbitionAction(f, a, m, zero, faithful, AmbitionMarkerDeclareAction(ff, mm, then)) =>
            AmbitionMarkerDeclareAction(ff, mm, DeclareAmbitionAction(f, a, m, zero, faithful, then))

        case AmbitionMarkerDeclareAction(f, mm, then) =>
            f.power -= mm.low

            f.log("lost", mm.low.power)

            then

        case AssignChosenMainAction(f, then) =>
            Ask(f).group("Choose", JudgesChosen)
                .each(f.rivals)(e => AssignChosenAction(f, e, then).as(e))

        case AssignChosenAction(f, e, then) =>
            f.abilities :-= JudgesOwnChosen

            e.abilities :+= JudgesChosen

            f.log("chose", e, "as", JudgesChosen)

            then

        case ScoreAmbitionsAction if factions.%(f => f.hasGuild(TheArbiter) && f.used.has(TheArbiter).not).any =>
            val next = ScoreAmbitionsAction
            val f = factions.%(f => f.hasGuild(TheArbiter) && f.used.has(TheArbiter).not).first

            Ask(f).group(TheArbiter, "gives")
                .each(f.spendable.resources)((r, k) => ArbiterGiveResourceMainAction(f, r, next).as(r -> k).!!!)
                .each(f.trophies)(u => ArbiterGiveTrophyMainAction(f, u, next).as(u, game.showFigure(u, 1)))
                .each(f.captives)(u => ArbiterGiveCaptiveMainAction(f, u, next).as(u, game.showFigure(u)))
                .done(AdjustResourcesAction(UseEffectAction(f, TheArbiter, next)))

        case ArbiterGiveResourceMainAction(f, r, then) =>
            Ask(f).group(TheArbiter, "gives", r, "to")
                .each(f.rivals)(e => ArbiterGiveResourceAction(f, r, e, then).as(e).!(e.spendable.exists(s => s.canHold(r) && s.canHoldMore).not))
                .cancel

        case ArbiterGiveResourceAction(f, r, e, then) =>
            e.take(r)

            f.log("gave", r, "to", e)

            then

        case ArbiterGiveTrophyMainAction(f, u, then) =>
            Ask(f).group(TheArbiter, "gives trophy", u, "to")
                .each(f.rivals)(e => ArbiterGiveTrophyAction(f, u, e, then).as(e))
                .cancel

        case ArbiterGiveTrophyAction(f, u, e, then) =>
            if (u.faction == e)
                u --> e.reserve
            else
                u --> e.trophies

            f.log("gave trophy", u, "to", e)

            then

        case ArbiterGiveCaptiveMainAction(f, u, then) =>
            Ask(f).group(TheArbiter, "gives captive", u, "to")
                .each(f.rivals)(e => ArbiterGiveCaptiveAction(f, u, e, then).as(e))
                .cancel

        case ArbiterGiveCaptiveAction(f, u, e, then) =>
            if (u.faction == e)
                u --> e.reserve
            else
                u --> e.captives

            e.log("gave captive", u, "to", e)

            then


        // ...
        case _ => UnknownContinue
    }
}
