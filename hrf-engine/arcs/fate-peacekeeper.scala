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


case object Peacekeeper extends Fate("Peacekeeper", "fate15", 2) {
    override val expansion = PeacekeeperExpansion
}


case object ArsenalKeepersSlots extends ResourceSlot with Elementary {
    override def canHold(t : ResourceLike)(implicit game : Game) = t.is(Weapon)
    override val raidable = |(2)
    def elem = ArsenalKeepers.elem
}


case object RestrictTheWeaponSupply extends Objective("f15-01b", "Restrict the Weapon Supply")

case object OathOfPeace extends Lore("f15-02", "Oath of Peace")
case object ArsenalKeepers extends GuildEffect("Arsenal Keepers", Weapon, 999)
case object ArmsInterest extends GuildEffect("Arms Interest", Weapon, 2)

case object APeacefulReach extends Objective("f15-07b", "A Peaceful Reach")

case object Ceasefires extends Law("f15-08", "Ceasefires")
case object EmpireBalksAtPeace extends Edict("f15-09", "Empire Balks at Peace", "34")
case object PeaceDividends extends Edict("f15-10", "Peace Dividends", "35")
case object PeaceAccords extends Law("f15-11", "Peace Accords")


case class PeacekeeperSetupAction(self : Faction, then : ForcedAction) extends ForcedAction

case class ArsenalKeepersMainAction(self : Faction, then : ForcedAction) extends ForcedAction
case class ArsenalKeepersAction(self : Faction, l : $[ResourceLike], then : ForcedAction) extends ForcedAction

case class BalkAtPeaceAction(self : Faction, cluster : Int, then : ForcedAction) extends ForcedAction

case class EndCeasefireAction(self : Faction, n : Int, x : Cost, then : ForcedAction) extends ForcedAction


object PeacekeeperExpansion extends FateExpansion(Peacekeeper) {
    val deck = $(
        OathOfPeace,
        GuildCard("f15-03", ArsenalKeepers),
        GuildCard("f15-05", ArmsInterest),
        GuildCard("f15-06", ArmsInterest),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // PEACEKEEPER II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(RestrictTheWeaponSupply)

            f.progress = game.factions.num @@ {
                case 2 => 18
                case 3 => 16
                case 4 => 14
            }

            f.log("objective was set to", f.progress.hlb)

            ClearOutrageAction(f, f.outraged, OutrageAction(f, Weapon, |(OathOfPeace), PeacekeeperSetupAction(f, then)))

        case PeacekeeperSetupAction(f, then) =>
            FateDeck(fate) --> OathOfPeace --> f.lores

            f.log("got", f.lores.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(ArsenalKeepers)) --> f.loyal

            f.log("took", f.loyal.last)

            game.resources.register(ArsenalKeepersSlots)

            f.recalculateSlots()

            ReplenishShipsMainAction(f, 8, then)

        case AdjustResourcesAction(then) if factions.%(_.taken.exists(_.is(Weapon))).%(_.hasGuild(ArsenalKeepers)).any =>
            ArsenalKeepersMainAction(factions.%(_.taken.exists(_.is(Weapon))).%(_.hasGuild(ArsenalKeepers)).first, then)

        case ArsenalKeepersMainAction(f, then) =>
            Ask(f).group(ArsenalKeepers, "take")
                .each(1.to(f.taken.count(_.is(Weapon))).reverse./(f.taken.%(_.is(Weapon)).take))(l => ArsenalKeepersAction(f, l, then).as(l.intersperse(Comma)))
                .skip(ArsenalKeepersAction(f, $, then))

        case ArsenalKeepersAction(f, l, then) =>
            if (l.any) {
                l --> ArsenalKeepersSlots

                f.log("gave", l.intersperse(Comma), "to", ArsenalKeepers)
            }

            f.taken = f.taken.%!(_.is(Weapon))

            AdjustResourcesAction(then)

        case FateFailAction(f, `fate`, 2, then) =>
            if (f.hasLore(OathOfPeace)) {
                f.lores --> OathOfPeace --> FateDeck(fate)

                f.log("lost", OathOfPeace)
            }

            f.loyal.%(_.as[GuildCard]./(_.effect).has(ArsenalKeepers)).foreach { c =>
                c --> game.court

                f.log("added", c, "to the court deck")
            }

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(ArmsInterest)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            if (f.hasLore(OathOfPeace)) {
                f.lores --> OathOfPeace --> FateDeck(fate)

                // f.log("lost", OathOfPeace)
            }

            Then(then)

        // PEACEKEEPER III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(APeacefulReach)

            f.progress = 0

            FateDeck(fate) --> OathOfPeace --> f.lores

            f.log("renewed", f.lores.last)

            game.laws :+= Ceasefires

            f.log("set", game.laws.last)

            game.edicts :+= EmpireBalksAtPeace

            f.log("added", game.edicts.last, "edict")

            game.edicts :+= PeaceDividends

            f.log("added", game.edicts.last, "edict")

            game.laws :+= PeaceAccords

            f.log("set", game.laws.last)

            $(1, 2, 3, 4, 5, 6).foreach { c =>
                if (systems.%(_.cluster == c).exists(f.rulesAOE)) {
                    game.ceasefire :+= c

                    f.log("forced peace in", "Cluster".hh, c.styled(styles.cluster).hlb)
                }
            }

            Then(then)

        case ResolveEdictAction(priority, then) if priority == EmpireBalksAtPeace.priority =>
            val next = ResolveNextEdictAction(priority, then)

            val peacekeeper = factions.%(_.fates.has(Peacekeeper)).only
            val primus = factions.%(_.primus).single

            if (peacekeeper.regent.not && game.edicts.has(PolicyOfWar) && primus.any) {
                Ask(primus.get).group(EmpireBalksAtPeace)
                    .each($(1, 2, 3, 4, 5, 6).%(game.ceasefire.has))(i =>
                        BalkAtPeaceAction(primus.get, i, next).as("End", "Peace".hl, "in cluster", i.styled(styles.cluster).hl)
                            .!(systems.%(_.cluster == i).%(Empire.rules).num < 2)
                    )
                    .skip(next)
            }
            else
                next

        case BalkAtPeaceAction(f, cluster, then) =>
            game.ceasefire :-= cluster

            f.log("ended", "Peace".hl, "in cluster", cluster.styled(styles.cluster).hl)

            then

        case ResolveEdictAction(priority, then) if priority == PeaceDividends.priority =>
            val next = ResolveNextEdictAction(priority, then)

            factions.foreach { f =>
                val l = systems.%(s => game.ceasefire.has(s.cluster)).%(f.rulesAOE).%(s => f.primus.not || game.edicts.has(PolicyOfPeace) || Empire.rules(s).not)

                if (l.any) {
                    val n = l.num * 2

                    f.power += n

                    f.log("gained", n.power)
                }
            }

            next

        case TryHarmAction(f, e, s, reserved, then) =>
            if (game.ceasefire.has(s.cluster) && f != e && e == Blight) {
                Ask(f).group("End", "Ceasefire".hl, "in cluster", s.cluster.hl.styled(styles.cluster), "spending")
                    .each(f.spendable.resources.%<(_.is(Weapon) || f.hasGuild(LoyalMarines)))((r, k) => EndCeasefireAction(f, s.cluster, PayResource(r, k), then).as(r -> k).!(|(reserved).has(PayResource(r, k))))
                    .cancel
            }
            else
                Then(then)

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            if (game.ceasefire.num >= 2) {
                f.grand += 1

                f.log("fulfilled a grand ambition with small", "Peace".hh)
            }

            if (game.ceasefire.num >= 5) {
                f.grand += 1

                f.log("fulfilled a grand ambition with large", "Peace".hh)
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
