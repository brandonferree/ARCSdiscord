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


case object Bunker extends Piece


case object Survivalist extends Fate("Survivalist", "fate18", 3) {
    override val expansion = SurvivalistExpansion
}


case object KeepThePeopleSafe extends Objective("f18-01b", "Keep the People Safe")

case object VowOfSurvival extends Lore("f18-02", "Vow of Survival")
case object BuildingBunkers extends Ability("f18-03", "Building Bunkers")
case object Bunkers extends Law("f18-04", "Bunkers")
case object CaptiveRelease extends Edict("f18-05", "Captive Release", "85")


case class BuildBunkerMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class BuildBunkerAction(self : Faction, cost : Cost, s : System, then : ForcedAction) extends ForcedAction
case class BuildBunkerCaptureAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction

case class CaptiveReleaseMainAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction
case class CaptiveReleaseAction(self : Faction, e : Faction, s : System, then : ForcedAction) extends ForcedAction
case class CaptiveReleaseReleaseAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction
case class CaptiveReleaseOutrageAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction

case class ScrapCaptiveMainAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction
case class ScrapCaptiveAction(self : Faction, e : Faction, u : Figure, then : ForcedAction) extends ForcedAction


object SurvivalistExpansion extends FateExpansion(Survivalist) {
    val deck = $(
        VowOfSurvival,
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // SURVIVALIST III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(KeepThePeopleSafe)

            f.progress = game.factions.num @@ {
                case 2 => 22
                case 3 => 18
                case 4 => 14
            }

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate) --> VowOfSurvival --> f.lores

            f.log("got", f.lores.last)

            f.abilities :+= BuildingBunkers

            f.log("gained", f.abilities.last)

            game.laws :+= Bunkers

            f.log("set", game.laws.last)

            val units = game.figures.register(FatePieces(fate), content = 1.to(9)./(Figure(f, Bunker, _)))

            units --> f.reserve

            f.log("took", Bunker.sof(f))

            game.edicts :+= CaptiveRelease

            f.log("added", game.edicts.last, "edict")

            Then(then)


        case BuildBunkerMainAction(f, cost, then) =>
            var present =
                if (f.officers)
                    systems.%(s => f.present(s) || Empire.present(s))
                else
                    systems.%(f.present)

            Ask(f).group("Build", Bunker.of(f), "in")
                .each(present.%!(_.gate).%(f.at(_).bunkers.none))(s => BuildBunkerAction(f, cost, s, then).as(s))
                .cancel

        case BuildBunkerAction(f, x, s, then) =>
            f.pay(x)

            val u = f.reserve --> Bunker

            if (f.rivals.exists(_.rules(s)) || campaign.&&(f.regent.not && Empire.rules(s)))
                f.damaged :+= u

            u --> s

            f.log("built", u, "in", s, x)

            if (f.reserve.$.piece(Agent).none) {
                f.log("had no agents to scrap")
            }

            if (f.reserve.$.piece(Agent).any) {
                f.reserve --> Agent --> Scrap

                f.log("scrapped", Agent.of(f))
            }

            Ask(f).group("Capture", Agent.name.hh)
                .each(f.rivals.%(_.pool(Agent)))(e => BuildBunkerCaptureAction(f, e, then).as(Agent.of(e)))
                .bailw(then) {
                    f.log("could not capture any", Agent.name.hh)
                }

        case BuildBunkerCaptureAction(f, e, then) =>
            e.reserve --> Agent --> f.captives

            f.log("captured", Agent.of(e))

            then

        case ResolveEdictAction(priority, then) if priority == CaptiveRelease.priority =>
            val next = ResolveNextEdictAction(priority, then)

            val survivalist = factions.%(_.fates.has(Survivalist)).only

            val releasers = factions.but(survivalist).%(_.power > survivalist.power).%(survivalist.captives.$.ofc(_).any)

            if (releasers.any)
                releasers.foldLeft(next : ForcedAction)((q, e) => CaptiveReleaseMainAction(e, survivalist, q))
            else
                next

        case CaptiveReleaseMainAction(f, e, then) =>
            val l = systems.%(s => e.at(s).bunkers.any).%(f.rules)

            if (l.any)
                Ask(f).group(CaptiveRelease, "in")
                    .each(l)(s => CaptiveReleaseAction(f, e, s, then).as(s))
            else
                Then(then)

        case CaptiveReleaseAction(f, e, s, then) =>
            f.log("demanded", CaptiveRelease, "in", s)

            Ask(e).group(CaptiveRelease, "in", s)
                .add(CaptiveReleaseReleaseAction(e, f, then).as("Release", e.captives.$.ofc(f)./(game.showFigure)))
                .add(CaptiveReleaseOutrageAction(e, s, then).as("Outrage", s, game.resources(s)))

        case CaptiveReleaseReleaseAction(f, e, then) =>
            val l = f.captives.$.ofc(e)

            l --> e.reserve

            f.log("released", l.intersperse(Comma))

            then

        case CaptiveReleaseOutrageAction(f, s, then) =>
            game.resources(s).distinct.foldLeft(then)((q, r) => OutrageAction(f, r, |(CaptiveRelease), q))

        case CleanUpChapterAction(then)
        if game.declared.contains(Tyrant) && factions.exists(f =>
            f.hasLore(VowOfSurvival) && f.captives.num > systems./(f.at(_).bunkers.num).sum && systems./(f.at(_).bunkers.num).sum > 0
        ) =>
            val f = factions.%(f => f.hasLore(VowOfSurvival) && f.captives.num > systems./(f.at(_).bunkers.num).sum).first
            val n = systems./(f.at(_).bunkers.num).sum

            implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, selected.??(2))

            XXSelectObjectsAction(f, f.captives)
                .withGroup("Keep captives with", VowOfSurvival)
                .withRule(_.num(n))
                .withThen(l => ReturnCaptivesAction(f, f.captives.$.diff(l), CleanUpChapterAction(then)))(l => ("Keep", l.intersperse(Comma)))

        case ScrapCaptiveMainAction(f, e, then) =>
            if (e.captives.any) {
                implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, selected.??(2))

                YYSelectObjectsAction(f, e.captives)
                    .withGroup("Scrap captive of", e)
                    .withThen(u => ScrapCaptiveAction(f, e, u, then))(u => ("Scrap", u))("Scrap")
            }
            else
                Then(then)

        case ScrapCaptiveAction(f, e, u, then) =>
            f.log("scapped", u, Comma, "captive of", e)

            e.captives --> u --> Scrap

            then


        // ...
        case _ => UnknownContinue
    }
}
