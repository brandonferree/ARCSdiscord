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


case object Warden extends Fate("Warden", "fate16", 2) {
    override val expansion = WardenExpansion
}


case object ControlTheFiefsSeats extends Objective("f16-01b", "Control the Fiefs' Seats")

case object ClaimsOfNobility extends Lore("f16-02", "Claims of Nobility") with FateCrisis
case object SeatsAndFiefs extends Law("f16-09", "Seats & Fiefs")
case object FeudalLaw extends Law("f16-10", "Feudal Law")
case object WardensLevy extends Lore("f16-12", "Warden's Levy") with SpecialRegion
case object FeastDay extends VoxEffect("Feast Day")


case object ReturnToTheOldWays extends Objective("f16-17b", "Return to the Old Ways")

case object FeudalCourts extends Law("f16-18", "Feudal Courts")
case object LordsGainPower extends Edict("f16-19", "Lords Gain Power", "80")


case class ClaimFiefMainAction(self : Faction, x : Cost, cancel : Boolean, then : ForcedAction) extends ForcedAction with Soft
case class ClaimFiefAttachAction(self : Faction, x : Cost, lane : Int, then : ForcedAction) extends ForcedAction
case class ClaimFiefAction(self : Faction, s : System, u : Figure, then : ForcedAction) extends ForcedAction

case class LevyMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class LevyAction(self : Faction, x : Cost, e : Faction, then : ForcedAction) extends ForcedAction

case class FeastDayAction(self : Faction, i : Int, s : System, u : Figure, then : ForcedAction) extends ForcedAction

case class FeudalCourtClaimMainAction(self : Faction, lane : Int, then : ForcedAction) extends ForcedAction
case class FeudalCourtClaimAction(self : Faction, lane : Int, cluster : Int, then : ForcedAction) extends ForcedAction

case class LordsGainPowerMainAction(cluster : Int, then : ForcedAction) extends ForcedAction


object WardenExpansion extends FateExpansion(Warden) {
    val deck = $(
        ClaimsOfNobility,
        WardensLevy,
        VoxCard("f16-13", FeastDay),
        VoxCard("f16-14", FeastDay),
        GuildCard("f16-15", Skirmishers),
        GuildCard("f16-16", SwornGuardians),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // WARDEN II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(ControlTheFiefsSeats)

            f.progress = 22

            f.log("objective was set to", f.progress.hlb)

            game.laws :+= SeatsAndFiefs

            f.log("set", game.laws.last)

            game.laws :+= FeudalLaw

            f.log("set", game.laws.last)

            FateDeck(fate) --> ClaimsOfNobility --> f.lores

            f.log("got", f.lores.last)

            ReplenishShipsMainAction(f, 8, then)

        case FateCrisisAction(f, ClaimsOfNobility, cluster, symbol, then) =>
            ClaimFiefMainAction(f, NoCost, false, then)

        case ClaimFiefMainAction(f, x, cancel, then) =>
            Ask(f).group(ClaimsOfNobility, x)
                .each(game.market) { m =>
                    ClaimFiefAttachAction(f, x, m.index, then).as(m.$)
                        .!(m.num > 1 && cancel, "already attached")
                        .!(m.num > game.market./(_.num).min)
                }
                .cancelIf(cancel)
                .needOk

        case ClaimFiefAttachAction(f, x, lane, then) =>
            f.pay(x)

            f.lores --> ClaimsOfNobility --> Market(lane)

            f.log("attached", ClaimsOfNobility, "to", Market(lane).first, x)

            Ask(f).group("Claim Fief".styled(f), x)
                .some(systems.%(s => game.seats.contains(s.cluster).not && f.at(s).cities.any))(s => f.at(s).cities./(u => ClaimFiefAction(f, s, u, then).as(u, "in", s).!(f.rules(s).not)))
                .skip(then)
                .needOk

        case ClaimFiefAction(f, s, u, then) =>
            f.log("claimed fief", u, "in", s)

            game.seats += s.cluster -> u

            then

        case FateFailAction(f, `fate`, 2, then) =>
            (ClaimsOfNobility : CourtCard) --> CourtScrap

            f.log("scrapped", ClaimsOfNobility)

            game.market.foreach { m =>
                if (m.none) {
                    val agents = Influence(m.index).$

                    if (agents.any) {
                        agents.foreach { u =>
                            u --> u.faction.reserve
                        }

                        log("Returned agents from", ClaimsOfNobility)
                    }
                }
            }

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(Skirmishers)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(SwornGuardians)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            FateDeck(fate) --> WardensLevy --> f.lores

            f.log("got", f.lores.last)

            game.figures.register(WardensLevy)

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(FeastDay)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        // WARDEN II LEGACY
        case LevyMainAction(f, x, then) =>
            val l = WardensLevy.$

            def limit(e : Faction) = $(1, 2, 3, 4, 5, 6).%(i => f.isLordIn(i) && e.isVassalIn(i)).num

            Ask(f).group(WardensLevy)
                .each(f.rivals.%(e => limit(e) > 0))(e => LevyAction(f, x, e, then).as(e).!(e.pool(Ship).not, "no ships").!(l.ofc(e).num >= limit(e), "limit"))
                .cancel

        case LevyAction(f, x, e, then) =>
            f.pay(x)

            e.reserve --> Ship --> WardensLevy

            f.log("levied", e)

            then

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, FeastDay), then) =>
            val next : ForcedAction = DiscardCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            if (main) {
                val l = Influence(lane).$

                if (l.any) {
                    l --> Scrap

                    log("scrapped", l.intersperse(Comma))
                }
            }

            next

        case GainCourtCardAction(f, v @ VoxCard(_, FeastDay), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, then)

            Ask(f).group(FeastDay)
                .each(game.seats.$.sortBy(_._1).%>(_.faction != f))((i, u) => FeastDayAction(f, i, u.system, u, next).as(u, "in", u.system).!(f.pool(City).not, "no cities"))
                .skip(next)
                .needOk

        case FeastDayAction(f, i, s, u, then) =>
            u --> u.faction.reserve

            val n = f.reserve.$.cities.first

            n --> s

            if (u.damaged) {
                u.faction.damaged :-= u
                f.damaged :+= n
            }

            if (game.unslotted.has(u)) {
                game.unslotted :-= u
                game.unslotted :+= n
            }

            game.seats += i -> n

            u.faction.as[Faction].foreach { e =>
                e.recalculateSlots()
            }

            f.recalculateSlots()

            AdjustResourcesAction(then)

        // WARDEN III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(ReturnToTheOldWays)

            f.progress = 0

            game.laws :+= FeudalCourts

            f.log("set", game.laws.last)

            game.edicts :+= LordsGainPower

            f.log("added", game.edicts.last, "edict")

            Then(then)

        case FeudalCourtClaimMainAction(f, lane, then) =>
            val l = $(1, 2, 3, 4, 5, 6).%!(game.feudal.values.$.contains).%(i => f.isLordIn(i) || f.isVassalIn(i))

            Ask(f).group(FeudalCourts, "claim", Market(lane).first)
                .each(l)(i => FeudalCourtClaimAction(f, lane, i, then).as("Cluster".hh, i.styled(styles.cluster).hlb))
                .skip(then)

        case FeudalCourtClaimAction(f, lane, cluster, then) =>
            game.feudal += lane -> cluster

            f.log("claimed", Market(lane).first, "for", "Cluster".hh, cluster.styled(styles.cluster).hlb)

            then

        case ResolveEdictAction(priority, then) if priority == LordsGainPower.priority =>
            val next = ResolveNextEdictAction(priority, then)

            $(1, 2, 3, 4, 5, 6).foldLeft(next : ForcedAction)((q, i) => LordsGainPowerMainAction(i, q))

        case LordsGainPowerMainAction(cluster, then) =>
            factions.%(_.isLordIn(cluster)).single./ { f =>
                f.power += 3

                f.log("gained", 3.power, "from", "Cluster".hh, cluster.styled(styles.cluster).hlb)

                game.feudal.keys.$.%(i => game.feudal(i) == cluster).single./ { n =>
                    Ask(f).group(LordsGainPower, "in", "Cluster".hh, cluster.styled(styles.cluster).hlb)
                        .add(SecureAction(f, NoCost, n, |(LordsGainPower), then).as("Secure", Market(n).$.intersperse("|")).!(Influence(n).$.use(l => l.%(_.faction == f).num <= f.rivals./(e => l.%(_.faction == e).num).max)))
                        .add(InfluenceAction(f, NoCost, n, |(LordsGainPower), then).as("Influence", Market(n).first).!(f.pool(Agent).not, "no agents"))
                        .skip(then)
                } | Then(then)
            } | Then(then)

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            if ($(1, 2, 3, 4, 5, 6).%(f.isLordIn).num > f.rivals./(e => $(1, 2, 3, 4, 5, 6).%(e.isLordIn).num).max) {
                f.grand += 1

                f.log("fulfilled a grand ambition as the biggest", "Lord".hh)
            }

            if (game.seats.values.$./(_.system).count(f.rulesAOE) > game.chapter) {
                f.grand += 1

                f.log("fulfilled a grand ambition controlling", "Seats".hh)
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
