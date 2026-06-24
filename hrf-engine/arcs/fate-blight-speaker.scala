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


case object BlightSpeaker extends Fate("Blight Speaker", "fate13", 2) {
    override val expansion = BlightSpeakerExpansion
}


case object ContactAnAncientIntelligence extends Objective("f13-01b", "Contact an Ancient Intelligence")

case object SporeShips extends GuildEffect("Spore Ships", Psionic, 999)
case object BlightFury extends Lore("f13-04", "Blight Fury")
case object BlightHunters extends GuildEffect("Blight Hunters", Weapon, 2)
case object BlightReapers extends GuildEffect("Blight Reapers", Material, 2)
case object BlightPurge extends VoxEffect("Blight Purge")
case object BlightPanic extends VoxEffect("Blight Panic")

case object GorgeTheBlight extends Objective("f13-12b", "Gorge the Blight with Bodies")

case object BlightHunger extends Lore("f13-13", "Blight Hunger") with SpecialRegion


case class SpawnMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class SpawnAction(self : Faction, x : Cost, s : System, then : ForcedAction) extends ForcedAction

case class BlightPanicMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction
case class BlightPanicAction(self : Faction, lane : Int, then : ForcedAction) extends ForcedAction

case class BlightPanicDestroyAction(self : Faction, cluster : Int, then : ForcedAction) extends ForcedAction

case class HarvestMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class HarvestAction(self : Faction, x : Cost, s : System, then : ForcedAction) extends ForcedAction

case class AngerInitAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction
case class AngerRolledAction(self : Faction, random : Int, then : ForcedAction) extends RandomAction[Int]
case class AngerMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction with Soft
case class AngerAdjustMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction with Soft
case class AngerAdjustAction(self : Faction, n : Int, x : Cost, then : ForcedAction) extends ForcedAction
case class AngerAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction


object BlightSpeakerExpansion extends FateExpansion(BlightSpeaker) {
    val deck = $(
        GuildCard("f13-02", SporeShips),
        BlightFury,
        GuildCard("f13-05", BlightHunters),
        GuildCard("f13-06", BlightReapers),
        VoxCard("f13-07", BlightPurge),
        VoxCard("f13-08", BlightPurge),
        VoxCard("f13-09", BlightPurge),
        VoxCard("f13-10", BlightPanic),
        VoxCard("f13-11", BlightLooms),
        BlightHunger,
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // BLIGHT SPEAKER II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(ContactAnAncientIntelligence)

            f.progress = game.factions.num @@ {
                case 2 => 18
                case 3 => 16
                case 4 => 14
            }

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(SporeShips)) --> f.loyal

            f.log("took", f.loyal.last)

            ScrapShipsMainAction(f, 2, ReplenishShipsMainAction(f, 8, then))

        case SpawnMainAction(f, x, then) =>
            Ask(f).group(SporeShips)
                .each(systems.%(s => Blights.at(s).fresh.any).%(f.rules))(s => SpawnAction(f, x, s, then).as(s).!(Blights.reserve.none))
                .cancel

        case SpawnAction(f, x, s, then) =>
            f.pay(x)

            val o = Blights.at(s).fresh.first
            val n = Blights.reserve.first

            n --> s

            Blights.damaged :+= o
            Blights.damaged :+= n

            f.log("spawned", n, "in", s, x)

            if (f.objective.has(ContactAnAncientIntelligence))
                f.advance(1, $("spawning"))

            then

        case FateFailAction(f, `fate`, 2, then) =>
            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(BlightPurge)).take(1) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(BlightPanic)).take(1) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(BlightLooms)).take(1) --> game.court

            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            FateDeck(fate) --> BlightFury --> f.lores

            f.log("got", f.lores.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(BlightHunters)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(BlightReapers)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(BlightPurge)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        // BLIGHT SPEAKER II LEGACY
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, BlightPanic), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            factions.foldLeft(next)((q, f) => BlightPanicMainAction(f, systems.%(f.at(_).use(l => l.buildings.any || l.flagships.any))./(Blights.at(_).num).sum, q))

        case BlightPanicMainAction(f, n, then) =>
            val l = game.market.%(m => Influence(m.index).$.ofc(f).any)

            if (l.any && n > 0)
                Ask(f).group("Remove", n.hlb, (n > 1).?(Agent.sof(f)).|(Agent.of(f)), "from court")
                    .each(game.market)(m => BlightPanicAction(f, m.index, BlightPanicMainAction(f, n - 1, then)).as(m.starting).!(l.has(m).not))
                    .needOk
            else
                Then(then)

        case BlightPanicAction(f, lane, then) =>
            Influence(lane).$.ofc(f).first --> f.reserve

            f.log("removed", Agent.of(f), "from", Market(lane).$.first)

            then

        case GainCourtCardAction(f, v @ VoxCard(_, BlightPanic), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, then)

            val l = 1.to(6).$./(i => i -> systems.%(_.cluster == i).%(Blights.at(_).damaged.any))

            Ask(f).group(v, "destroyed damaged", Blight, "in")
                .each(l.%>(_.any))((i, ss) => BlightPanicDestroyAction(f, i, next).as(ss.intersperse(Comma)).!(systems.%(_.cluster == i).exists(f.at(_).damaged.any).not))
                .bailout(then.as("No suitable cluster"))

        case BlightPanicDestroyAction(f, cluster, then) =>
            systems.%(_.cluster == cluster).foreach { s =>
                val l = Blights.at(s).damaged

                if (l.any) {
                    l --> f.trophies

                    f.log("destroyed", l.intersperse(Comma), "in", s)

                    Blights.damaged = Blights.damaged.diff(l)
                }
            }

            then

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, BlightPurge), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            factions.foreach { f =>
                val l = f.trophies.$.blights

                if (l.any) {
                    f.power += l.num * 2

                    f.log("gained", (l.num * 2).power, "for", Blights, "in trophies")
                }
            }

            next

        case GainCourtCardAction(f, v @ VoxCard(_, BlightPurge), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            val l = f.trophies.$.blights

            if (l.any) {
                f.power += l.num * 2

                f.log("gained", (l.num * 2).power, "for", Blights, "in trophies")
            }

            f.rivals.foreach { f =>
                val l = f.trophies.$.blights

                if (l.any) {
                    l --> Blights.reserve

                    f.log("lost", Blights, "trophies")
                }
            }

            next

        case HarvestMainAction(f, x, then) =>
            Ask(f).group("Harvest damaged", Blight)
                .each(systems.%(Blights.at(_).damaged.any).%(f.rules))(s => HarvestAction(f, x, s, then).as(s))
                .cancel

        case HarvestAction(f, x, s, then) =>
            f.pay(x)

            val u = Blights.at(s).damaged.first

            u --> Blights.reserve

            Blights.damaged :-= u

            f.log("harvested in", s, x)

            val l = s.gate.?($(Material, Fuel, Weapon, Relic, Psionic)).|(game.resources(s).distinct).%(game.available)

            if (l.any)
                Ask(f).group("Harvest in", s)
                    .each(l)(r => TaxGainAction(f, |(r), false, then).as("Gain", ResourceRef(r, None)).!(game.available(r).not))
            else {
                f.log("could not gain a resource")

                Then(then)
            }

        case AngerInitAction(f, x, then) =>
            f.pay(x)

            f.log("angered", x)

            Random[Int]($(1, 2, 3, 4, 5, 6), AngerRolledAction(f, _, then))

        case AngerRolledAction(f, n, then) =>
            f.log("rolled", n.hlb)

            AngerMainAction(f, n, then)

        case AngerMainAction(f, n, then) =>
            Ask(f).group("Anger in")
                .add(AngerAction(f, n, then).as(systems.%(_.cluster == n).%(Blights.at(_).any).some.|("Cluster" -> n.hl.styled(styles.cluster))))
                .add(AngerAdjustMainAction(f, (n - 1 + 1) % 6 + 1, then).as("Adjust to cluster", ((n - 1 + 1) % 6 + 1).hlb).!!!)
                .add(AngerAdjustMainAction(f, (n - 1 + 5) % 6 + 1, then).as("Adjust to cluster", ((n - 1 + 5) % 6 + 1).hlb).!!!)

        case AngerAdjustMainAction(f, n, then) =>
            Ask(f).group("Adjust anger cluster to", n.hl.styled(styles.cluster), "spending")
                .each(f.spendable.resources.%<(_.is(Psionic) || f.hasGuild(LoyalEmpaths)))((r, k) => AngerAdjustAction(f, n, PayResource(r, k), then).as(r -> k))
                .cancel

        case AngerAdjustAction(f, n, x, then) =>
            f.pay(x)

            f.log("adjusted cluster to", n.hl.styled(styles.cluster), x)

            AngerMainAction(f, n, then)

        case AngerAction(f, n, then) =>
            BlightCrisesMainAction(systems.%(_.cluster == n), $, $, then)

        // BLIGHT SPEAKER III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(GorgeTheBlight)

            f.progress = 0

            FateDeck(fate) --> BlightHunger --> f.lores

            f.log("got", f.lores.last)

            game.figures.register(BlightHunger)

            Then(then)

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            if (game.winners.get(Warlord).?(_ != f).not) {
                f.grand += 1

                f.log("fulfilled a grand ambition denying", Warlord)
            }

            if (BlightHunger.$.num > game.chapter || systems./(_.$.buildings.num).sum == 0) {
                f.grand += 1

                f.log("fulfilled a grand ambition with", BlightHunger)
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
