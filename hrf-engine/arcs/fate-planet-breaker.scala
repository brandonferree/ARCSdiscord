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


case object PlanetBreaker extends Fate("Planet Breaker", "fate11", 2) {
    override val expansion = PlanetBreakerExpansion
}


case object CrushAPlanet extends Objective("f11-01b", "Crush a Planet")

case object HammerFragment extends Piece
case object HammerToken extends Piece
case object BrokenWorld extends Piece

case object HammerFragments extends Ability("f11-03a", "Hammer Fragments")
case object BreakingWorlds extends Ability("f11-03b", "Breaking Worlds") with SpecialRegion
case object BreakingWorldsLaw extends Law("f11-03b", "Breaking Worlds")
case object PlanetHammer extends Lore("f11-04", "Planet Hammer")

abstract class Refugees(name : String, suit : Resource) extends GuildEffect(name, suit, 2)
case object ForgeworldRefugees extends Refugees("Forgeworld Refugees", Material)
case object BlazeworldRefugees extends Refugees("Blazeworld Refugees", Fuel)
case object DeadworldRefugees  extends Refugees("Deadworld Refugees",  Weapon)
case object LostworldRefugees  extends Refugees("Lostworld Refugees",  Relic)
case object HeartworldRefugees extends Refugees("Heartworld Refugees", Psionic)

case object PlanetEaterLoose extends VoxEffect("Planet-Eater Loose")


case object BreakTheWorldsOfTheReach extends Objective("f11-12b", "Break the Worlds of the Reach")


case class HammerSetupMainAction(then : ForcedAction) extends ForcedAction
case class HammerSetupAction(random : Symbol, then : ForcedAction) extends RandomAction[Symbol]

case class RepairHammerMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class RepairHammerAction(self : Faction, x : Cost, s : System, then : ForcedAction) extends ForcedAction

case class PrepareBreakWorldMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class PrepareBreakWorldAction(self : Faction, x : Cost, s : System, then : ForcedAction) extends ForcedAction

case class BreakWorldAction(self : Faction, x : Cost, s : System, then : ForcedAction) extends ForcedAction
case class BreakWorldMayBattleAction(self : Faction, e : Faction, s : System, then : ForcedAction) extends ForcedAction
case class BreakWorldCompleteAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction
case class BreakWorldResolveAction(self : Color, s : System, then : ForcedAction) extends ForcedAction

case class PlanetEaterLooseRolledAction(cluster : Int, symbol : Symbol, random : Symbol, then : ForcedAction) extends RandomAction[Symbol]
case class PlanetEaterLooseProcessAction(cluster : Int, symbol : Symbol, random : Symbol, then : ForcedAction) extends ForcedAction
case class PlanetEaterLooseReRollAction(cluster : Int, symbol : Symbol, random : Symbol, then : ForcedAction) extends ForcedAction
case class PlanetEaterLooseBattleAction(self : Faction, rolled : Rolled, v : VoxCard, stay : ForcedAction, then : ForcedAction) extends RolledAction[$[BattleResult]]

case class ResettleRefugeesMainAction(self : Faction, x : Cost, r : Resource, e : GuildEffect, then : ForcedAction) extends ForcedAction with Soft
case class ResettleRefugeesAction(self : Faction, x : Cost, s : System, cloud : Boolean, r : Resource, e : GuildEffect, then : ForcedAction) extends ForcedAction


object PlanetBreakerExpansion extends FateExpansion(PlanetBreaker) {
    val deck = $(
        GuildCard("f11-02", Sycophants),
        PlanetHammer,
        GuildCard("f11-05", ForgeworldRefugees),
        GuildCard("f11-06", BlazeworldRefugees),
        GuildCard("f11-07", DeadworldRefugees),
        GuildCard("f11-08", LostworldRefugees),
        GuildCard("f11-09", HeartworldRefugees),
        VoxCard("f11-11", PlanetEaterLoose),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // PLANET BREAKER II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(CrushAPlanet)

            f.progress = 12

            f.log("objective was set to", f.progress.hlb)

            f.abilities :+= HammerFragments

            f.log("gained", f.abilities.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(Sycophants)) --> f.loyal

            f.log("took", f.loyal.last)

            val units = game.figures.register(FatePieces(fate), content =
                1.to(6)./(Figure(Neutrals, HammerFragment, _)) ++
                1.to(1)./(Figure(Neutrals, HammerToken, _)) ++
                1.to(18)./(Figure(Neutrals, BrokenWorld, _))
            )

            game.figures.register(BreakingWorlds)

            SetupFlagshipMainAction(f, $(|(Starport), None, None, None, None, None), HammerSetupMainAction(then))

        case HammerSetupMainAction(then) =>
            Random[Symbol]($(Arrow, Crescent, Hex), HammerSetupAction(_, then))

        case HammerSetupAction(s, then) =>
            systems.%(_.symbol == s).foreach { s =>
                FatePieces(fate) --> HammerFragment --> s

                log("Hammer Fragment".hh, "was placed in", s)
            }

            then

        case RepairHammerMainAction(f, x, then) =>
            Ask(f).group("Repair Hammer".hl)
                .each(systems.%(_.$.hasA(HammerFragment)))(s => RepairHammerAction(f, x, s, then).as(s).!(f.rules(s).not, "no control"))
                .cancel

        case RepairHammerAction(f, x, s, then) =>
            f.pay(x)

            s.$.piece(HammerFragment) --> FatePieces(fate)

            f.log("repaired", "Hammer".hh, "in", s, x)

            if (f.objective.has(CrushAPlanet))
                f.advance(1, $("reparing hammer"))

            if (f.pool(Ship)) {
                f.reserve --> Ship --> s

                f.log("placed", Ship.of(f), "in", s)
            }

            if (FatePieces(fate).$.piece(HammerFragment).num == 6) {
                f.abilities :-= HammerFragments
                f.abilities :+= BreakingWorlds

                f.log("replaced", HammerFragments, "with", BreakingWorlds)

                FateDeck(fate) --> PlanetHammer --> f.lores

                f.log("got", f.lores.last)
            }

            then

        case PrepareBreakWorldMainAction(f, x, then) =>
            Ask(f).group("Prepare to Break World".hh)
                .each(systems.%!(_.gate).%(f.rules))(s => PrepareBreakWorldAction(f, x, s, then).as(s))
                .cancel

        case PrepareBreakWorldAction(f, x, s, then) =>
            f.pay(x)

            systems.foreach { s =>
                s.$.piece(HammerToken) --> FatePieces(fate)
            }

            FatePieces(fate).$.piece(HammerToken) --> s

            f.used :+= PlanetHammer

            f.log("prepared to break world", s)

            then

        case BreakWorldAction(f, x, s, then) =>
            f.pay(x)

            f.log("tried to break world in", s)

            f.rivals.reverse.foldLeft(BreakWorldCompleteAction(f, s, then) : ForcedAction)((q, e) => BreakWorldMayBattleAction(e, f, s, q))

        case BreakWorldMayBattleAction(f, e, s, then) =>
            val officers = f.regent && f.officers

            val own = f.at(s).shiplikes.any
            val imperial = f.regent.??(Empire.at(s).ships.any && (f.present(s) || officers))

            if (own.not && imperial.not) {
                f.log("was not there to stop", e, "in", s)

                Then(then)
            }
            else
            if (f.canHarm(e, s).not) {
                f.log("could not harm", e, "in", s)

                Then(then)
            }
            else
                Ask(f).group("Battle", e, "trying to break world in", s)
                    .when(imperial)(TryHarmAction(f, e, s, NoCost, BattleFactionAction(f, NoCost, |(PlanetHammer), s, $(Empire), e, then)).as(f, "and", Empire))
                    .when(own)(TryHarmAction(f, e, s, NoCost, BattleFactionAction(f, NoCost, |(PlanetHammer), s, $, e, then)).as(f))
                    .skip(then)

        case BreakWorldCompleteAction(f, s, then) =>
            if (f.rules(s)) {
                f.log("broke world in", s)

                factions.foldLeft(BreakWorldResolveAction(f, s, then) : ForcedAction)((q, e) => game.resources(s).foldLeft(q)((q, r) => OutrageAction(e, r, None, q)))
            }
            else {
                f.log("could not break world in", s)

                then
            }

        case BreakWorldResolveAction(f, s, then) =>
            val suits = game.resources(s)

            s.$.buildinglikes.some.foreach { l =>
                log(f.as[Faction].|("Planet Eater".hl), "permanently destroyed", l.intersperse(Comma))

                l --> BreakingWorlds
            }

            FateDeck(fate).$.of[GuildCard].%(_.suit.in(suits)).%(_.effect.is[Refugees]).foreach { c =>
                val l = game.court.$

                FateDeck(fate) --> c --> game.court
                l --> CourtScrap
                l --> game.court

                log(c, "were placed on top of the court deck")
            }

            game.market.foreach { m =>
                if (m.starting./~(_.as[GuildCard]).?(_.suit.in(suits))) {
                    Influence(m.index).groupBy(_.faction).$./ { (e, l) =>
                        l --> e.reserve

                        e.log("returned", l.intersperse(Comma))
                    }
                }

                m.%(_.as[GuildCard].?(_.suit.in(suits)))./ { c =>
                    c --> CourtScrap

                    log(c, "was scrapped")
                }
            }

            game.brokenPlanets :+= s

            FatePieces(fate) --> BrokenWorld --> s

            s.$.piece(HammerToken) --> FatePieces(fate)

            f.as[Faction].foreach { f =>
                if (f.objective.has(CrushAPlanet))
                    f.advance(6, $("breaking a world"))
            }

            ReplenishMarketAction(then)

        case GainCourtCardAction(f, c @ GuildCard(_, e : Refugees), _, _, then) if f.hasLore(PlanetHammer) =>
            (c : CourtCard) --> CourtScrap

            f.log("scrapped", c)

            then

        case FateFailAction(f, `fate`, 2, then) =>
            if (FatePieces(fate).$.piece(HammerFragment).num < 6) {
                f.abilities :-= HammerFragments
                f.abilities :-= BreakingWorlds

                systems.foreach { s =>
                    s.$.piece(HammerFragment) --> FatePieces(fate)
                }

                f.log("replaced", HammerFragments, "with", BreakingWorlds)
            }
            else {
                f.lores --> PlanetHammer --> FateDeck(fate)

                f.log("lost", PlanetHammer)
            }

            game.laws :+= BreakingWorldsLaw

            f.log("set", game.laws.last)

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(PlanetEaterLoose)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            val l = BreakingWorlds.$

            if (l.any) {
                f.log("recorded", l.intersperse(Comma), "on", BreakingWorlds, "card")
            }

            Then(then)

        // PLANET BREAKER II LEGACY
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, PlanetEaterLoose), then) =>
            log("Crisis", v)

            val next : ForcedAction = CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

            Random[Symbol]($(Arrow, Crescent, Hex), PlanetEaterLooseRolledAction(cluster, symbol, _, then))

        case PlanetEaterLooseRolledAction(cluster, symbol, rolled, then) if game.declared.contains(Empath) && factions.first.hasLore(EmpathsVision) && factions.first.used.has(EmpathsVision).not =>
            log("Rolled", rolled.name.hh, rolled.smb.hl)

            val f = factions.first

            Ask(f).group(PlanetEaterLoose, System(cluster, symbol), HorizontalBreak, EmpathsVision)
                .add(PlanetEaterLooseReRollAction(cluster, symbol, rolled, then).as("Reroll", rolled.smb.hl))
                .add(PlanetEaterLooseProcessAction(cluster, symbol, rolled, then).as("Skip"))

        case PlanetEaterLooseReRollAction(cluster, symbol, rolled, then) =>
            val f = factions.first

            log(f, "rerolled with", EmpathsVision)

            f.used :+= EmpathsVision

            Random[Symbol]($(Arrow, Crescent, Hex), PlanetEaterLooseRolledAction(cluster, symbol, _, then))

        case PlanetEaterLooseRolledAction(cluster, symbol, rolled, then) =>
            log("Rolled", rolled.name.hh, rolled.smb.hl)

            val f = factions.first

            f.used :-= EmpathsVision

            PlanetEaterLooseProcessAction(cluster, symbol, rolled, then)

        case PlanetEaterLooseProcessAction(cluster, symbol, rolled, then) =>
            if (symbol == rolled) {
                val s = System(cluster, symbol)

                log(PlanetEaterLoose, "broke world in", s)

                factions.foldLeft(BreakWorldResolveAction(Blights, s, then) : ForcedAction)((q, e) => game.resources(s).foldLeft(q)((q, r) => OutrageAction(e, r, |(PlanetEaterLoose), q)))
            }
            else
                then

        case GainCourtCardAction(f, v @ VoxCard(_, PlanetEaterLoose), lane, false, then) =>
            then

        case GainCourtCardAction(f, v @ VoxCard(_, PlanetEaterLoose), lane, true, then) =>
            val stay : ForcedAction = then match {
                case CaptureAgentsCourtCardAction(_, _, ReplenishMarketAction(out)) => out
                case ExecuteAgentsCourtCardAction(_, _ ,ReplenishMarketAction(out)) => out
                case ReturnAgentsCourtCardAction(_, ReplenishMarketAction(out)) => out
                case _ => throw new Error("PlanetEaterLoose unknown THEN: " + then)
            }

            if (Influence(lane).num < 3) {
                log("Not enough agents to stop", "Planet Eater".hl)

                stay
            }
            else
                Roll[$[BattleResult]](Influence(lane).num.times(Skirmish.die), l => PlanetEaterLooseBattleAction(f, l, v, stay, then))

        // case PlanetEaterLooseBattleAction(f, rolled, v, stay, then) if game.declared.contains(Empath) && f.hasLore(EmpathsVision) && f.used.has(EmpathsVision).not =>
        //     Ask(f).group(EmpathsVision)
        //         .add()

        case PlanetEaterLooseBattleAction(f, rolled, v, stay, then) =>
            f.log("rolled", rolled./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)))

            if (rolled.flatten.count(HitShip) >= 3) {
                f.log("subdued", "Planet Eater".hl)

                f.power += 3

                f.log("gained", 3.power)

                val c = game.deck.take(1)

                if (c.any) {
                    c --> f.hand

                    f.log("drew a card")
                }

                BuryVoxCardAction(f, v, then)
            }
            else {
                f.log("failed to subdue", "Planet Eater".hl)

                stay
            }

        case ResettleRefugeesMainAction(f, x, resource, e, then) =>
            val control = systems.%!(_.gate).%(s => game.resources(s).has(resource)).%(f.rules)
            val slots = control.%(s => game.freeSlots(s) > 0)

            var cities = slots
            var clouds = f.hasLore(CloudCities).??(control.%(_.$.cities.intersect(game.unslotted).none))

            val resources = f.spendable.resources.%<!(x.as[PayResource]./(_.resource).has)

            Ask(f).group(e, x)
                .each(cities)(s => ResettleRefugeesAction(f, x, s, false, resource, e, then).as(City.of(Free), Image("free-city", styles.qbuilding), "in", s))
                .some(clouds)(s => resources.%((r, k) => game.resources(s).has(r.resource))./((r, k) =>
                    ResettleRefugeesAction(f, MultiCost(x, PayResource(r, k)), s, true, resource, e, then).as("Cloud".styled(Free), City.of(Free), Image("free-city", styles.qbuilding), "out of slot in", s, "with", r -> k))
                )
                .cancel

        case ResettleRefugeesAction(f, x, s, cloud, r, e, then) =>
            f.pay(x)

            val u = Free.reserve --> City

            u --> s

            if (cloud)
                game.unslotted :+= u
            else
            if (f.taxed.slots.has(s))
                f.taxed.slots :-= s

            f.log("resettled", u, "in", s, cloud.?("with" -> CloudCities), x)

            if (f.outraged.has(r)) {
                f.outraged :-= r

                f.log("cleared", r, "outrage")
            }

            while (f.spendable.%(s => s.canHold(r) && s.canHoldMore).but(PirateHoardSlots).any && game.available(r)) {
                f.gain(r, $)
            }

            val l = f.loyal.%(_.as[GuildCard]./(_.effect).has(e))

            l --> FateDeck(fate)

            f.log("returned", l)

            AdjustResourcesAction(then)

        // PLANET BREAKER III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(BreakTheWorldsOfTheReach)

            f.progress = 0

            Then(then)

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            if (factions.forall(_.outraged.any)) {
                f.grand += 1

                f.log("fulfilled a grand ambition speading", "Outrage".hh)
            }

            if (BreakingWorlds.$.buildings.num > game.chapter || systems./(_.$.buildings.num).sum == 0) {
                f.grand += 1

                f.log("fulfilled a grand ambition with", "broken buildings".hh)
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
