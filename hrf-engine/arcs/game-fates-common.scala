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


case object MaterialLiaisons extends GuildEffect("Material Liaisons", Material, 2)
case object FuelLiaisons extends GuildEffect("Fuel Liaisons", Fuel, 2)
case object WeaponLiaisons extends GuildEffect("Weapon Liaisons", Weapon, 2)
case object RelicLiaisons extends GuildEffect("Relic Liaisons", Relic, 2)
case object PsionicLiaisons extends GuildEffect("Psionic Liaisons", Psionic, 2)

case object Sycophants extends GuildEffect("Sycophants", Weapon, 999) with LoyalGuild


case class LiaisonsMainAction(self : Faction, a : Ambition, c : GuildCard, then : ForcedAction) extends ForcedAction with Soft
case class LiaisonsAttachAction(self : Faction, c : GuildCard, index : Int, then : ForcedAction) extends ForcedAction

case class BlightLoomsSecuredAction(self : Faction, cluster : Int, then : ForcedAction) extends ForcedAction

case class DiplomaticFiascoMainAction(l : $[Faction], then : ForcedAction) extends ForcedAction with Soft

case class MayGainResourceAction(self : Faction, r : Resource, e : Effect, then : ForcedAction) extends ForcedAction
case class GainFreeResourceAction(self : Faction, r : Resource, e : Effect, then : ForcedAction) extends ForcedAction

case class ReturnCaptivesAction(self : Faction, l : $[Figure], then : ForcedAction) extends ForcedAction


object FatesCommonExpansion extends Expansion {
    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // LIAISONS
        case LiaisonsMainAction(f, a, c, then) =>
            Ask(f).group(c, "declare", a, "attaching to")
                .each(game.market) { m =>
                    DeclareAmbitionMainAction(f, |(c.effect), $(a), game.ambitionable.last, false, false, $(CancelAction), LiaisonsAttachAction(f, c, m.index, then)).as(m.$)
                        .!(Influence(m.index).any, "agents")
                        .!(m.num > 1, "already attached")
                }
                .cancel
                .needOk

        case DeclareAmbitionAction(f, a, m, zero, faithful, LiaisonsAttachAction(ff, c, index, then)) =>
            LiaisonsAttachAction(ff, c, index, DeclareAmbitionAction(f, a, m, zero, faithful, then))

        case LiaisonsAttachAction(f, c, index, then) =>
            f.loyal --> c --> Market(index)

            f.log("attached", c, "to", Market(index).first)

            then

        case MayGainResourceAction(f, r, e, then) =>
            Ask(f).group(e)
                .add(GainFreeResourceAction(f, r, e, then).as("Gain", r))
                .skip(then)

        case GainFreeResourceAction(f, r, e, then) =>
            f.gain(r, $("from", e))

            then

        // POPULIST DEMANDS
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, PopulistDemands), then) =>
            log("Crisis", v)

            if (game.declared.values.any) {
                val marker = game.declared.values.flatten.maxBy(_.high)

                game.declared = game.declared.view.mapValues(_.but(marker)).filter(_._2.any).toMap
                game.ambitionable :+= marker

                log("Marker", marker.high.styled(styles.titleW) ~ "/" ~ marker.low.styled(styles.titleW), "was undeclared")
            }

            CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

        case GainCourtCardAction(f, v @ VoxCard(_, PopulistDemands), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, then)

            if (game.ambitionable.any)
                DeclareAmbitionMainAction(f, |(PopulistDemands), game.ambitions, game.ambitionable.last, false, false, $(SkipAction(next)), next)
            else
                Ask(f).group("Declare Ambition".hl, "with", v).add(next.as("No Ambition Markers")).needOk

        // COUNCIL INTRIGUE
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, CouncilIntrigue), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            if (game.council.has(ImperialCouncilDecided)) {
                game.council --> ImperialCouncilDecided --> game.sidedeck

                game.sidedeck --> ImperialCouncilInSession --> game.council

                game.council.$.dropLast --> game.council

                if (game.decided.any) {
                    game.decided = None

                    log("Council was cancelled")
                }
                else
                    log("Council went in session")
            }

            if (main) {
                Influence(lane).foreach { u =>
                    u --> Influence(0)

                    log(u, "moved to", ImperialCouncilInSession)
                }
            }

            next

        case GainCourtCardAction(f, v @ VoxCard(_, CouncilIntrigue), lane, main, then) =>
            if (game.council.has(ImperialCouncilDecided)) {
                game.council --> ImperialCouncilDecided --> game.sidedeck

                game.sidedeck --> ImperialCouncilInSession --> game.council

                game.council.$.dropLast --> game.council

                if (game.decided.any) {
                    game.decided = None

                    log("Council was cancelled")
                }
                else
                    log("Council went in session")
            }

            val next = BuryVoxCardAction(f, v, then)

            if (main)
                Then(SpreadAgentsAction(f, lane, next))
            else
                Then(next)

        // SONG OF FREEDOM
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, SongOfFreedom), then) =>
            val next : ForcedAction = CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

            val f = factions.%(_.power >= factions./(_.power).max).first

            log(SingleLine)

            log("Crises", SongOfFreedom, "for", f)

            val prefix = f.short + "-"

            Ask(f).group(SongOfFreedom)
                .some(systems)(s => f.at(s).cities./(u => FreeCityAction(f, s, u, next).as(City.of(f), Image(prefix + "city" + f.damaged.has(u).??("-damaged"), styles.qbuilding), "in", s)))
                .bailw(next) {
                    f.log("had no", "Cities".styled(f))
                }

        case GainCourtCardAction(f, v @ VoxCard(_, SongOfFreedom), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, ShuffleCourtDeckAction(then))

            val l = systems.%(f.rules)

            Ask(f).group(v, "frees a", "City".hl)
                .some(l)(s => factions./~(_.at(s).cities)./(u => FreeCityAction(f, s, u, FreeCitySeizeAskAction(f, next)).as(u, "in", s)))
                .skip(next)

        case FreeCityAction(f, s, u, then) =>
            f.log("freed", u, "in", s)

            val damaged = f.damaged.has(u)

            u --> u.faction.reserve

            if (damaged)
                f.damaged :-= u

            game.onRemoveFigure(u)

            val n = Free.reserve --> City

            n --> s

            if (damaged)
                Free.damaged :+= n

            u.faction.as[Faction].foreach { e =>
                e.recalculateSlots()
            }

            AdjustResourcesAction(then)

        case FreeCitySeizeAskAction(f, then) =>
            if (game.seized.none && factions.first != f)
                Ask(f).group(SongOfFreedom)
                    .add(FreeCitySeizeAction(f, then).as("Seize Initiative".hl))
                    .skip(then)
            else
                NoAsk(f)(then)

        case FreeCitySeizeAction(f, then) =>
            game.seized = |(f)

            f.log("seized the initative")

            if (f.objective.has(SowDivision))
                f.advance(1, $("seizing initative"))

            then

        // DIPLOMATIC FIASCO
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, DiplomaticFiasco), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log(SingleLine)

            log("Crises", DiplomaticFiasco)

            DiplomaticFiascoMainAction(factions, next)

        case DiplomaticFiascoMainAction(Nil, next) =>
            Then(next)

        case DiplomaticFiascoMainAction(factions, next) =>
            val f = factions.first

            if (f.reserve.$.agents.num < f.loyal.num)
                Ask(f).group(DiplomaticFiasco, f.reserve.$.agents./(u => game.showFigure(u)).merge)
                    .each(f.loyal.$.of[GuildCard])(c => DiscardGuildCardAction(f, c, DiplomaticFiascoMainAction(factions, next)).as((c.keys < 999).?("Discard"), c).!(c.keys >= 999))
                    .bailw(DiplomaticFiascoMainAction(factions.drop(1), next)) {
                        f.log("had only locked guild cards")
                    }
            else
                Then(DiplomaticFiascoMainAction(factions.drop(1), next))

        case GainCourtCardAction(f, v @ VoxCard(_, DiplomaticFiasco), lane, main, then) =>
            DiplomaticFiascoMainAction(factions.but(f), BuryVoxCardAction(f, v, then))

        // BLIGHT LOOMS
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, BlightLooms), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log(SingleLine)

            log("Crises", v)

            systems.%(_.cluster == cluster).foreach { s =>
                Blights.at(s).foreach { u =>
                    if (Blights.damaged.has(u)) {
                        Blights.damaged :-= u

                        Blights.log("repaired in", s)
                    }
                }
            }

            systems.%(_.symbol == symbol).foreach { s =>
                if (Blights.at(s).none) {
                    val u = Blights.reserve --> Blight

                    u --> s

                    Blights.damaged :+= u

                    Blights.log("appeared in", s)
                }
            }

            next

        case GainCourtCardAction(f, v @ VoxCard(_, BlightLooms), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, then)

            val l1 = systems.%(f.present)
            val l2 = systems.%(Blights.at(_).damaged.any)

            val cc = l1./(_.cluster).distinct `intersect` l2./(_.cluster).distinct

            Ask(f).group(v, "destroys all damaged Blight in")
                .each(cc)(i => BlightLoomsSecuredAction(f, i, next).as("Cluster".hl, i.hlb))
                .skip(next)
                .needOk

        case BlightLoomsSecuredAction(f, i, then) =>
            systems.%(_.cluster == i).foreach { s =>
                Blights.at(s).damaged.foreach { u =>
                    f.log("destroyed", u, "in", s)

                    u --> f.trophies

                    Blights.damaged :-= u
                }
            }

            then

        case ReturnCaptivesAction(f, l, then) =>
            l.foreach { u =>
                u --> u.faction.reserve
            }

            f.log("returned captives", l.comma)

            then


        // ...
        case _ => UnknownContinue
    }
}
