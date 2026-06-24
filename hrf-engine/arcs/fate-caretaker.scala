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


case object Caretaker extends Fate("Caretaker", "fate05", 1) {
    override val expansion = CaretakerExpansion
}


abstract class GolemType(val id : String, override val name : String, val order : Int) extends Resource
case object WarriorGolem extends GolemType("warrior", "Warrior", 600)
case object ProtectorGolem extends GolemType("protector", "Protector", 700)
case object SeekerGolem extends GolemType("seeker", "Seeker", 800)
case object HarvesterGolem extends GolemType("harvester", "Harvester", 900)

case class ProtoGolem(golem : GolemType) extends Piece


case object GolemHearthSlots extends ResourceSlot with Elementary {
    override def capacity(f : Faction)(implicit game : Game) = f.adjustable.content.count(_.isGolem)
    override def canHold(t : ResourceLike)(implicit game : Game) = t.isGolem
    override val raidable = |(2)
    def elem = "Golem Hearth".styled(styles.titleW)
}

case object GolemSupply extends ResourceSlot

case class GolemToken(golem : GolemType, awake : Boolean) extends Cost with ResourceLike {
    def id = "golem-" + awake.?(golem.id).|("sleep")
    // def elem = awake.?(golem.name).|("Golem").hh
    def elem = (awake.not.??("Sleeping ") + golem.name).hh
    def is(r : Resource) = false
    def isResource = false
    def isGolem = true
    def elem(implicit game : Game) = token
    def order : Int = golem.order
    def token = elem ~ Image(id, styles.token)
}


case object FindGolems extends Objective("f05-01b", "Find Golems")

case object Golems extends Law("f05-05", "Golems")
case object GolemActions extends Law("f05-06", "Golem Actions")

case object GolemBeacon extends Lore("f05-02", "Golem Beacons")
case object GolemHearth extends Lore("f05-03", "Golem Hearth") with FateCrisis

trait UnstableGolemEffect {
    val golem : GolemType
}

case object UnstableWarrior extends VoxEffect("Unstable Warrior") with UnstableGolemEffect { val golem = WarriorGolem }
case object UnstableProtector extends VoxEffect("Unstable Protector") with UnstableGolemEffect { val golem = ProtectorGolem }
case object UnstableSeeker extends VoxEffect("Unstable Seeker") with UnstableGolemEffect { val golem = SeekerGolem }
case object UnstableHarvester extends VoxEffect("Unstable Harvester") with UnstableGolemEffect { val golem = HarvesterGolem }

case object StoneSpeakers extends GuildEffect("Stone-Speakers", Material, 999)


case class ProtoGolemsShuffledAction(shuffled : $[GolemType], then : ForcedAction) extends ShuffledAction[GolemType]

case class AwakenMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class AwakenAction(self : Faction, cost : Cost, l : $[System], then : ForcedAction) extends ForcedAction

case class SpendGolemMainAction(self : Faction, golem : GolemToken, slot : ResourceSlot, then : ForcedAction) extends ForcedAction with Soft

case class GolemGainResourcesAction(self : Faction, r : Resource, then : ForcedAction) extends ForcedAction
case class GiveGolemAwayPreludeAction(self : Faction, golem : GolemToken, slot : ResourceSlot, then : ForcedAction) extends ForcedAction
case class GiveGolemAwayMainAction(self : Faction, golem : GolemToken, slot : ResourceSlot, then : ForcedAction) extends ForcedAction
case class GiveGolemAwayAction(self : Faction, golem : GolemToken, e : Faction, slot : ResourceSlot, then : ForcedAction) extends ForcedAction

case class DestroyAllDamagedShipsAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction
case class RepairAnyDamagedShipsAction(self : Faction, cancel : Boolean, flagships : $[Faction], then : ForcedAction) extends ForcedAction with Soft
case class RepairDamagedShipAction(self : Faction, s : System, u : Figure, then : ForcedAction) extends ForcedAction
case class ProtectorFlagshipRepairAction(f : Faction, then : ForcedAction) extends ForcedAction

case class StoneSpeakersMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class StoneSpeakersAction(self : Faction, l : $[GolemToken], then : ForcedAction) extends ForcedAction
case class StoneSpeakersCrisesAction(then : ForcedAction) extends ForcedAction
case class StoneSpeakersRolledAction(random1 : Boolean, random2 : Int, random3 : Symbol, then : ForcedAction) extends Random3Action[Boolean, Int, Symbol]

case class AddLoreToCourtShuffledAction(self : Faction, shuffled : $[Lore], then : ForcedAction) extends ShuffledAction[Lore]

case class FateSetupAssignGolemsMainAction(caretaker : Faction, then : ForcedAction) extends ForcedAction
case class FateSetupAssignGolemsAction(f : Faction, t : GolemToken, then : ForcedAction) extends ForcedAction


object CaretakerExpansion extends FateExpansion(Caretaker) {
    val deck = $(
        GolemBeacon,
        GolemHearth,
        GuildCard("f05-04", StoneSpeakers),
        VoxCard("f05-08", UnstableHarvester),
        VoxCard("f05-09", UnstableSeeker),
        VoxCard("f05-10", UnstableProtector),
        VoxCard("f05-11", UnstableWarrior),
    )

    val golems = $(WarriorGolem, ProtectorGolem, SeekerGolem, HarvesterGolem)

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // CARETAKER I
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(FindGolems)

            f.progress = 18

            f.log("objective was set to", f.progress.hlb)

            game.laws :+= Golems

            f.log("set", game.laws.last)

            game.laws :+= GolemActions

            f.log("set", game.laws.last)

            FateDeck(fate) --> GolemBeacon --> f.lores

            f.log("got", f.lores.last)

            FateDeck(fate) --> GolemHearth --> f.lores

            f.log("got", f.lores.last)

            f.recalculateSlots()

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(StoneSpeakers)) --> f.loyal

            f.log("took", f.loyal.last)

            game.resources.register(GolemSupply, content = golems./~(t => GolemToken(t, false) :: GolemToken(t, true)))

            game.resources.register(GolemHearthSlots)

            game.figures.register(FatePieces(fate), content = golems./(t => Figure(Neutrals, ProtoGolem(t), 1)))

            Shuffle[GolemType](golems, ProtoGolemsShuffledAction(_, then))

        case ProtoGolemsShuffledAction(l, then) =>
            systems.%(_.gate).%(_.$.ofc(Empire).none).zp(l).foreach { (s, t) =>
                FatePieces(fate) --> ProtoGolem(t) --> s

                log("Golem".hh, "was placed in", s)
            }

            then

        case FateSetupInitAction(f, `fate`, 1, then) =>
            game.internalPerform(FateInitAction(f, `fate`, 1, then), NoVoid, 0) match {
                case Shuffle(l, shuffled, _) => log("Skipped placing golem tokens on the map")
            }

            then

        case AwakenMainAction(f, x, then) =>
            val l = systems.%(_.gate).%(f.rules).%(_.$.%(_.piece.is[ProtoGolem]).any)

            Ask(f).group("Awaken", "Golems".hl, "in")
                .each(1.to(l.num).reverse./~(n => l.combinations(n))) { a =>
                    AwakenAction(f, x, a, then).as(a.intersperse(Comma))
                }
                .cancel

        case AwakenAction(f, x, l, then) =>
            f.pay(x)

            val awake = game.declared.keys.exists(a => game.declared(a).any && f.ambitionValue(a) > f.rivals./(_.ambitionValue(a)).max)

            l.foreach { s =>
                s.$.%(_.piece.is[ProtoGolem]).foreach { u =>
                    u --> FatePieces(fate)

                    val golem = u.piece.as[ProtoGolem].get.golem

                    val t = GolemToken(golem, awake)

                    (t : ResourceLike) --> GolemHearthSlots

                    f.log(awake.not.??("half-") + "awakened", t)
                }
            }

            then

        case FateCrisisAction(f, GolemHearth, cluster, symbol, then) =>
            factions.foreach { f =>
                f.spendable.foreach { s =>
                    s.$.of[GolemToken].%!(_.awake).foreach { t =>
                        val a = t.copy(awake = true)

                        (t : ResourceLike) --> GolemSupply
                        (a : ResourceLike) --> s

                        log(a -> s, "awoke")
                    }
                }
            }

            then

        case SpendGolemMainAction(f, x @ GolemToken(WarriorGolem, _), k, then) =>
            val next = GiveGolemAwayPreludeAction(f, x, k, then)

            val l = systems.%(s => s.$.ships.damaged./(_.faction).distinct.forall(e => f.canHarm(e, s)))

            Ask(f).group(x, "destroys", "all", "damaged ships", "in")
                .each(systems.%(s => s.$.ships.damaged.any)) { s =>
                    if (l.has(s)) {
                        val ll = s.$.ships.damaged./(_.faction).distinct
                        val nn = ll.foldLeft(next : ForcedAction)((q, e) => HarmAction(f, e, s, q))
                        ll.foldLeft(DestroyAllDamagedShipsAction(f, s, nn) : ForcedAction)((q, e) => TryHarmAction(f, e, s, x, q)).as(s)
                    }
                    else
                        DestroyAllDamagedShipsAction(f, s, next).as(s).!(true)
                }
                .group(" ")
                .add(next.as("Discard", x, "with no effect".styled(xstyles.error)))
                .group(" ")
                .cancel

        case DestroyAllDamagedShipsAction(f, s, then) =>
            val l = s.$.ships.damaged

            f.log("destroyed", l.intersperse(Comma), "in", s, "with", GolemToken(WarriorGolem, true))

            l.foreach { u =>
                if (u.faction == f)
                    u --> f.reserve
                else
                    u --> f.trophies

                u.faction.damaged :-= u
            }

            var next = then

            l./(_.faction).distinct.of[Faction].foreach { e =>
                if ((f.isLordIn(s.cluster) && e.isVassalIn(s.cluster)) || (e.isLordIn(s.cluster) && f.isVassalIn(s.cluster)))
                    systems.%(_.cluster == s.cluster).%(_.$.cities.any)./~(game.resources).distinct.foreach { r =>
                        next = OutrageAction(f, r, |(FeudalLaw), next)
                    }
            }

            Then(next)

        case SpendGolemMainAction(f, x @ GolemToken(ProtectorGolem, _), k, then) =>
            val next = GiveGolemAwayPreludeAction(f, x, k, then)

            RepairAnyDamagedShipsAction(f, true, factions.%(_.flagship.any).%(e => Flagship.scheme(e).exists(_.$.damaged.any)), next)

        case RepairAnyDamagedShipsAction(f, cancel, l, then) =>
            Ask(f).group(f, "repairs any ships with", GolemToken(ProtectorGolem, true))
                .each(l) { e => ProtectorFlagshipRepairAction(e, RepairAnyDamagedShipsAction(f, false, l :- e, then)).as(e.flagship, "in", e.flagship.get.system)("Flagships") }
                .some(systems) { s =>
                    s.$.ships.damaged./(u => RepairDamagedShipAction(f, s, u, RepairAnyDamagedShipsAction(f, false, l, then)).as(u)(s))
                }
                .group(" ")
                .when(cancel)(then.as("Discard", GolemToken(ProtectorGolem, true), "with no effect".styled(xstyles.error)))
                .group(" ")
                .cancelIf(cancel)
                .doneIf(cancel.not)(then)

        case RepairDamagedShipAction(f, s, u, then) =>
            u.faction.damaged :-= u

            f.log("repaired", u, "in", s, "with", GolemToken(ProtectorGolem, true))

            then

        case SpendGolemMainAction(f, x @ GolemToken(SeekerGolem, _), k, then) =>
            val next = GiveGolemAwayPreludeAction(f, x, k, then)

            val regent = campaign && f.regent
            val officers = regent && f.officers

            var ss = systems./(s => s -> f.at(s).shiplikes.use(l => l ++ regent.??((l.any || officers).??(Empire.at(s).ships)))).%>(_.any).toMap

            Ask(f).group("Move".hh, "with", x, "from")
                .each(ss.keys.$)(s => MoveFromAction(f, s, ss(s), true, x, None, CancelAction, next).as(s))
                .add(next.as("Discard", x, "with no effect".styled(xstyles.error)))
                .cancel

        case SpendGolemMainAction(f, x @ GolemToken(HarvesterGolem, _), k, then) =>
            val next = GiveGolemAwayPreludeAction(f, x, k, then)

            Ask(f).group(x, "procures any resource")
                .each(Resources.all) { r =>
                    GolemGainResourcesAction(f, r, next.copy(then = AdjustResourcesAction(next.then))).as("Gain".hh, r.token).!(game.available(r).not, "not in supply")
                }
                .add(next.as("Discard", x, "with no effect".styled(xstyles.error)))
                .cancel

        case GolemGainResourcesAction(f, r, then) =>
            f.gain(r, $("with", GolemToken(HarvesterGolem, true)))

            then

        case GiveGolemAwayPreludeAction(f, x, k, then) =>
            factions.%(_.objective.has(FindGolems)).foreach { f =>
                f.advance(2, $("for golem use"))
            }

            GiveGolemAwayMainAction(f, x, k, then)

        case GiveGolemAwayMainAction(f, x, k, then) =>
            (x : ResourceLike) --> GolemSupply

            val z = x.copy(awake = false)

            (z : ResourceLike) --> k

            Ask(f).group("Give away", x)
                .some(f.rivals) { e =>
                    e.spendable.%(s => s.canHold(x) && s.canHoldMore)./(s => GiveGolemAwayAction(f, z, e, s, then).as(ResourceLikeInSlot(Nothingness, s))(e))
                }
                .bailout(then.as("No free slots"))
                .needOk

        case GiveGolemAwayAction(f, x, e, s, then) =>
            (x : ResourceLike) --> s

            f.log("gave", x.copy(awake = true), "away to", e)

            then

        case StoneSpeakersMainAction(f, then) =>
            val l = f.rivals./~ { e =>
                e.spendable./~ { s =>
                    s.$.of[GolemToken]./ { t =>
                        t -> e
                    }
                }
            }

            Ask(f).group(StoneSpeakers, "take golems")
                .each(0.to(l.num).reverse./~(l.combinations)) { m =>
                    StoneSpeakersAction(f, m.lefts, then).as(m./((t, e) => (t, "from", e)).intersperse(Comma), m.none.?("None"))
                }
                .needOk
                .cancel

        case StoneSpeakersAction(f, l, then) =>
            l.foreach { t =>
                f.take(t)

                f.log("took", t, "with", StoneSpeakers)
            }

            f.log("ended turn")

            val next = CheckNoFleetAction(f, EndTurnAction(f))

            AdjustResourcesAction(StoneSpeakersCrisesAction(then match {
                // normal declare
                case then @ AmbitionDeclaredAction(_, _, _, out : PrePreludeActionAction) =>
                    next
                // ???
//                case then @ AmbitionDeclaredAction(_, _, _, DiscardVoxCardAction(p1, p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(out : PreludeActionAction)))) =>
//                    DiscardVoxCardAction(p1, p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(next)))
                // ???
//                case then @ AmbitionDeclaredAction(_, _, _, DiscardVoxCardAction(p1, p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(out : MainTurnAction)))) =>
//                    DiscardVoxCardAction(p1, p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(next)))
                // populist demands
                case then @ AmbitionDeclaredAction(_, _, _, BuryVoxCardAction(p1, p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(out : PreludeActionAction)))) =>
                    BuryVoxCardAction(p1, p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(next)))
                // populist demands
                case then @ AmbitionDeclaredAction(_, _, _, BuryVoxCardAction(p1 ,p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(out : MainTurnAction)))) =>
                    BuryVoxCardAction(p1, p2, CaptureAgentsCourtCardAction(p3, p4, ReplenishMarketAction(next)))
                // populist demands
                case then @ AmbitionDeclaredAction(_, _, _, BuryVoxCardAction(p1, p2, ExecuteAgentsCourtCardAction(p3, p4, ReplenishMarketAction(out : BattleRaidAction)))) =>
                    BuryVoxCardAction(p1, p2, ExecuteAgentsCourtCardAction(p3, p4, ReplenishMarketAction(next)))
                // lesser regents
                case then @ AmbitionDeclaredAction(_, _, _, DiscardPreludeGuildCardAction(p1, p2, out : PreludeActionAction)) =>
                    DiscardPreludeGuildCardAction(p1, p2, next)
                // liaisons
                case then @ AmbitionDeclaredAction(_, _, _, out : PreludeActionAction) =>
                    next
                // tycoon's ambition
                case then @ AmbitionDeclaredAction(_, _, _, PayCostAction(p4, p5, out : PreludeActionAction)) =>
                    PayCostAction(p4, p5, next)
                // galactic bards
                case then @ AmbitionDeclaredAction(_, _, _, UseEffectAction(_, _, CheckSeizeAction(_, out : PrePreludeActionAction))) =>
                    next
                // rebuke of the keepers
                case then @ AmbitionDeclaredAction(_, _, _, ResolveNextEdictAction(_, _)) =>
                    then
                // judge sets agenda
                case then @ AmbitionDeclaredAction(_, _, _, AssignChosenMainAction(_, _)) =>
                    then
                // refill to conspire
                case then @ AmbitionDeclaredAction(_, _, _, RefillToConspireAction(p1, p2, p3, out : MainTurnAction)) =>
                    RefillToConspireAction(p1, p2, p3, then)

                case _ => throw new Error("unhandled case then @ " + then + " =>")
            }))

        case StoneSpeakersCrisesAction(then) =>
            log("Rolled", "Event Dice".styled(styles.titleW))

            Random3[Boolean, Int, Symbol]($(false, true), $(1, 2, 3, 4, 5, 6), $(Arrow, Crescent, Hex), (e, i, s) => StoneSpeakersRolledAction(e, i, s, then))

        case StoneSpeakersRolledAction(edicts, cluster, symbol, then) =>
            if (edicts) {
                log("Outcome was no", "Crises".styled(Blights))

                then
            }
            else
                ResolveCrisesAction(cluster, symbol, then)

        case FateFailAction(f, `fate`, 1, then) =>
            f.lores --> GolemBeacon --> FateDeck(fate)

            f.log("lost", GolemBeacon)

            systems.%(_.gate)./~(_.$.ofc(Neutrals).%(_.piece.is[ProtoGolem])).some.foreach { l =>
                l --> FatePieces(fate)

                f.log("lost unrecovered", "Golems".hh)
            }

            f.loyal.%(_.as[GuildCard]./(_.effect).has(StoneSpeakers)).foreach { c =>
                c --> game.court

                f.log("added", c, "to the court deck")
            }

            if (GolemSupply.has(GolemToken(WarriorGolem, false)).not || GolemSupply.has(GolemToken(WarriorGolem, true)).not) {
                FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableWarrior)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            if (GolemSupply.has(GolemToken(ProtectorGolem, false)).not || GolemSupply.has(GolemToken(ProtectorGolem, true)).not) {
                FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableProtector)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            if (GolemSupply.has(GolemToken(SeekerGolem, false)).not || GolemSupply.has(GolemToken(SeekerGolem, true)).not) {
                FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableSeeker)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            if (GolemSupply.has(GolemToken(HarvesterGolem, false)).not || GolemSupply.has(GolemToken(HarvesterGolem, true)).not) {
                FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableHarvester)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            Then(then)

        case FateDoneAction(f, `fate`, 1, then) =>
            f.lores --> GolemBeacon --> FateDeck(fate)

            f.log("lost", GolemBeacon)

            systems.%(_.gate)./~(_.$.ofc(Neutrals).%(_.piece.is[ProtoGolem])).foreach { u =>
                val t = u.piece.as[ProtoGolem].get.golem

                u --> FatePieces(fate)

                (GolemSupply : ResourceSlot) --> GolemToken(t, false) --> GolemHearthSlots

                f.log("took sleeping", GolemToken(t, true))
            }

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableWarrior)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableProtector)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableSeeker)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(UnstableHarvester)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            ShuffleTake[Lore](game.allLores.$.of[Lore].notOf[UnofficialLore], factions.num, AddLoreToCourtShuffledAction(f, _, then))

        case action @ FateSetupFailAction(f, `fate`, 1, then) if f.used.has(GolemBeacon).not =>
            FateSetupAssignGolemsMainAction(f, action)

        case action @ FateSetupDoneAction(f, `fate`, 1, then) if f.used.has(GolemBeacon).not =>
            FateSetupAssignGolemsMainAction(f, action)

        case FateSetupDoneAction(f, `fate`, 1, then) =>
            game.internalPerform(FateDoneAction(f, `fate`, 1, then), NoVoid, 0) match {
                case ShuffleTake(l, n, shuffled, _) => log("Skipped adding", n.hh, "random Lore cards to the deck, add manually")
            }

            then

        case action @ FateSetupAssignGolemsMainAction(caretaker, then) =>
            MultiAsk(factions./ { h =>
                Ask(h).group("Assign Golems")
                    .some(GolemSupply.$.of[GolemToken].%(_.awake)) { t =>
                        factions./(f => FateSetupAssignGolemsAction(f, t, action).as(t, "to", f))
                    }
                    .done(UseEffectAction(caretaker, GolemBeacon, then))
            })

        case FateSetupAssignGolemsAction(f, t, then) =>
            f.take(t)

            f.log("took", t)

            then

        case AddLoreToCourtShuffledAction(f, l, then) =>
            game.allLores --> l --> game.court

            log("Added more lores", l.intersperse(Comma), "to court")

            Then(then)

        // CARETAKER II
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, UnstableWarrior), then) =>
            val next : ForcedAction = CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then)

            log("Crisis", v)

            val f = factions.%(_.spendable.exists(s => s.has(GolemToken(WarriorGolem, false)) || s.has(GolemToken(WarriorGolem, true)))).only

            systems.foreach { s =>
                f.at(s).ships.damaged.some./ { l =>
                    log(v, "destroyed", l.intersperse(Comma), "in", s)

                    l --> game.laws.has(TheDeadLive).?(TheDeadLive).|(f.reserve)

                    f.damaged = f.damaged.diff(l)
                }
            }

            next

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, UnstableProtector), then) =>
            val next : ForcedAction = CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then)

            log("Crisis", v)

            val f = factions.%(_.spendable.exists(s => s.has(GolemToken(ProtectorGolem, false)) || s.has(GolemToken(ProtectorGolem, true)))).only

            systems.foreach { s =>
                val l = s.$.ships.damaged.%!(_.faction == f)

                if (l.any) {
                    log(v, "repaired", l.intersperse(Comma), "in", s)

                    l.foreach { u =>
                        u.faction.damaged :-= u
                    }
                }
            }

            val l = factions.but(f).%(_.flagship.any).%(e => Flagship.scheme(e).exists(_.$.damaged.any))

            l.foldLeft(next : ForcedAction)((q, e) => ProtectorFlagshipRepairAction(e, q))

        case ProtectorFlagshipRepairAction(f, then) =>
            val upgrades = Flagship.scheme(f).%(_.$.damaged.any)

            Ask(f)
                .group(UnstableProtector, "repairs")
                .some(upgrades)(q => q.$.damaged./(u => RepairFlagshipAction(f, NoCost, q, u, then).as(u, game.showFigure(u), "as", q)))
                .needOk

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, UnstableSeeker), then) =>
            val next : ForcedAction = CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then)

            log("Crisis", v)

            val dest = System(cluster, Gate)

            val f = factions.%(_.spendable.exists(s => s.has(GolemToken(SeekerGolem, false)) || s.has(GolemToken(SeekerGolem, true)))).only

            systems.%(_.symbol == symbol).foreach { s =>
                val l = f.at(s).ships

                if (l.any) {
                    log(v, "moved", l.intersperse(Comma), "from", s, "to", dest)

                    l --> dest
                }
            }

            next

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, UnstableHarvester), then) =>
            val next : ForcedAction = CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then)

            log("Crisis", v)

            val f = factions.%(_.spendable.exists(s => s.has(GolemToken(HarvesterGolem, false)) || s.has(GolemToken(HarvesterGolem, true)))).only

            val l = f.spendable.resources.content

            l.foreach { r => r --> r.supply }

            f.log("discarded", l)

            next

        case GainCourtCardAction(f, v @ VoxCard(_, u : UnstableGolemEffect), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            val aw = factions.%(_.spendable.exists(s => s.has(GolemToken(u.golem, true)))).single
            val sl = factions.%(_.spendable.exists(s => s.has(GolemToken(u.golem, false)))).single

            val sleeping = sl.any
            val enemy = aw.||(sl).but(f)

            if (enemy.any) {
                if (sleeping)
                    GolemToken(u.golem, false) --> GolemSupply

                f.take(GolemToken(u.golem, true))
            }
            else {
                if (sleeping)
                    f.spendable.foreach { s =>
                        if (s.has(GolemToken(u.golem, false))) {
                            GolemToken(u.golem, true) --> s
                            GolemToken(u.golem, false) --> GolemSupply
                        }
                    }
            }

            if (enemy.any)
                f.log("took", GolemToken(u.golem, false), "from", enemy, sleeping.?("and awakened"))
            else
            if (sleeping)
                f.log("awakened", GolemToken(u.golem, false))

            AdjustResourcesAction(next)


        // ...
        case _ => UnknownContinue
    }
}
