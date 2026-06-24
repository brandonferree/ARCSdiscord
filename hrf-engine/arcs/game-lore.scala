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


abstract class Lore(val id : String, val name : String) extends CourtCard with Record with Effect with Elementary

trait UnofficialLore extends Lore

case object ToolPriests       extends Lore("lore01", "Tool Priests")
case object GalacticRifles    extends Lore("lore02", "Galactic Rifles")
case object SprinterDrives    extends Lore("lore03", "Sprinter Drives")
case object MirrorPlating     extends Lore("lore04", "Mirror Plating")
case object HiddenHarbors     extends Lore("lore05", "Hidden Harbors")
case object SignalBreaker     extends Lore("lore06", "Signal Breaker")
case object RepairDrones      extends Lore("lore07", "Repair Drones")
case object GatePorts         extends Lore("lore08", "Gate Ports")
case object CloudCities       extends Lore("lore09", "Cloud Cities")
case object LivingStructures  extends Lore("lore10", "Living Structures")
case object GateStations      extends Lore("lore11", "Gate Stations")
case object RailgunArrays     extends Lore("lore12", "Railgun Arrays")
case object AncientHoldings   extends Lore("lore13", "Ancient Holdings")
case object SeekerTorpedoes   extends Lore("lore14", "Seeker Torpedoes")
case object PredictiveSensors extends Lore("lore15", "Predictive Sensors")
case object ForceBeams        extends Lore("lore16", "Force Beams")
case object RaiderExosuits    extends Lore("lore17", "Raider Exosuits")
case object SurvivalOverrides extends Lore("lore18", "Survival Overrides")
case object EmpathsVision     extends Lore("lore19", "Empath's Vision")
case object EmpathsBond       extends Lore("lore20", "Empath's Bond")
case object KeepersTrust      extends Lore("lore21", "Keeper's Trust")
case object KeepersSolidarity extends Lore("lore22", "Keeper's Solidarity")
case object WarlordsCruelty   extends Lore("lore23", "Warlord's Cruelty")
case object WarlordsTerror    extends Lore("lore24", "Warlord's Terror")
case object TyrantsEgo        extends Lore("lore25", "Tyrant's Ego")
case object TyrantsAuthority  extends Lore("lore26", "Tyrant's Authority")
case object TycoonsAmbition   extends Lore("lore27", "Tycoon's Ambition")
case object TycoonsCharm      extends Lore("lore28", "Tycoon's Charm")
case object GuildLoyaltyLL      extends Lore("lore29", "Guild Loyalty") with UnofficialLore
case object CatapultOverdriveLL extends Lore("lore30", "Catapult Overdrive") with UnofficialLore

object Lores {
    def all = $(
        ToolPriests,
        GalacticRifles,
        SprinterDrives,
        MirrorPlating,
        HiddenHarbors,
        SignalBreaker,
        RepairDrones,
        GatePorts,
        CloudCities,
        LivingStructures,
        GateStations,
        RailgunArrays,
        AncientHoldings,
        SeekerTorpedoes,
        PredictiveSensors,
        ForceBeams,
        RaiderExosuits,
        SurvivalOverrides,
        EmpathsVision,
        EmpathsBond,
        KeepersTrust,
        KeepersSolidarity,
        WarlordsCruelty,
        WarlordsTerror,
        TyrantsEgo,
        TyrantsAuthority,
        TycoonsAmbition,
        TycoonsCharm,
        GuildLoyaltyLL,
        CatapultOverdriveLL,
    )

    def done = $(
        ToolPriests,
        GalacticRifles,
        SprinterDrives,
        MirrorPlating,
        HiddenHarbors,
        SignalBreaker,
        RepairDrones,
        GatePorts,
        CloudCities,
        LivingStructures,
        GateStations,
        RailgunArrays,
        AncientHoldings,
        SeekerTorpedoes,
        PredictiveSensors,
        ForceBeams,
        RaiderExosuits,
        SurvivalOverrides,
        EmpathsVision,
        KeepersTrust,
        WarlordsCruelty,
        TyrantsEgo,
        TycoonsCharm,
        GuildLoyaltyLL,
        CatapultOverdriveLL
    )

    def preset1 = $(SprinterDrives, PredictiveSensors, ForceBeams, CatapultOverdriveLL, SurvivalOverrides) // Movement
    def preset2 = $(LivingStructures, CloudCities, GateStations, GatePorts, ToolPriests) // Buildings
    def preset3 = $(SignalBreaker, RailgunArrays, SeekerTorpedoes, MirrorPlating, GalacticRifles) // Battle
    def preset4 = $(GuildLoyaltyLL, AncientHoldings, HiddenHarbors, RaiderExosuits, RepairDrones) // Archivist
    def preset5 = $(TycoonsCharm, TyrantsEgo, WarlordsCruelty, KeepersTrust, EmpathsVision) // Clear Outrage
    def preset6 = $(TycoonsAmbition, TyrantsAuthority, WarlordsTerror, KeepersSolidarity, EmpathsBond) // Ambitions
}

case class DiscardLoreCardAction(self : Faction, c : Lore, then : ForcedAction) extends ForcedAction

case class NurtureMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft

case class PruneMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class PruneCityAction(self : Faction, cost : Cost, s : Region, u : Figure, then : ForcedAction) extends ForcedAction
case class PruneStarportAction(self : Faction, cost : Cost, s : Region, u : Figure, then : ForcedAction) extends ForcedAction

case class MartyrMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class MartyrAction(self : Faction, cost : Cost, s : System, u : Figure, t : Figure, then : ForcedAction) extends ForcedAction

case class FireRiflesMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class FireRiflesFromAction(self : Faction, cost : Cost, s : System, then : ForcedAction) extends ForcedAction with Soft
case class FireRiflesAction(self : Faction, cost : Cost, s : System, e : Color, t : System, then : ForcedAction) extends ForcedAction
case class FireRiflesRolledAction(self : Faction, e : Color, t : System, rolled : Rolled, then : ForcedAction) extends RolledAction[$[BattleResult]]
case class EmpathsVisionFireRiflesAction(self : Faction, s : System, e : Color, skirmish : Rolled, reroll : Rolled, then : ForcedAction) extends ForcedAction
case class EmpathsVisionFireRiflesRolledAction(self : Faction, s : System, e : Color, skirmish : Rolled, rolled : Rolled, then : ForcedAction) extends RolledAction[$[BattleResult]]

case class PredictiveSensorsAction(self : Faction, s : System, u : Figure, t : System, then : ForcedAction) extends ForcedAction

case class GuideMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class GuideFromAction(self : Faction, cost : Cost, s : System, l : $[System], then : ForcedAction) extends ForcedAction with Soft
case class GuideToAction(self : Faction, cost : Cost, s : System, l : $[System], then : ForcedAction) extends ForcedAction with Soft
case class GuidePathAction(self : Faction, cost : Cost, s : System, t : System, cancel : Boolean, then : ForcedAction) extends ForcedAction with Soft
case class GuideAction(self : Faction, cost : Cost, s : System, t : System, l : $[Figure], then : ForcedAction) extends ForcedAction

case class TyrantsEgoMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class WarlordsTerrorMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft

case class AnnexMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class AnnexAction(self : Faction, cost : Cost, s : System, u : Figure, then : ForcedAction) extends ForcedAction


object LoreExpansion extends Expansion {
    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // DISCARD
        case DiscardLoreCardAction(f, l, then) =>
            f.lores --> l --> game.unusedLores

            f.log("discarded", l)

            then

        // SURVIVAL OVERRIDES
        case MartyrMainAction(f, x, then) =>
            def convert(u : Figure) = game.showFigure(u, u.damaged.??(1))

            Ask(f).group("Martyr".hl, x)
                .some(systems./~(s => f.at(s).ships.fresh.take(1)./(s -> _))) { case (s, u) =>
                    f.others.%(f.canHarm(_, s))./~(_.at(s).ships./(t =>
                        MartyrAction(f, x, s, u, t, then).as(convert(t))("Martyr".hh, "in", s)
                    ))
                }
                .cancel

        case MartyrAction(f, x, s, u, t, then) =>
            f.pay(x)

            t --> f.trophies

            t.faction.damaged :-= t

            u --> game.laws.has(TheDeadLive).?(TheDeadLive).|(f.reserve)

            f.log("martyred", t, "in", s, "with", u, x)

            then

        // LIVING STRUCTURES
        case NurtureMainAction(f, x, then) =>
            TaxMainAction(f, x, |(LivingStructures), then)

        case PruneMainAction(f, x, then) =>
            val prefix = f.short + "-"

            Ask(f)
                .group("Prune".hl)
                .some(systems)(s => f.pool(Starport).??(f.at(s).cities)./(u => PruneCityAction(f, x, s, u, then).as(City.of(f), game.showFigure(u), "in", s)))
                .some(systems)(s => f.pool(City).??(f.at(s).starports)./(u => PruneStarportAction(f, x, s, u, then).as(Starport.of(f), game.showFigure(u), "in", s)))
                .some(f.flagship.any.??(Flagship.scheme(f)))(s => f.pool(Starport).??(s.$.cities)./(u => PruneCityAction(f, x, s, u, then).as(City.of(f), game.showFigure(u), "in", s)))
                .some(f.flagship.any.??(Flagship.scheme(f)))(s => f.pool(City).??(s.$.starports)./(u => PruneStarportAction(f, x, s, u, then).as(City.of(f), game.showFigure(u), "in", s)))
                .cancel

        case PruneCityAction(f, x, s, u, then) =>
            f.pay(x)

            if (f.taxed.cities.has(u))
                f.taxed.cities :-= u

            val n = f.reserve --> Starport.of(f)

            if (f.damaged.has(u)) {
                f.damaged :-= u
                f.damaged :+= n
            }

            if (game.unslotted.has(u)) {
                game.unslotted :-= u
                game.unslotted :+= n
            }

            u --> f.reserve

            n --> s

            game.onRemoveFigure(u)

            f.log("pruned", u, "in", s, x)

            f.recalculateSlots()

            AdjustResourcesAction(then)

        case PruneStarportAction(f, x, s, u, then) =>
            f.pay(x)

            if (f.worked.has(u))
                f.worked :-= u

            val n = f.reserve --> City.of(f)

            if (f.damaged.has(u)) {
                f.damaged :-= u
                f.damaged :+= n
            }

            if (game.unslotted.has(u)) {
                game.unslotted :-= u
                game.unslotted :+= n
            }

            u --> f.reserve

            n --> s

            f.log("pruned", u, "in", s, x)

            f.recalculateSlots()

            then

        // GALACTIC RIFLES
        case FireRiflesMainAction(f, x, then) =>
            Ask(f).group("Fire Rifles".hl, x, "from")
                .each(systems.%(s => f.at(s).ships.fresh.any)) { s =>
                    FireRiflesFromAction(f, x, s, then).as(s)
                }
                .cancel

        case FireRiflesFromAction(f, x, s, then) =>
            Ask(f).group("Fire Rifles".hl, x, "from", s)
                .some(game.connected(s))(t => game.colors.but(f).%(_.at(t).any).%(f.canHarm(_, s))./(e => FireRiflesAction(f, x, s, e, t, then).as(e, "in", t)))
                .cancel

        case FireRiflesAction(f, x, s, e, t, then) =>
            f.pay(x)

            f.log("fired rifles from", s, "at", e, "in", t, x)

            Roll[$[BattleResult]](min(f.at(s).ships.fresh.num, 6).times(Skirmish.die), l => FireRiflesRolledAction(f, e, t, l, then))

        case FireRiflesRolledAction(f, e, t, l, then) =>
            f.log("rolled", l./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.token)))

            val next = AssignHitsAction(f, t, f, e, e.at(t), l.flatten.count(HitShip), 0, 0, |(GalacticRifles), $, then)

            if (game.declared.contains(Empath) && f.hasLore(EmpathsVision)) {
                val rerollable = 1.to(l.num).reverse./~(n => l.combinations(n).$)

                Ask(f)
                    .group(EmpathsVision)
                    .each(rerollable) { q =>
                        EmpathsVisionFireRiflesAction(f, t, e, l.diff(q), q, then)
                            .as("Reroll", q./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.token)))
                    }
                    .skip(next)
            }
            else
                Then(next)

        case EmpathsVisionFireRiflesAction(f, t, e, o, q, then) =>
            f.log("rerolled", q./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.token)), "with", EmpathsVision)

            Roll[$[BattleResult]](q.num.times(Skirmish.die), l => EmpathsVisionFireRiflesRolledAction(f, t, e, o, l, then))

        case EmpathsVisionFireRiflesRolledAction(f, t, e, o, n, then) =>
            f.log("rolled", n./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.token)))

            AssignHitsAction(f, t, f, e, e.at(t), (o ++ n).flatten.count(HitShip), 0, 0, |(GalacticRifles), $(EmpathsVision), then)

        // PREDICTIVE SENSORS
        case PredictiveSensorsAction(f, s, u, t, then) =>
            u --> t

            f.log("moved", u, "from", s, "to", t, "with", PredictiveSensors)

            then

        // FORCE BEAMS
        case GuideMainAction(f, x, then) =>
            val l = systems.%(s => f.at(s).starports.fresh.any)

            Ask(f)
                .group("Guide from").each(l)(s => GuideFromAction(f, x, s, game.connected(s), then).as(s).!!!)
                .group("Guide to").each(l)(s => GuideToAction(f, x, s, game.connected(s), then).as(s).!!!)
                .cancel

        case GuideFromAction(f, x, s, l, then) =>
            Ask(f).group("Guide from", s, "to")
                .each(l)(t => GuidePathAction(f, x, s, t, true, then).as(t).!!!)
                .cancel

        case GuideToAction(f, x, t, l, then) =>
            Ask(f).group("Guide to", t, "from")
                .each(l)(s => GuidePathAction(f, x, s, t, true, then).as(s).!!!)
                .cancel

        case GuidePathAction(f, x, s, t, cancel, then) =>
            val l = factions./~(_.at(s).ships)

            val combinations = l.groupBy(_.faction).$./~{ (f, l) =>
                val n = l.num
                val (damaged, fresh) = l.partition(f.damaged.has)
                val combinations = 0.to(n)./~(k => max(0, k - fresh.num).to(min(k, damaged.num))./(i => fresh.take(k - i) ++ damaged.take(i)))
                combinations.%(_.any)
            }

            implicit def convert(u : Figure) = game.showFigure(u, u.faction.damaged.has(u).??(1))

            Ask(f).group("Move from", s, "to", t)
                .each(combinations)(l => GuideAction(f, x, s, t, l, then).as(l./(u => convert(u))))
                .cancelIf(cancel)
                .doneIf(cancel.not)(then)

        case GuideAction(f, x, s, t, l, then) =>
            f.pay(x)

            f.log("guided", l.comma, "from", s, "to", t, x)

            l --> t

            GuidePathAction(f, AlreadyPaid, s, t, false, then)

        // TYRANT'S EGO
        case TyrantsEgoMainAction(f, then) =>
            implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, selected.??(2))

            XXSelectObjectsAction(f, f.captives)
                .withGroup("Secure".hl, "with", TyrantsEgo)
                .withRule(_.num(1))
                .withAuto(u => SecureMainAction(f, ReleaseCaptive(u.only), |(TyrantsEgo), false, true, then).as("Secure"))
                .withExtras(CancelAction)


        // WARLORD'S TERROR
        case WarlordsTerrorMainAction(f, then) =>
            implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, selected.?(2).|(1))

            XXSelectObjectsAction(f, f.trophies)
                .withGroup("Influence".hl, "with", WarlordsTerror)
                .withRule(_.num(1))
                .withAuto(u => InfluenceMainAction(f, ReleaseTrophy(u.only), |(WarlordsTerror), false, true, then).as("Influence"))
                .withExtras(CancelAction)

        // TYRANT'S AUTHORITY
        case AnnexMainAction(f, x, then) =>
            var targets = systems.%(f.rules)./~(s => s.$.buildings.%(_.faction != f)./(_ -> s))

            Ask(f).group("Annex".hl, x)
                .each(targets) { case (u, s) => AnnexAction(f, x, s, u, then).as(u, game.showFigure(u), "in", s).!(f.pool(u.piece).not, "max") }
                .cancel

        case AnnexAction(f, x, s, u, then) =>
            f.pay(x)

            f.log("annexed", u, x)

            u --> u.faction.reserve

            val n = f.reserve.$.piece(u.piece).first

            n --> s

            if (u.damaged) {
                u.faction.damaged :-= u
                f.damaged :+= n
            }

            if (u.piece == City) {
                game.seats.keys.foreach { i =>
                    if (game.seats.get(i).has(u))
                        game.seats += i -> n
                }

                u.faction.as[Faction].foreach { e =>
                    e.recalculateSlots()
                }

                f.recalculateSlots()
            }

            AdjustResourcesAction(then)


        // ...
        case _ => UnknownContinue
    }
}
