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


case object Pirate extends Fate("Pirate", "fate12", 2) {
    override val expansion = PirateExpansion
}


case object PirateHoardSlots extends ResourceSlot with Elementary {
    override def canHold(t : ResourceLike)(implicit game : Game) = t.isResource
    override val raidable = |(2)
    def elem = PirateHoard.elem
}


case object GrowYourHoard extends Objective("f12-01b", "Grow Your Hoard")

case object PirateRaid extends Effect

case object PirateHoard extends Lore("f12-02", "Pirate Hoard")
case object PirateFleet extends GuildEffect("Pirate Fleet", Weapon, 999)
case object PirateSmugglers extends GuildEffect("Pirate Smugglers", Weapon, 2)
case object CallToAction extends VoxEffect("Call to Action")


trait Rumor extends Piece
case object FalseClueRumor extends Rumor {
    override val name = "False Clue"
}
case object ClusterCorrectRumor extends Rumor {
    override val name = "Cluster Correct"
}
case object SymbolCorrectRumor extends Rumor {
    override val name = "Symbol Correct"
}


case object LaughAtYourRivalsFolly extends Objective("f12-08b", "Laugh at Your Rivals' Folly")

case object RumorsSpread extends VoxEffect("Rumors Spread")
case object RumorsOfTheHaven extends Law("f12-11", "Rumors of the Haven")
case object SharingRumors extends Law("f12-12", "Breaking Worlds")
case object RaidRumor extends Effect


case class PirateReplenishMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft

case class PirateSpentHoardMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class PirateTakeHoardAction(self : Faction, then : ForcedAction) extends ForcedAction

case class SetupRumorsMainAction(self : Faction, then : ForcedAction) extends ForcedAction
case class PlaceRumorMainAction(self : Faction, p : Piece, then : ForcedAction) extends ForcedAction with Soft
case class PlaceRumorAction(self : Faction, p : Piece, s : System, then : ForcedAction) extends ForcedAction

case class SpreadRumorAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction

case class LaughAction(self : Faction, noRival : Boolean, aLoyal : Boolean, then : ForcedAction) extends ForcedAction

case class GuessHavenMainAction(self : Faction, then : ForcedAction) extends ForcedAction
case class GuessHavenAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction
case class CheckHavenAction(self : Faction) extends ForcedAction


object PirateExpansion extends FateExpansion(Pirate) {
    val deck = $(
        PirateHoard,
        GuildCard("f12-03", PirateFleet),
        GuildCard("f12-05", PirateSmugglers),
        VoxCard("f12-06", CallToAction),
        GuildCard("f12-07", SilverTongues),
        VoxCard("f12-09", RumorsSpread),
        VoxCard("f12-10", RumorsSpread),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // PIRATE II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(GrowYourHoard)

            f.progress = game.factions.num @@ {
                case 2 => 24
                case 3 => 21
                case 4 => 18
            }

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate) --> PirateHoard --> f.lores

            f.log("got", f.lores.last)

            game.resources.register(PirateHoardSlots)

            f.recalculateSlots()

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PirateFleet)) --> f.loyal

            f.log("took", f.loyal.last)

            SetupFlagshipMainAction(f, $(None, |(Starport), None, |(Starport), None, None), PirateReplenishMainAction(f, then))

        case PirateReplenishMainAction(f, then) =>
            val ss = systems.%(s => s.gate && systems.exists(o => o.cluster == s.cluster && f.at(o).shiplikes.any).not)
            val n = ss.num.hi(f.pooled(Ship))

            Ask(f).group("Place", Ship.sof(f), "at gates")
                .each(ss.combinations(n).$)(l => ShipsInSystemsAction(f, l, then).as(l.comma))

        case FateFailAction(f, `fate`, 2, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PirateSmugglers)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(CallToAction)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(SilverTongues)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            $(PirateHoardSlots).resources.content.some./ { l =>
                l.foreach(r => r --> r.resource.supply)

                f.log("emptied", PirateHoard)
            }

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PirateSmugglers)) --> f.loyal

            f.log("took", f.loyal.last)

            PirateSpentHoardMainAction(f, then)

        case PirateSpentHoardMainAction(f, then) =>
            val next = PirateSpentHoardMainAction(f, then)

            implicit val ask = builder
            implicit val group = f.elem ~ " " ~ "spend" ~ PirateHoardSlots.elem

            val guilds = true

            $(PirateHoardSlots).resources./ { (r, k) =>
                val cost = PayResource(r, k)

                if (r.is(Material) || r.is(Fuel) || r.is(Relic)) {
                    // BUILD
                    if ((r.is(Material) && f.outraged.has(Material).not) || f.hasGuild(LoyalEngineers)) {
                        game.build(f, cost, next)
                        game.buildAlt(f, cost, guilds, next)
                    }

                    // REPAIR
                    if ((r.is(Material) && f.outraged.has(Material).not) || f.hasGuild(LoyalEngineers)) {
                        game.repair(f, cost, next)
                        game.repairAlt(f, cost, guilds, next)
                    }

                    // MOVE
                    if ((r.is(Fuel) && f.outraged.has(Fuel).not) || f.hasGuild(LoyalPilots)) {
                        game.move(f, cost, next)
                        game.moveAlt(f, cost, guilds, next)
                    }

                    // SECURE
                    if ((r.is(Relic) && f.outraged.has(Relic).not) || f.hasGuild(LoyalKeepers)) {
                        game.secure(f, cost, next)
                        game.secureAlt(f, cost, guilds, next)
                    }
                }
            }

            + PirateTakeHoardAction(f, then).as("Take remaining resources")

            ask(f)

        case PirateTakeHoardAction(f, then) =>
            val l = PirateHoardSlots.$

            if (l.any) {
                l.foreach { r =>
                    f.take(r)
                }

                f.log("took", l.intersperse(Comma))

                AdjustResourcesAction(then)
            }
            else
                then

        // PIRATE II LEGACY
        case GainCourtCardAction(f, v @ VoxCard(_, CallToAction), lane, main, then) =>
            game.deck.take(1) --> f.hand

            f.log("drew", 1.cards)

            DiscardVoxCardAction(f, v, then)

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, CallToAction), then) =>
            log("Crisis", v)

            val fr = factions./(f => f -> (f.countableResources(Material) + f.countableResources(Fuel) + f.countableResources(Weapon) + f.countableResources(Relic) + f.countableResources(Psionic)))
            val r = fr.rights.max
            val f = fr.%>(_ == r).lefts.single

            if (f.any) {
                f.foreach { f =>
                    game.deck.take(1) --> f.hand

                    f.log("drew", 1.cards)
                }

                DiscardCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))
            }
            else
                CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

        // PIRATE III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(LaughAtYourRivalsFolly)

            f.progress = 0

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(RumorsSpread)) --> game.court

            f.log("shuffled", game.court.last, "into the court deck")
            f.log("shuffled", game.court.last, "into the court deck")

            val units = game.figures.register(FatePieces(fate), content =
                1.to(1)./(Figure(Neutrals, ClusterCorrectRumor, _)) ++
                1.to(1)./(Figure(Neutrals, SymbolCorrectRumor, _)) ++
                1.to(2)./(Figure(Neutrals, FalseClueRumor, _))
            )

            game.laws :+= RumorsOfTheHaven

            f.log("set", game.laws.last)

            game.laws :+= SharingRumors

            f.log("set", game.laws.last)

            ShuffleCourtDeckAction(SetupRumorsMainAction(f, then))

        case SetupRumorsMainAction(f, then) =>
            Ask(f).group("Place Rumors")
                .each(FatePieces(fate).$.shuffle)(u => PlaceRumorMainAction(f, u.piece, then).as(u.piece.name.hl))
                .bail(then)

        case PlaceRumorMainAction(f, p, then) =>
            Ask(f).group("Place", p.name.hl, "in")
                .each(systems.%!(_.gate))(s => PlaceRumorAction(f, p, s, then).as(s))
                .cancel

        case PlaceRumorAction(f, p, s, then) =>
            FatePieces(fate) --> p --> s

            f.log("placed a", "Rumor".hh, "in", s)

            SetupRumorsMainAction(f, then)

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, RumorsSpread), then) =>
            log("Crisis", v)

            if (game.unrumored.has(cluster).not && systems.%(_.cluster == cluster).%(_.$.rumors.any).any) {
                val next : ForcedAction = DiscardCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

                game.unrumored :+= cluster

                systems.%(_.cluster == cluster).%(_.$.rumors.any).foreach { s =>
                    log(RumorsSpread, "revealed", "Rumor".hh, "in", s)

                    s.$.agents.foreach { u =>
                        u --> u.faction.reserve

                        u.faction.log("returned", u)
                    }
                }

                next
            }
            else
                CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

        case GainCourtCardAction(f, v @ VoxCard(_, RumorsSpread), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            val l = systems.%!(_.cluster.in(game.unrumored)).%(_.$.rumors.any)

            Ask(f).group("Investigate", "Rumor".hh, "in")
                .each(l)(s => SpreadRumorAction(f, s, next).as(s)
                    .!(f.at(s).agents.any, "present")
                    .!(f.at(s).none, "no presence")
                    .!(f.pool(Agent), "no agents")
                )
                .skip(next)
                .needOk

        case SpreadRumorAction(f, s, then) =>
            f.reserve --> Agent --> s

            f.log("investigated", "Rumor".hh, "in", s)

            then

        case RaidedAction(f, e, then) if game.act == 3 && e.as[Faction].?(_.fates.has(Pirate)) =>
            then @@ {
                case a : BattleRaidAction if a.used.has(RaidRumor) => then
                case a : BattleRaidAction =>
                    val next = a.copy(used = a.used :+ RaidRumor)

                    val l = systems.%!(_.cluster.in(game.unrumored)).%(_.$.rumors.any)

                    Ask(f).group("Interogate", "Rumor".hh, "in")
                        .each(l)(s => SpreadRumorAction(f, s, next).as(s)
                            .!(f.at(s).agents.any, "present")
                            .!(f.pool(Agent), "no agents")
                        )
                        .skip(next)
                        .needOk

                case _ => then
            }

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) && PirateHoardSlots.num < 2 =>
            f.log("had not enough resources in", PirateHoard, "to laugh")

            then

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            val cluster = systems.%(_.$.hasA(ClusterCorrectRumor)).only.cluster
            val symbol = systems.%(_.$.hasA(SymbolCorrectRumor)).only.symbol
            val haven = System(cluster, symbol)

            val noRival = f.rivals.forall(e => e.at(haven).none)
            val aLoyal = f.at(haven).any

            Ask(f).group(LaughAtYourRivalsFolly, HorizontalBreak, "Declare that at", "Haven".hh, "in", haven)
                .add(LaughAction(f, true, false, then).as("No rival pieces are present").!(noRival.not))
                .add(LaughAction(f, true, false, then).as("A loyal piece is present").!(aLoyal.not))
                .add(LaughAction(f, true, true, then).as("No rival pieces are present and a loyal piece is present").!(noRival.not || aLoyal.not))
                .add(LaughAction(f, false, false, then).as("Keep silence"))
                .needOk

        case LaughAction(f, noRival, aLoyal, then) =>
            f.grand = noRival.??(1) + aLoyal.??(1)

            if (noRival)
                f.log("declared that no rival pieces are at", "Pirate's Haven".hh)

            if (aLoyal)
                f.log("declared that a piece is at", "Pirate's Haven".hh)

            Then(then)

        case BlightCheckWinAction if game.chapter == 4 && factions.exists(f => f.fates.has(fate) && f.guess.none) =>
            val f = factions.%(_.fates.has(fate)).only

            val cluster = systems.%(_.$.hasA(ClusterCorrectRumor)).only.cluster
            val symbol = systems.%(_.$.hasA(SymbolCorrectRumor)).only.symbol
            f.guess = |(System(cluster, symbol))

            factions.but(f).reverse.foldLeft(CheckHavenAction(f) : ForcedAction)((q, e) => GuessHavenMainAction(e, q))

        case GuessHavenMainAction(f, then) =>
            Ask(f).group(LaughAtYourRivalsFolly, HorizontalBreak, "Guess", "Pirate's Haven".hh)
                .each(systems.%!(_.gate).%(f.rulesAOE))(s => GuessHavenAction(f, s, then).as(s))
                .skip(then)

        case GuessHavenAction(f, s, then) =>
            f.guess = |(s)

            f.log("searched", PirateHoard, "in", s)

            then

        case CheckHavenAction(f) =>
            val ee = f.rivals.%(_.guess == f.guess)

            if (ee.any)
                ee.foreach { e =>
                    e.power += 10
                    f.power -= 10

                    e.log("found", "Pirate's Haven".hh, "and gained", 10.power)
                    f.log("lost", 10.power)
                }
            else {
                f.power += 10

                f.log("Pirate's Haven".hh, "was save in", f.guess)
                f.log("got", 10.power)
            }

            BlightCheckWinAction


        // ...
        case _ => UnknownContinue
    }
}
