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

import scala.collection.immutable.ListMap


case object BoardFull extends BaseBoard {
    val name = "Full Board"
    val clusters = $(1, 2, 3, 4, 5, 6)

    val starting : $[(System, System, $[System])] = $
}

trait Tough { self : Piece => }

case object Blight extends Piece with Tough

case object Blights extends Color {
    override def name = "Blight"
    override def id = "Blights"
}

case object Empire extends Color
case object Free extends Color {
    override def name = "Free States"
    override def id = "Free"
}

case object Neutrals extends Color


abstract class ImperialCard(val id : String, val name : String) extends CourtCard {
    override def elem = name.styled(Empire)(styles.title)
}

case object ImperialCouncilInSession extends ImperialCard("aid01a", "Imperial Council")
case object ImperialCouncilDecided extends ImperialCard("aid01b", "Imperial Council Decided")

case object FirstRegent extends Elementary {
    def elem = "First Regent".styled(Empire)(styles.title)
}


case object ImperialTrust extends ResourceSlot with Elementary {
    override val stealable = true
    def elem = "Imperial Trust".styled(Empire)(styles.title)
}


abstract class Fate(val name : String, val id : String, val act : Int) extends Record with Elementary {
    def img = Image(id, styles.fateCard)
    def elem = (id == "no-fate").?(name.hh.styled(styles.title).spn(styles.notDoneYet)).|(name.styled(styles.titleW))

    val expansion : FateExpansion = null
}


object Fates {
    val act1 = $(
        Steward       ,
        Founder       ,
        Magnate       ,
        Advocate      ,
        Caretaker     ,
        Partisan      ,
        Admiral       ,
        Believer      ,
    )

    val act2 = $(
        Pathfinder    ,
        Hegemon       ,
        PlanetBreaker ,
        Pirate        ,
        BlightSpeaker ,
        Pacifist      ,
        Peacekeeper   ,
        Warden        ,
    )

    val act3 = $(
        Overlord      ,
        Survivalist   ,
        Redeemer      ,
        Guardian      ,
        Naturalist    ,
        GateWraith    ,
        Conspirator   ,
        Judge         ,
    )

    val ready : $[Fate] = act1 ++ act2 ++ act3
}


abstract class Edict(val id : String, val name : String, val priority : String) extends Effect with Elementary {
    def img = Image(id, styles.card)
    def elem = name.styled(styles.titleW)
}

abstract class Objective(val id : String, val name : String) extends Effect with Elementary {
    def img = Image(id, styles.card)
    def elem = name.styled(styles.titleW)
}

abstract class Ability(val id : String, val name : String) extends Effect with Elementary {
    def img = Image(id, styles.card)
    def elem = name.styled(styles.titleW)
}

abstract class Law(val id : String, val name : String) extends Effect with Elementary {
    def img = Image(id, styles.card)
    def elem = name.styled(styles.titleW)
}

case object ImperialControlCommand extends Law("aid14", "Imperial Control & Command")
case object ImperialMovement extends Law("aid15", "Imperial Movement")
case object ImperialTruce extends Law("aid16", "Imperial Truce")

case object FlagshipUpgradesAidA extends Law("aid10a", "Flagship Upgrades")
case object FlagshipUpgradesAidB extends Law("aid10b", "Flagship Upgrades")


case object PolicyOfPeace extends Edict("aid04", "A Policy of Peace", "03-peace")
case object PolicyOfEscalation extends Edict("aid05", "A Policy of Escalation", "03-escalation")
case object PolicyOfWar extends Edict("aid03", "A Policy of War", "03-war")


trait FateCrisis extends Effect

case object CouncilIntrigue extends VoxEffect("Council Intrigue")
case object DiplomaticFiasco extends VoxEffect("Diplomatic Fiasco")
case object BlightLooms extends VoxEffect("Blight Looms")
case object PopulistDemands extends VoxEffect("Populist Demands")
case object SongOfFreedom extends VoxEffect("Song of Freedom")


case class IntermissionReport(act : Int, units : $[(System, $[Figure])], court : $[CourtCard], deck : $[DeckCard], laws : $[Law], edicts : $[Edict], players : $[(Faction, IntermissionPlayerReport)])
case class IntermissionPlayerReport(fate : Fate, past : $[Fate], power : Int, regent : Boolean, primus : Boolean, abilities : $[Ability], lores : $[Lore], guilds : $[GuildCard], resources : $[ResourceLike], outraged : $[Resource], favors : $[Figure])


object BlightCards {
    val court = $(
        GuildCard("cc01", MiningInterest),
        GuildCard("cc02", ConstructionUnion),
        GuildCard("cc03", Gatekeepers),
        GuildCard("cc04", ShippingInterest),
        GuildCard("cc05", Skirmishers),
        GuildCard("cc06", ArmsUnion),
        GuildCard("cc07", LatticeSpies),
        GuildCard("cc08", SilverTongues),
        GuildCard("cc09", SwornGuardians),
        GuildCard("cc10", ElderBroker),
        VoxCard("cc11", PopulistDemands),
        VoxCard("cc12", CouncilIntrigue),
        VoxCard("cc13", DiplomaticFiasco),
        VoxCard("cc14", SongOfFreedom),
        VoxCard("cc15", BlightLooms),
    )

    val sidedeck = $(
        ImperialCouncilInSession,
        ImperialCouncilDecided,
    )
}

case object Flagship extends Piece {
    def functions(f : Faction) = $(SlipstreamDrive(f), TractorBeam(f), ControlArray(f), DefenseArray(f), ShipCrane(f), Hull(f))
    def armors(f : Faction) = functions(f)./(_.armor)
    def scheme(f : Faction) = functions(f) ++ armors(f)
}


trait FlagshipUpgrade extends SpecialRegion with Elementary with Record {
    val name : String
    val faction : Faction
    def elem : Elem = name.styled(styles.title).styled(faction)
}

case class Armor(upgrade : FunctionalUpgrade) extends FlagshipUpgrade {
    override val name = upgrade.name + " Armor"
    override val faction = upgrade.faction
}

trait FunctionalUpgrade extends FlagshipUpgrade {
    def armor = Armor(this)
}

case class SlipstreamDrive(override val faction : Faction) extends FunctionalUpgrade { override val name = "Slipstream Drive" }
case class TractorBeam(override val faction : Faction) extends FunctionalUpgrade { override val name = "Tractor Beam" }
case class ControlArray(override val faction : Faction) extends FunctionalUpgrade { override val name = "Control Array" }
case class DefenseArray(override val faction : Faction) extends FunctionalUpgrade { override val name = "Defense Array" }
case class ShipCrane(override val faction : Faction) extends FunctionalUpgrade { override val name = "Ship Crane" }
case class Hull(override val faction : Faction) extends FunctionalUpgrade { override val name = "Hull" }

case object Slipstream extends Effect with Elementary {
    def elem = "Slipstream".styled(styles.titleW)
}


case class DealFatesAction(then : ForcedAction) extends ForcedAction
case class ChooseFatesAction(then : ForcedAction) extends ForcedAction
case class ChooseFateAction(self : Faction, x : Fate, then : ForcedAction) extends ForcedAction
case class FatesShuffledAction(shuffled : $[Fate], then : ForcedAction) extends ShuffledAction[Fate]
case object AddLoreToCourtAction extends ForcedAction
case class LoreToCourtShuffledAction(shuffled : $[Lore]) extends ShuffledAction[Lore]
case object SetupEmpireAction extends ForcedAction
case class EmpireClustersRandomAction(random : $[Int]) extends RandomAction[$[Int]]
case class FreeCitiesRandomAction(random : Symbol) extends RandomAction[Symbol]
case class GovernEdictRandomAction(random : Resource) extends RandomAction[Resource]

case object BuildingsShipsSetupAction extends ForcedAction
case object FatesChosenAction extends ForcedAction
case class FatesSetupAction(l : $[Faction]) extends ForcedAction /*with Soft*/
case class FateSetupAction(f : Faction, then : ForcedAction) extends ForcedAction
case class PlaceCityAndShipsAction(self : Faction, s : System) extends ForcedAction
case class PlaceStarportAndShipsAction(self : Faction, s : System) extends ForcedAction
case object FillFreeCitiesSetupAction extends ForcedAction
case object NewActSetupAction extends ForcedAction
case object SpawnMoreBlightAction extends ForcedAction
case class SpawnMoreBlightRolledAction(random : Symbol) extends RandomAction[Symbol]

case class ResolveEventAction(self : Faction) extends ForcedAction
case class ResolveCouncilAction(self : Faction) extends ForcedAction

case class CouncilBonusAction(self : Faction) extends ForcedAction
case class CouncilStealMainAction(self : Faction, e : Faction, l : $[ResourceToken], then : ForcedAction) extends ForcedAction with Soft
case class CouncilStealLoseAction(self : Faction, e : Faction, l : $[ResourceToken], then : ForcedAction) extends ForcedAction

case class BecomeOutlawAction(self : Faction, then : ForcedAction) extends ForcedAction
case class BecomeRegentAction(self : Faction, then : ForcedAction) extends ForcedAction

case class SetupFlagshipMainAction(self : Faction, l : $[|[Piece]], then : ForcedAction) extends ForcedAction
case class SetupFlagshipAction(self : Faction, s : System, l : $[|[Piece]], then : ForcedAction) extends ForcedAction
case class SlipstreamDriveFlagshipMainAction(self : Faction, s : System, flagship : Figure, then : ForcedAction) extends ForcedAction with Soft
case class SlipstreamDriveOtherMainAction(self : Faction, s : System, flagship : Figure, then : ForcedAction) extends ForcedAction with Soft
case class MayResettleAction(self : Faction, then : ForcedAction) extends ForcedAction
case class ResettleAction(self : Faction, then : ForcedAction) extends ForcedAction
case class ResettleBuildingsMainAction(self : Faction, l : $[Piece], then : ForcedAction) extends ForcedAction
case class ResettleBuildingsAction(self : Faction, p : Piece, s : System, then : ForcedAction) extends ForcedAction
case class ResettleBuildingsReplaceAction(self : Faction, p : Piece, s : System, u : Figure, then : ForcedAction) extends ForcedAction

case class ReplenishShipsMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction
case class ReplenishShipsLoyalMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction
case class ScrapShipsMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction
case class ScrapShipsAction(self : Faction, s : |[System], l : $[Figure], then : ForcedAction) extends ForcedAction

case class ChooseEventAction(self : Faction) extends ForcedAction
case class ChooseCrisesAction(self : Faction) extends ForcedAction
case class ChooseEdictsAction(self : Faction) extends ForcedAction

case class RollForEventAction(self : Faction) extends ForcedAction

case class EventRollAction(self : Faction, edicts : $[Boolean], clusters : $[Int], symbols : $[Symbol], used : $[Effect]) extends ForcedAction
case class EventRolledAction(self : Faction, random1 : Boolean, random2 : Int, random3 : Symbol, used : $[Effect]) extends Random3Action[Boolean, Int, Symbol]
case class EventProcessAction(self : Faction, edict : Boolean, cluster : Int, symbol : Symbol) extends ForcedAction

case class ResolveCrisesAction(cluster : Int, symbol : Symbol, then : ForcedAction) extends ForcedAction
case class BlightCrisesGlobalAction(then : ForcedAction) extends ForcedAction
case class BlightCrisesMainAction(l : $[System], exempt : $[Faction], abstained : $[Faction], then : ForcedAction) extends ForcedAction with Soft

case class BlightCrisesAvertAction(f : Faction, x : Cost, then : ForcedAction) extends ForcedAction
case class BlightCrisesPerformAction(l : $[System], exempt : $[Faction], then : ForcedAction) extends ForcedAction
case class FateCrisesAction(cluster : Int, symbol : Symbol, then : ForcedAction) extends ForcedAction
case class FateCrisesFactionAction(f : Faction, cluster : Int, symbol : Symbol, done : $[FateCrisis], then : ForcedAction) extends ForcedAction
case class FateCrisisAction(f : Faction, c : FateCrisis, cluster : Int, symbol : Symbol, then : ForcedAction) extends ForcedAction
case class CourtCrisesAction(cluster : Int, symbol : Symbol, then : ForcedAction) extends ForcedAction

case class BuryCrisisVoxCardAction(v : VoxCard, lane : Int, then : ForcedAction) extends ForcedAction
case class UnderTopCrisisVoxCardAction(v : VoxCard, lane : Int, then : ForcedAction) extends ForcedAction
case class DiscardCrisisVoxCardAction(v : VoxCard, lane : Int, then : ForcedAction) extends ForcedAction

case class CourtCrisesContinueAction(cluster : Int, symbol : Symbol, lane : Int, skip : Int, then : ForcedAction) extends ForcedAction
case class CourtCrisesPerformAction(cluster : Int, symbol : Symbol, lane : Int, skip : Int, main : Boolean, v : VoxCard, then : ForcedAction) extends ForcedAction

case class SpreadAgentsAction(self : Faction, lane : Int, then : ForcedAction) extends ForcedAction with Soft
case class SpreadAgentAction(self : Faction, lane : Int, n : Int, then : ForcedAction) extends ForcedAction

case class ResolveEdictsAction(then : ForcedAction) extends ForcedAction
case class ResolveNextEdictAction(priority : String, then : ForcedAction) extends ForcedAction
case class ResolveEdictAction(priority : String, then : ForcedAction) extends ForcedAction
case class EnforcePolicyAction(self : Faction, priority : String, then : ForcedAction) extends ForcedAction
case class SwitchPolicyAction(self : Faction, priority : String, to : String, then : ForcedAction) extends ForcedAction

case class CollectDemandAction(self : Faction, resources : $[Resource], l : $[Faction], then : ForcedAction) extends ForcedAction
case class CollectTrophyDemandAction(self : Faction, l : $[Faction], then : ForcedAction) extends ForcedAction
case class CollectResourceAction(self : Faction, e : Faction, r : ResourceLike, k : ResourceSlot, then : ForcedAction) extends ForcedAction
case class CollectTrophyAction(self : Faction, e : Faction, u : Figure, then : ForcedAction) extends ForcedAction
case class CollectCaptiveAction(self : Faction, e : Faction, u : Figure, then : ForcedAction) extends ForcedAction
case class CollectFavorAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction
case class CollectExpelAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction

case class PeaceBonusAction(self : Faction, priority : String, then : ForcedAction) extends ForcedAction
case class EscalationBonusAction(self : Faction, priority : String, then : ForcedAction) extends ForcedAction with Soft
case class WarBonusAction(self : Faction, priority : String, then : ForcedAction) extends ForcedAction with Soft

case class EmpireShipsAction(self : Faction, l : $[System], then : ForcedAction) extends ForcedAction
case class DistributeWeaponsAction(self : Faction, l : $[Faction], then : ForcedAction) extends ForcedAction

case class FateInitAction(f : Faction, fate : Fate, act : Int, then : ForcedAction) extends ForcedAction
case class FateSetupInitAction(f : Faction, fate : Fate, act : Int, then : ForcedAction) extends ForcedAction
case object IntermissionAction extends ForcedAction
case class FateFailAction(f : Faction, fate : Fate, act : Int, then : ForcedAction) extends ForcedAction
case class FateDoneAction(f : Faction, fate : Fate, act : Int, then : ForcedAction) extends ForcedAction
case class FateSetupFailAction(f : Faction, fate : Fate, act : Int, then : ForcedAction) extends ForcedAction
case class FateSetupDoneAction(f : Faction, fate : Fate, act : Int, then : ForcedAction) extends ForcedAction
case class FateResolveAction(f : Faction, then : ForcedAction) extends ForcedAction

case object IntermissionClearCourtAction extends ForcedAction
case object IntermissionClearPiecesAction extends ForcedAction
case object IntermissionRepairDestroyAction extends ForcedAction
case object IntermissionInitiativePowerAction extends ForcedAction
case object IntermissionNextActAction extends ForcedAction

case object BlightCheckWinAction extends ForcedAction

case object SetupActTwoAction extends ForcedAction with Soft
case class SetupActOneFateMainAction(self : Faction, f : Faction) extends ForcedAction with Soft
case class SetupActOneFateAction(self : Faction, f : Faction, fate : Fate, success : Boolean) extends ForcedAction
case class SetupPlayerOrderMainAction(self : Faction) extends ForcedAction with Soft
case class SetupPlayerOrderAction(self : Faction, l : $[Faction], then : ForcedAction) extends ForcedAction
case object SetupFatesAction extends ForcedAction
case object SetupInitFatesAction extends ForcedAction
case object SetupInitFatesDoneAction extends ForcedAction

case class SetupCourtDeckMainAction(host : Faction) extends ForcedAction with Soft with Only[SetupCourtDeckScrapAction] { def tag = implicitly }
case class SetupCourtDeckScrapAction(host : Faction, l : $[CourtCard]) extends ForcedAction

case class SetupCourtScrapMainAction(host : Faction) extends ForcedAction with Soft with Only[SetupCourtScrapUnscrapAction] { def tag = implicitly }
case class SetupCourtScrapUnscrapAction(host : Faction, l : $[CourtCard]) extends ForcedAction

case class SetupActionDeckMainAction(host : Faction) extends ForcedAction with Soft
case class SetupPlayerAction(host : Faction, f : Faction) extends ForcedAction with Soft
case class SetupPlayerPlaceMainAction(host : Faction, f : Color, p : Piece, unslotted : Boolean) extends ForcedAction with Soft with Only[SetupPlayerPlaceAction] { def tag = implicitly }
case class SetupPlayerPlaceAction(host : Faction, f : Color, p : Piece, unslotted : Boolean, s : System) extends ForcedAction
case class SetupPlayerPlaceShipMainAction(host : Faction, f : Color) extends ForcedAction with Soft with Only[SetupPlayerPlaceShipAction] { def tag = implicitly }
case class SetupPlayerPlaceShipAction(host : Faction, f : Color, s : System) extends ForcedAction with Soft
case class SetupPlayerPlaceShipListAction(host : Faction, f : Color, s : System, n : Int) extends ForcedAction
case class SetupPlayerRemoveMainAction(host : Faction) extends ForcedAction with Soft with Only[SetupPlayerRemoveAction] { def tag = implicitly }
case class SetupPlayerRemoveSystemAction(host : Faction, s : System) extends ForcedAction with Soft
case class SetupPlayerRemoveAction(host : Faction, u : Figure, s : System) extends ForcedAction
case class SetupPlayerAdjustPowerMainAction(host : Faction, faction : Faction, power : Int) extends ForcedAction with Soft with SelfExplode with SelfValidate {
    def validate(target : Action) = target @@ {
        case SetupPlayerAdjustPowerMainAction(h, f, t) => host == h && faction == f
        case SetupPlayerAdjustPowerAction(h, f, t) => host == h && faction == f
        case _ => false
    }

    def explode(withSoft : Boolean)(implicit game : Game) = {
        $
    }
}

case class SetupPlayerAdjustPowerAction(host : Faction, f : Faction, power : Int) extends ForcedAction
case class SetupPlayerPrimusAction(host : Faction, f : Faction) extends ForcedAction
case class SetupPlayerRegentAction(host : Faction, f : Faction) extends ForcedAction
case class SetupPlayerOutlawAction(host : Faction, f : Faction) extends ForcedAction

case class SetupPlayerOutrageMainAction(host : Faction, f : Faction) extends ForcedAction with Soft
case class SetupPlayerOutrageAction(host : Faction, f : Faction, r : Resource, state : Boolean) extends ForcedAction

case class SetupPlayerResourceMainAction(host : Faction, f : Faction) extends ForcedAction with Soft
case class SetupPlayerResourceAction(host : Faction, f : Faction, r : Resource) extends ForcedAction
case class SetupPlayerResourceClearAction(host : Faction, f : Faction) extends ForcedAction

case class SetupPlayerTakeLoreMainAction(host : Faction, f : Faction) extends ForcedAction with Soft
case class SetupPlayerTakeLoreAction(host : Faction, f : Faction, l : Lore) extends ForcedAction
case class SetupPlayerRemoveLoreMainAction(host : Faction, f : Faction) extends ForcedAction with Soft
case class SetupPlayerRemoveLoreAction(host : Faction, f : Faction, l : Lore) extends ForcedAction

case class SetupPlayerTakeGuildMainAction(host : Faction, f : Faction) extends ForcedAction with Soft
case class SetupPlayerTakeGuildAction(host : Faction, f : Faction, l : GuildCard) extends ForcedAction
case class SetupPlayerRemoveGuildMainAction(host : Faction, f : Faction) extends ForcedAction with Soft
case class SetupPlayerRemoveGuildAction(host : Faction, f : Faction, l : GuildCard) extends ForcedAction

case object SetupActTwoFinishAction extends ForcedAction

case class SetupInitiativeAction(host : Faction, f : Faction) extends ForcedAction
case class SetupPolicyAction(self : Faction, to : String, then : ForcedAction) extends ForcedAction

case object SetupChooseFatesAction extends ForcedAction

case class DebugGainResourceAction(f : Faction, r : Resource, then : ForcedAction) extends ForcedAction
case class DebugFailAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugSucceedAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugRedealCourtAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugPipsAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugCrisesAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugEdictsAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugSummitAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugUnlockAction(f : Faction, then : ForcedAction) extends ForcedAction
case class DebugSuitAction(f : Faction, suit : Suit, then : ForcedAction) extends ForcedAction


abstract class FateExpansion(val fate : Fate) extends Expansion {
    val deck : $[CourtCard]
}

object BlightExpansion extends Expansion {
    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        case ArcsBlightedReachFromActTwoAction(_, _, _) =>
            game.setup.foreach { f =>
                game.states += f -> new FactionState(f)

                f.recalculateSlots()
            }

            game.states += Blights -> new BlightsState(Blights)
            game.states += Empire -> new EmpireState(Empire)
            game.states += Free -> new FreeState(Free)
            game.states += Neutrals -> new NeutralsState(Neutrals)

            game.laws :+= ImperialControlCommand
            game.laws :+= ImperialMovement
            game.laws :+= ImperialTruce

            factions.foreach { f =>
                f.regent = true
            }

            MultiAsk(factions./ { h =>
                Ask(h).group("Play order")
                    .each(factions.permutations.$)(l => SetupPlayerOrderAction(h, l, SetupFatesAction).as(l.intersperse(Comma)))
            })

        case SetupPlayerOrderAction(h, l, then) =>
            game.seating = l

            game.factions = game.seating

            game.sidedeck --> ImperialCouncilInSession --> game.council

            log("Play order", l)

            then

        case SetupFatesAction =>
            if (factions.%(_.fates.none).any)
                MultiAsk(factions./ { h =>
                    Ask(h).group("Setup Act I Fate")
                        .each(factions.%(_.fates.none).%(_.failed.none))(f => SetupActOneFateMainAction(h, f).as(f))
                })
            else
                SetupInitFatesAction

        case SetupActOneFateMainAction(h, f) =>
            Ask(h).group(f, "Fate")
                .some(Fates.act1.diff(factions./~(_.fates)).diff(factions./~(_.failed)))(fate => SetupActOneFateAction(h, f, fate, true).as(fate, "Success") :: SetupActOneFateAction(h, f, fate, false).as(fate, "Fail"))
                .cancel

        case SetupActOneFateAction(h, f, fate, success) =>
            f.log(success.?("succeeded").|("failed"), "as", fate)

            f.fates = $(fate)

            if (f.past.has(fate).not)
                f.past :+= fate

            f.failed ++= success.not.$(fate)

            if (fate.act == 1) {
                game.courtiers.register(FateDeck(fate), content = fate.expansion.deck)

                game.expansions = fate.expansion +: game.expansions
            }

            SetupFatesAction

        case SetupInitFatesAction =>
            factions.foldLeft(SetupInitFatesDoneAction : ForcedAction) { (q, f) =>
                val fate = f.fates.only

                if (f.failed.has(fate))
                    FateSetupInitAction(f, fate, 1, FateSetupFailAction(f, fate, 1, q))
                else
                    FateSetupInitAction(f, fate, 1, FateSetupDoneAction(f, fate, 1, q))
            }

        case SetupInitFatesDoneAction =>
            factions.foreach { f =>
                if (f.fates.only.in(f.failed))
                    f.fates = $
                else
                    f.progress = 0
            }

            SetupActTwoAction

        case SetupActTwoAction =>
            MultiAsk(factions./ { h =>
                Ask(h).group("Act II setup")
                    .add(SetupPlayerOrderMainAction(h).as("Play order"))
                    .each(factions)(f => SetupPlayerAction(h, f).as(f))
                    .add(SetupPlayerPlaceShipMainAction(h, Empire).as(Empire))
                    .add(SetupPlayerPlaceMainAction(h, Free, City, false).as(City.of(Free)))
                    .add(SetupPlayerPlaceMainAction(h, Free, Starport, false).as(Starport.of(Free)))
                    .add(SetupPlayerPlaceMainAction(h, Blights, Blight, false).as(Blights))
                    .add(SetupPlayerRemoveMainAction(h).as("Remove Piece"))
                    .add(SetupCourtDeckMainAction(h).as("Court Deck"))
                    .add(SetupActionDeckMainAction(h).as("Action Deck"))
                    .add(SetupActTwoFinishAction.as("Finish"))
                    .needOk
            })

        case SetupPlayerOrderMainAction(h) =>
            Ask(h).group("Play order")
                .each(factions.permutations.$)(l => SetupPlayerOrderAction(h, l, SetupActTwoAction).as(l.intersperse(Comma)))

        case SetupPlayerAction(h, f) =>
            Ask(h).group(f)
                .add(SetupPlayerAdjustPowerMainAction(h, f, f.power).as("Adjust Power"))
                .when(f.regent && f.primus.not)(SetupPlayerPrimusAction(h, f).as("Become First Regent"))
                .when(f.regent)(SetupPlayerOutlawAction(h, f).as("Become Outlaw"))
                .when(f.regent.not)(SetupPlayerRegentAction(h, f).as("Become Regent"))
                .add(SetupPlayerResourceMainAction(h, f).as("Resources"))
                .add(SetupPlayerOutrageMainAction(h, f).as("Outrage"))
                .add(SetupPlayerPlaceMainAction(h, f, City, false).as("Place", game.showFigure(Figure(f, City, 0)), City.of(f)))
                .add(SetupPlayerPlaceMainAction(h, f, Starport, false).as("Place", game.showFigure(Figure(f, Starport, 0)), Starport.of(f)))
                .add(SetupPlayerPlaceShipMainAction(h, f).as("Place", game.showFigure(Figure(f, Ship, 0)), Ship.of(f)))
                .add(SetupPlayerTakeLoreMainAction(h, f).as("Add Lore"))
                .add(SetupPlayerRemoveLoreMainAction(h, f).as("Remove Lore"))
                .add(SetupPlayerTakeGuildMainAction(h, f).as("Add Guild"))
                .add(SetupPlayerRemoveGuildMainAction(h, f).as("Remove Guild"))
                .cancel

        case SetupPlayerPlaceShipMainAction(h, f) =>
            Ask(h).group("Place", Ship.sof(f), "in")
                .each(systems)(s => SetupPlayerPlaceShipAction(h, f, s).as(s))
                .cancel

        case SetupPlayerPlaceShipAction(h, f, s) =>
            Ask(h).group("Place in", s)
                .each(1.to(min(6, f.pooled(Ship))).$)(i => SetupPlayerPlaceShipListAction(h, f, s, i).as(i.times(game.showFigure(Figure(f, Ship, 0)))))
                .cancel

        case SetupPlayerPlaceShipListAction(h, f, s, n) =>
            val l = f.reserve.$.ships.take(n)

            l --> s

            f.log("placed", l.intersperse(Comma), "in", s)

            SetupActTwoAction

        case SetupPlayerPlaceMainAction(h, f, p, unslotted) =>
            Ask(h).group("Place", p.of(f), "in")
                .each(systems)(s => SetupPlayerPlaceAction(h, f, p, unslotted, s).as(s))
                .cancel

        case SetupPlayerPlaceAction(h, f, p, unslotted, s) =>
            val u = f.reserve --> p

            u --> s

            f.log("placed", u, unslotted.?("out of slot"), "in", s)

            if (unslotted)
                game.unslotted :+= u

            if (u.piece == City) {
                u.faction.as[Faction].foreach { f =>
                    f.recalculateSlots()

                    $(f.overflow).resources.content.foreach(f.take)
                }
            }

            SetupActTwoAction

        case SetupPlayerRemoveMainAction(h) =>
            Ask(h).group("Remove from")
                .each(systems.%(_.any))(s => SetupPlayerRemoveSystemAction(h, s).as(s))
                .cancel

        case SetupPlayerRemoveSystemAction(h, s) =>
            Ask(h).group("Remove from", s)
                .each(s.$)(u => SetupPlayerRemoveAction(h, u, s).as(u, game.showFigure(u)))
                .cancel

        case SetupPlayerRemoveAction(h, u, s) =>
            u --> u.faction.reserve

            if (game.unslotted.has(u))
                game.unslotted :-= u

            u.faction.log("removed", u, "from", s)

            if (u.piece == City)
                u.faction.as[Faction].foreach(_.recalculateSlots())

            SetupActTwoAction

        case SetupPlayerPrimusAction(h, f) =>
            f.primus = true

            factions.%(_.regent).but(f).foreach { e =>
                e.primus = false
            }

            f.log("became the", "First Regent".styled(Empire))

            SetupActTwoAction

        case SetupPlayerRegentAction(h, f) =>
            BecomeRegentAction(f, SetupActTwoAction)

        case SetupPlayerOutlawAction(h, f) =>
            BecomeOutlawAction(f, SetupActTwoAction)

        case SetupPlayerAdjustPowerMainAction(h, f, power) =>
            Ask(h).group("Adjust", f, "power to", power.power)
                .add(SetupPlayerAdjustPowerMainAction(h, f, power + 1).as("+1"))
                .add(SetupPlayerAdjustPowerMainAction(h, f, power + 10).as("+10"))
                .add(SetupPlayerAdjustPowerMainAction(h, f, power - 10).as("-10"))
                .add(SetupPlayerAdjustPowerMainAction(h, f, power - 1).as("-1"))
                .add(SetupPlayerAdjustPowerAction(h, f, power).as("Done".hh))

        case SetupPlayerAdjustPowerAction(h, f, power) =>
            f.power = power

            f.log("power adjusted to", f.power.power)

            SetupActTwoAction

        case SetupPlayerResourceMainAction(h, f) =>
            Ask(h).group(f, "Resources")
                .each($(Material, Fuel, Weapon, Relic, Psionic))(r => SetupPlayerResourceAction(h, f, r).as(r))
                .add(SetupPlayerResourceClearAction(h, f).as("Clear Resources"))
                .cancel

        case SetupPlayerResourceAction(h, f, r) =>
            f.gain(r)

            f.log("gained", r)

            SetupActTwoAction

        case SetupPlayerResourceClearAction(host, f) =>
            (f.spendable :+ f.overflow).resources.content.foreach { r => r --> r.supply }

            f.log("cleared resources")

            SetupActTwoAction

        case SetupPlayerOutrageMainAction(h, f) =>
            Ask(h).group(f, "Outrage")
                .each($(Material, Fuel, Weapon, Relic, Psionic))(r => SetupPlayerOutrageAction(h, f, r, f.outraged.has(r).not).as(f.outraged.has(r).?("Clear").|("Set"), r, "Outrage"))
                .cancel

        case SetupPlayerOutrageAction(h, f, r, state) =>
            if (state) {
                f.outraged :+= r

                f.log("outraged", r)
            }
            else {
                f.outraged :-= r

                f.log("cleared", r, "outrage")
            }

            SetupActTwoAction

        case SetupPlayerTakeLoreMainAction(h, f) =>
            Ask(h).group(f, "takes Lore")
                .each(game.allLores.of[Lore].sortBy(_.name))(l => SetupPlayerTakeLoreAction(h, f, l).as(l))
                .cancel

        case SetupPlayerTakeLoreAction(h, f, l) =>
            game.allLores --> l --> f.lores

            f.log("took", l)

            f.recalculateSlots()

            SetupActTwoAction

        case SetupPlayerRemoveLoreMainAction(h, f) =>
            Ask(h).group(f, "removes Lore")
                .each(f.lores.of[Lore])(l => SetupPlayerRemoveLoreAction(h, f, l).as(l))
                .cancel

        case SetupPlayerRemoveLoreAction(h, f, l) =>
            f.lores --> l --> game.allLores

            f.log("removed", l)

            f.recalculateSlots()

            SetupActTwoAction

        case SetupPlayerTakeGuildMainAction(h, f) =>
            Ask(h).group(f, "takes Guild Card")
                .each(game.court.of[GuildCard].sortBy(_.name))(l => SetupPlayerTakeGuildAction(h, f, l).as(l))
                .cancel

        case SetupPlayerTakeGuildAction(h, f, l) =>
            game.court --> l --> f.loyal

            f.log("took", l)

            f.recalculateSlots()

            SetupActTwoAction

        case SetupPlayerRemoveGuildMainAction(h, f) =>
            Ask(h).group(f, "removes Guild Card")
                .each(f.loyal.of[GuildCard])(l => SetupPlayerRemoveGuildAction(h, f, l).as(l))
                .cancel

        case SetupPlayerRemoveGuildAction(h, f, l) =>
            f.loyal --> l --> game.court

            f.log("removed", l)

            f.recalculateSlots()

            SetupActTwoAction

        case SetupCourtDeckMainAction(h) =>
            XXSelectObjectsAction(h, game.court.$.sortBy(_.id))
                .withGroup("Court Deck", "(" ~ game.court.num.hlb ~ " cards)")
                .withRule(_.upTo(3))
                .withThen(l => SetupCourtDeckScrapAction(h, l).as("Scrap", l.intersperse(Comma)))
                .withExtras(SetupActTwoAction.as("Done"))

        case SetupCourtDeckScrapAction(h, l) =>
            log("Scrapped", l.intersperse(Comma))

            l --> CourtScrap

            SetupActTwoAction

        case SetupCourtScrapMainAction(h) =>
            XXSelectObjectsAction(h, CourtScrap.$.sortBy(_.id))
                .withGroup("Court Scrap")
                .withRule(_.upTo(3))
                .withThen(l => SetupCourtScrapUnscrapAction(h, l).as("Unscrap", l.intersperse(Comma)))
                .withExtras(SetupActTwoAction.as("Done"))

        case SetupCourtScrapUnscrapAction(h, l) =>
            log("Unscrapped", l.intersperse(Comma))

            l --> game.court

            SetupActTwoAction

        case SetupActionDeckMainAction(h) =>
            YYSelectObjectsAction(h, game.deck)
                .withGroup("Action Deck")
                .withExtras(SetupActTwoAction.as("Done"))

        case SetupActTwoFinishAction =>
            MultiAsk(factions./ { h =>
                Ask(h).group("Initiative")
                    .each(factions)(f => SetupInitiativeAction(h, f).as(f).!(f.rivals.exists(_.power > f.power)))
            })

        case SetupInitiativeAction(h, f) =>
            game.factions = factions.dropWhile(_ != f) ++ factions.takeWhile(_ != f)

            f.log("had the initiative")

            game.act = 2

            factions.foreach { f =>
                f.fates ++= Fates.act2
            }

            val policies = $(PolicyOfPeace, PolicyOfEscalation, PolicyOfWar)

            implicit val convert = (x : Edict) => x.img

            MultiAsk(factions./{ h =>
                YYSelectObjectsAction(h, policies)
                    .withGroup("Select Policy")
                    .withThen(p => SetupPolicyAction(h, p.priority, SetupChooseFatesAction).as(p))("~~~")
                    .ask
            })

        case SetupPolicyAction(f, to, then) =>
            val policies = $(PolicyOfPeace, PolicyOfEscalation, PolicyOfWar)

            game.edicts :+= policies.%(_.priority == to).only

            game.edicts = game.edicts.sortBy(_.priority)

            log("Policy was", policies.%(_.priority == to).only)

            then

        case SetupChooseFatesAction =>
            val l = factions.%(_.fates.num > 1)

            implicit val convert = (x : Fate) => x.img

            if (l.none)
                MultiAsk(factions./{ h =>
                    Ask(h).add(FatesChosenAction.as("Action!".hl)("Everything is ready for Act II"))
                })
            else
                MultiAsk(factions./{ h =>
                    YYSelectObjectsAction(h, (factions./~(f => f.fates.intersect(f.past)) ++ factions./~(f => f.fates.diff(f.past)).distinct).diff(factions./~(_.fates.single)))
                        .withGroup("Select Fate")
                        .withRule(x => options.has(NoFate).not || x.act == game.act || game.act == 3)
                        .withThens(x => l./(f => ChooseFateAction(f, x, SetupChooseFatesAction).as(f, "plays", x).!(f.fates.has(x).not)))
                        .ask
                })

        case StartSetupAction =>
            factions.foreach { f =>
                f.regent = true
            }

            game.laws :+= ImperialControlCommand
            game.laws :+= ImperialMovement
            game.laws :+= ImperialTruce

            SetupEmpireAction

        case SetupEmpireAction =>
            Random[$[Int]]($($(1, 2), $(2, 3), $(3, 4), $(4, 5), $(5, 6), $(6, 1)), EmpireClustersRandomAction(_))

        case EmpireClustersRandomAction(l) =>
            systems.foreach { s =>
                if (s.cluster.in(l)) {
                    Empire.reserve --> Ship --> s
                }
                else {
                    val u = Blights.reserve --> Blight
                    u --> s
                    Blights.damaged :+= u
                }
            }

            Empire.log("controlled clusters", l(0).hlb, "and", l(1).hlb)

            Blights.log("spread through the rest of the map")

            Random[Symbol]($(Arrow, Crescent, Hex), FreeCitiesRandomAction(_))

        case FreeCitiesRandomAction(symbol) =>
            systems.foreach { s =>
                if (s.symbol == symbol && Empire.at(s).none) {
                    val u = Free.reserve --> City

                    u --> s

                    log(u, "remained in", s)
                }
            }

            Random[Resource]($(Weapon, Fuel, Relic), GovernEdictRandomAction(_))

        case GovernEdictRandomAction(r) =>
            game.edicts :+= r @@ {
                case Weapon => PolicyOfWar
                case Fuel => PolicyOfEscalation
                case Relic => PolicyOfPeace
            }

            log("Initial policy was", game.edicts)

            game.sidedeck --> ImperialCouncilInSession --> game.council

            ShuffleCourtDiscardAction(ReplenishMarketAction(AddLoreToCourtAction))

        case AddLoreToCourtAction =>
            ShuffleTake[Lore](Lores.done.notOf[UnofficialLore], factions.num, LoreToCourtShuffledAction(_))

        case LoreToCourtShuffledAction(l) =>
            game.allLores --> l --> game.court

            if (options.has(DebugInterface))
                log("Added lores", l.intersperse(Comma), "to the court", "<<<", "DEBUG".styled(xstyles.error))
            else
                log("Added lores to the court")

            ShuffleCourtDiscardAction(FactionsSetupAction)

        case FactionsSetupAction if options.has(HostTest) && game.act == 1 =>
            val winners = factions.%(_.power >= 0)

            GameOver(winners, "Game Over", winners./~(f => $(GameOverWonAction(null, f))))

        case FactionsSetupAction =>
            DealFatesAction(FatesChosenAction)

        case DealFatesAction(then) =>
            game.act += 1

            ShuffleUntil[Fate](Map(1 -> Fates.act1, 2 -> Fates.act2, 3 -> Fates.act3)(game.act), _.grouped(2).forall(_.first.in(Fates.ready)), FatesShuffledAction(_, then))

        case FatesShuffledAction(l, then) =>
            factions.zp(l.take(factions.num * 2).grouped(2).$).foreach { (f, fates) =>
                if (f.fates.none /*|| (options.has(NoFate) && game.act == 2)*/)
                    f.fates = fates
                else
                    f.fates ++= fates.take(1 + options.has(NoFate).??(1))
            }

            log("Fates were shuffled and dealt")

            ChooseFatesAction(then)

        case ChooseFatesAction(then) =>
            val l = factions.%(_.fates.num > 1)

            implicit val convert = (x : Fate) => x.img

            if (l.none)
                Milestone(then)
            else
                MultiAsk(l./(f =>
                    YYSelectObjectsAction(f, f.fates)
                        .withGroup(f, "chooses Fate")
                        // .withRule(x => options.has(NoFate).not || x.act == game.act || game.act == 3)
                        .withThen(x => ChooseFateAction(f, x, ChooseFatesAction(then)))(x => "Play " ~ x.elem)("~~~")
                        .ask
                ))

        case ChooseFateAction(f, x, then) =>
            f.fates = $(x)

            // f.fates = f @@ {
            //     case Blue => $(Believer) // $(Partisan)
            //     case White => $(Advocate) // $(Admiral)
            //     case Red => $(Magnate) // $(Steward)
            //     case Yellow => $(Caretaker) // $(Founder)
            // }

            if (f.past.has(x).not)
                f.past :+= x

            then

        case FatesChosenAction =>
            factions.foreach { f =>
                f.log("chose", f.fates)

                if (f.fates.only.act == game.act) {
                    f.favors.some./ { l =>
                        l.foreach { u =>
                            u --> u.faction.reserve
                        }

                        f.log("returned favors", l.intersperse(Comma))
                    }
                }
            }

            if (game.act == 1) {
                FatesSetupAction(factions)
            }
            else {
                val report = IntermissionReport(game.act, systems./(s => s -> s.$), game.court.$, game.deck.$.sortBy(d => (d.suit.sortKey, d.strength)), game.laws, game.edicts, factions./(f => f ->
                    IntermissionPlayerReport(f.fates.only, f.past, f.power, f.regent, f.primus, f.abilities, f.lores.$.of[Lore], f.loyal.$.of[GuildCard], f.spendable.content, f.outraged, f.favors.$)
                ))

                log(DoubleLine)
                log(report)
                log(DoubleLine)

                factions.foreach { f =>
                    f.objective = None
                }

                implicit val convert = (x : Fate) => x.img

                MultiAsk(factions./(f =>
                    YYSelectObjectsAction(f, f.fates)
                        .withGroup("Choose Fate")
                        .withRule(_ => false)
                        .withExtras(
                            Info("Thank you for playing.".&.hl ~ " " ~ ("Act " + game.act.times("I").join("") + " is currently under development.").&.hl),
                            OnClickInfo(report)("Intermission Report".hl.styled(styles.title)),
                            // DeadlockAction(f, NoMessage,
                            //     ArcsBlightedReachGameOverAction(hrf.HRF.version, game.seating, game.options, factions.maxBy(_.power), false, 3, factions./(f => f -> f.power).to(ListMap), factions./(f => f -> f.past).to(ListMap))
                            // ),
                            NewActSetupAction.as("Get on with it!".hlb)(" ").!(MetaBR.development.not),
                            HiddenOkAction,
                        )
                        .ask
                ))
            }

        case FatesSetupAction(Nil) if game.act == 1 =>
            Then(BuildingsShipsSetupAction)

        case FatesSetupAction(Nil) if game.act >= 2 =>
            factions.foreach(_.recalculateSlots())

            factions.foreach(_.adjust = true)

            Then(AdjustResourcesAction(StartChapterAction))

        case FatesSetupAction(f :: rest) =>
            Then(FateSetupAction(f, FatesSetupAction(rest)))

        case FateSetupAction(f, then) =>
            val fate = f.fates.only

            f.log("setup", fate)

            if (fate.act == game.act) {
                game.courtiers.register(FateDeck(fate), content = fate.expansion.deck)

                game.expansions = fate.expansion +: game.expansions
            }

            val next = FateInitAction(f, fate, game.act, then)

            if (f.flagship.any && fate.act == 3)
                Then(MayResettleAction(f, next))
            else
                Then(next)

        case FateInitAction(f, fate, act, then) =>
            log(fate, "not initialized")

            then

        case BuildingsShipsSetupAction =>
            val next = factions.%(f => systems./(f.at(_).buildings.num).sum == 0).starting ||
               factions.reverse.%(f => systems./(f.at(_).buildings.num).sum == 1).starting

            next./{ f =>
                val prefix = f.short + "-"

                Ask(f)
                    .some(systems.%(Empire.at(_).any).%(game.freeSlots(_) > 0)) { s =>
                        PlaceCityAndShipsAction(f, s).as(City.of(f), Image(prefix + "city", styles.qbuilding), "and ships")("Place in", s, game.resources(s)./(_.token)) ::
                        PlaceStarportAndShipsAction(f, s).as(Starport.of(f), Image(prefix + "starport", styles.qbuilding), "and ships")("Place in", s, game.resources(s)./(_.token))
                    }
            }.|(Milestone(FillFreeCitiesSetupAction))

        case PlaceCityAndShipsAction(f, s) =>
            val u = f.reserve --> City.of(f)
            u --> s

            val s1 = f.reserve --> Ship.of(f)
            s1 --> s

            val s2 = f.reserve --> Ship.of(f)
            s2 --> s

            val s3 = f.reserve --> Ship.of(f)
            s3 --> s

            f.recalculateSlots()

            f.log("placed", u, "and", s1, Comma, s2, Comma, s3, "in", s)

            val r = game.resources(s).only

            f.gain(r)

            f.log("gained", r.token)

            BuildingsShipsSetupAction

        case PlaceStarportAndShipsAction(f, s) =>
            val u = f.reserve --> Starport.of(f)
            u --> s

            val s1 = f.reserve --> Ship.of(f)
            s1 --> s

            val s2 = f.reserve --> Ship.of(f)
            s2 --> s

            val s3 = f.reserve --> Ship.of(f)
            s3 --> s

            f.log("placed", u, "and", s1, Comma, s2, Comma, s3, "in", s)

            val r = game.resources(s).only

            f.gain(r)

            f.log("gained", r.token)

            BuildingsShipsSetupAction

        case FillFreeCitiesSetupAction =>
            factions.foreach { f =>
                f.adjust = false
            }

            if (factions.%(_.primus).none) {
                factions.first.primus = true
                factions.first.recalculateSlots()

                factions.first.log("became the", FirstRegent)
            }

            systems.%(Empire.at(_).any).foreach { s =>
                game.freeSlots(s).timesDo { () =>
                    val u = Free.reserve --> City.of(Free)

                    u --> s

                    Free.log("placed", u, "in", s)
                }
            }

            StartChapterAction

        case NewActSetupAction =>
            if (game.council.has(ImperialCouncilDecided)) {
                game.council --> ImperialCouncilDecided --> game.sidedeck

                game.sidedeck --> ImperialCouncilInSession --> game.council

                game.council.$.dropLast --> game.council

                log("Council went in session")
            }

            game.current = factions.%(_.primus).single

            ShuffleCourtDiscardAction(ReplenishMarketAction(SpawnMoreBlightAction))

        case SpawnMoreBlightAction =>
            Random[Symbol]($(Arrow, Crescent, Hex), s => SpawnMoreBlightRolledAction(s))

        case SpawnMoreBlightRolledAction(symbol) =>
            log("Rolled for", Blights, symbol.name.hh, symbol.smb.hl)

            systems.foreach { s =>
                if (s.gate) {
                    if (Blights.at(s).none && s.$.shiplikes.none) {
                        val u = Blights.reserve.first
                        u --> s
                        Blights.damaged :+= u
                        Blights.log("appeared in", s)
                    }
                }
            }

            systems.foreach { s =>
                if (s.symbol == symbol) {
                    if (Blights.at(s).none) {
                        val u = Blights.reserve.first
                        u --> s
                        Blights.damaged :+= u
                        Blights.log("appeared in", s)
                    }
                    else {
                        systems.foreach { p =>
                            if (p.gate.not && p.cluster == s.cluster) {
                                if (Blights.at(p).none) {
                                    val u = Blights.reserve.first
                                    u --> p
                                    Blights.damaged :+= u
                                    Blights.log("appeared in", p)
                                }
                            }
                        }
                    }
                }
            }

            FatesSetupAction(factions)

        // LORES
        case GainCourtCardAction(f, v : Lore, lane, main, then) =>
            Market(lane) --> v --> f.lores

            f.log("gained", v)

            f.recalculateSlots()

            then

        // HARM
        case TryHarmAction(f, e, s, reserved, then) =>
            Then(then)

        case HarmAction(f, e, s, then) =>
            Then(then)

        // VOX CARDS
        case GainCourtCardAction(f, ImperialCouncilInSession, lane, main, then) =>
            game.council --> ImperialCouncilInSession --> game.sidedeck

            game.sidedeck --> ImperialCouncilDecided --> game.council

            game.council.$.dropLast --> game.council

            game.decided = |(f)

            Then(then)

        case SpreadAgentsAction(f, lane, then) =>
            if (Influence(lane).$.ofc(f).any)
                Ask(f).group(CouncilIntrigue, "moves", Influence(lane).$.ofc(f).num.times(game.showFigure(Figure(f, Agent, 0), 0)))
                    .each(game.market)(m => SpreadAgentAction(f, lane, m.index, then).as(m.$).!(m.index == lane).!(m.has(ImperialCouncilDecided)))
                    .done(then)
            else
                Then(then)

        case SpreadAgentAction(f, lane, n, then) =>
            val u = Influence(lane).$.ofc(f).first

            u --> Influence(n)

            log(u, "moved to", Market(n).$)

            SpreadAgentsAction(f, lane, then)

        // COURT
        case CheckCourtScrapAction(then) =>
            var l = (game.act == 3).??(game.expansions.has(HegemonExpansion).??(systems.forall(_.$.piece(Banner).none).??(game.market./~(m => m.%(_.as[VoxCard]./(_.effect).has(AgainstHegemony))))))

            if (l.any) {
                l --> CourtScrap

                log(l.intersperse(Comma), (l.num > 1).?("were").|("was"), "scrapped")

                ReplenishMarketAction(then)
            }
            else
                Then(then)

        // EVENT
        case ResolveEventAction(f) =>
            log(DoubleLine)

            log("Resolving", EventCard(0))

            Ask(f).group("Event Card".hh)
                .add(StartSummitAction(f, RollForEventAction(f)).as("Start Summit".hlb))
                .skip(RollForEventAction(f))

        // COUNCIL
        case ResolveCouncilAction(f) =>
            log(DoubleLine)

            log("Resolving", ImperialCouncilDecided)

            Ask(f).group(ImperialCouncilDecided)
                .add(StartSummitAction(f, CouncilBonusAction(f)).as("Start Summit".hlb))
                .skip(CouncilBonusAction(f))

        case CouncilBonusAction(f) =>
            if (f.regent.not) {
                if (factions.%(_.regent).any)
                    Then(CouncilStealMainAction(f, factions.%(_.primus).only, $, ChooseEventAction(f)))
                else
                    Then(ChooseEventAction(f))
            }
            else {
                if (f.primus.not) {
                    factions.foreach { e =>
                        e.primus = false
                        e.recalculateSlots()
                    }

                    f.primus = true
                    f.recalculateSlots()

                    f.log("became the", FirstRegent)
                }

                Then(ChooseEventAction(f))
            }

        case CouncilStealMainAction(f, e, l, then) =>
            if (f.displayable.exists(_.none) && ImperialTrust.any) {
                Ask(f).group("Steal".hl, "from", ImperialTrust)
                    .each(ImperialTrust.$) { x =>
                        StealResourceAction(f, e, x, ImperialTrust, CouncilStealMainAction(f, e, l ++ x.as[ResourceToken], then)).as(ResourceLock(x, None))
                            .!(e.hasGuild(SwornGuardians), SwornGuardians.name)
                            .!(game.declared.contains(Keeper) && e.hasLore(KeepersTrust) && x.as[ResourceToken]./(_.resource).?(f.hasCountableResource), "Keeper's Trust")
                    }
                    .bailout(CouncilStealLoseAction(f, e, l, then).as("Can't Steal"))
                    .needOk
            }
            else
                Then(CouncilStealLoseAction(f, e, l, then))

        case CouncilStealLoseAction(f, e, l, then) =>
            if (l.num > 0) {
                e.power -= l.num

                e.log("lost", l.num.power, "from stealing")
            }

            AdjustResourcesAction(then)

        case ChooseEventAction(f) =>
            Ask(f).group("Choose to resolve")
                .add(ChooseCrisesAction(f).as("Crises".styled(Blights).hlb))
                .add(ChooseEdictsAction(f).as("Edicts".styled(Empire).hlb))

        case ChooseCrisesAction(f) =>
            log(DoubleLine)

            f.log("chose", "Crises".styled(Blights)(styles.title))

            f.log("rolled", "Event Dice".styled(styles.titleW))

            EventRollAction(f, $(false), $(1, 2, 3, 4, 5, 6), $(Arrow, Crescent, Hex), $)

        case ChooseEdictsAction(f) =>
            log(DoubleLine)

            f.log("chose", "Edicts".styled(Empire)(styles.title))

            ResolveEdictsAction(ContinueRoundsAction)

        case RollForEventAction(f) =>
            log(DoubleLine)

            f.log("rolled", "Event Dice".styled(styles.titleW))

            EventRollAction(f, $(false, true), $(1, 2, 3, 4, 5, 6), $(Arrow, Crescent, Hex), $)

        case EventRollAction(f, edicts, clusters, symbols, used) =>
            val n = (clusters.num > 1).??(1) + (symbols.num > 1).??(1)

            if (used.any) {
                if (n == 2)
                    f.log("rerolled", 2.hl, "event dice")

                if (n == 1)
                    f.log("rerolled", 1.hl, "event die")
            }

            Random3[Boolean, Int, Symbol](edicts, clusters, symbols, (e, i, s) => EventRolledAction(f, e, i, s, used))

        case EventRolledAction(f, edict, cluster, symbol, used) if game.declared.contains(Empath) && f.hasLore(EmpathsVision) && used.has(EmpathsVision).not =>
            f.log("rolled", edict.?("Edicts".styled(Empire)(styles.title)).|("Crises".styled(Blights)(styles.title)), System(cluster, symbol))

            if (game.decided.has(Blights)) {
                Ask(f).group("Event Roll", edict.?("Edicts".styled(Empire)(styles.title)).|("Crises".styled(Blights)(styles.title)), System(cluster, symbol), HorizontalBreak, EmpathsVision)
                    .add(EventRollAction(f, $(false, true), $(1, 2, 3, 4, 5, 6), $(Arrow, Crescent, Hex), used :+ EmpathsVision).as("Reroll", "Outcome".hh, Comma, "Symbol".hh, "and", "Cluster".hh))
                    .add(EventRollAction(f, $(false, true), $(cluster), $(Arrow, Crescent, Hex), used :+ EmpathsVision).as("Reroll", "Outcome".hh, "and", "Symbol".hh))
                    .when(edict.not)(EventRollAction(f, $(edict), $(1, 2, 3, 4, 5, 6), $(symbol), used :+ EmpathsVision).as("Reroll", "Cluster".hh))
                    .add(EventProcessAction(f, edict, cluster, symbol).as("Skip"))
            }
            else
            if (edict.not) {
                Ask(f).group("Crises Roll", System(cluster, symbol), HorizontalBreak, "Reroll with", EmpathsVision)
                    .add(EventRollAction(f, $(edict), $(1, 2, 3, 4, 5, 6), $(Arrow, Crescent, Hex), used :+ EmpathsVision).as("Reroll", "Symbol".hh, "and", "Cluster".hh))
                    .add(EventRollAction(f, $(edict), $(cluster), $(Arrow, Crescent, Hex), used :+ EmpathsVision).as("Reroll", "Symbol".hh))
                    .add(EventRollAction(f, $(edict), $(1, 2, 3, 4, 5, 6), $(symbol), used :+ EmpathsVision).as("Reroll", "Cluster".hh))
                    .add(EventProcessAction(f, edict, cluster, symbol).as("Skip"))
            }
            else
                Then(EventProcessAction(f, edict, cluster, symbol))

        case EventRolledAction(f, edict, cluster, symbol, _) =>
            EventProcessAction(f, edict, cluster, symbol)

        case EventProcessAction(_, edict, cluster, symbol) =>
            if (edict)
                ResolveEdictsAction(ContinueRoundsAction)
            else
                ResolveCrisesAction(cluster, symbol, ContinueRoundsAction)

        case ResolveCrisesAction(cluster, symbol, then) =>
            log("Outcome", "Crises".styled(Blights)(styles.title), System(cluster, symbol))

            Then(BlightCrisesGlobalAction(FateCrisesAction(cluster, symbol, then)))

        case BlightCrisesGlobalAction(then) =>
            if (factions.%(f => f.hasLore(BlightSociety) && f.hasSpendableResource(Psionic)).any)
                BlightCrisesMainAction(systems, $, $, then)
            else
                BlightCrisesPerformAction(systems, $, then)

        case BlightCrisesMainAction(l, exempt, abstained, then) =>
            val ll = l.%(s => Blights.at(s).any).%!(s => game.laws.has(ImperialProtectors) && Empire.at(s).fresh.ships.any).%!(s => exempt.exists(f => f.at(s).shiplikes.any))
            val ff = factions.%(f => f.hasLore(BlightSociety) && f.hasSpendableResource(Psionic)).diff(exempt).diff(abstained).%(f => ll.exists(s => f.at(s).shiplikes.any))

            if (ff.any) {
                val f = ff.first

                Ask(f).group("Spend", Psionic, "to prevent", "Blight Crises".styled(Blights)(styles.title), "in", ll.%(s => f.at(s).shiplikes.any))
                    .each(f.spendable.resources.%<(_.is(Psionic)))((r, k) => BlightCrisesAvertAction(f, PayResource(r, k), BlightCrisesMainAction(l.%!(f.present), exempt :+ f, abstained, then)).as(r -> k))
                    .skip(BlightCrisesMainAction(l, exempt, abstained :+ f, then))
            }
            else
                Then(BlightCrisesPerformAction(l, exempt, then))

        case BlightCrisesAvertAction(f, x, then) =>
            f.pay(x)

            f.log("spoke to", BlightSociety, x)

            then

        case BlightCrisesPerformAction(l, exempt, then) =>
            log(SingleLine)

            log("Blight Crises".styled(Blights)(styles.title))

            val dead = game.laws.has(TheDeadLive).?(TheDeadLive)
            val hunger = factions.%(_.hasLore(BlightHunger)).any.?(BlightHunger)

            l.foreach { s =>
                val damage = Blights.at(s).use(l => l.fresh.num * 2 + l.num)

                if (damage > 0) {
                    if (game.laws.has(ImperialProtectors) && Empire.at(s).fresh.ships.any) {
                        log(ImperialProtectors, "averted crisis", "in", s)
                    }
                    else
                    if (exempt.exists(_.at(s).shiplikes.any)) {
                        exempt.%(_.at(s).shiplikes.any).first.log("averted crisis", "in", s)
                    }
                    else {
                        val empire = Empire.at(s).use(l => l.fresh ++ l)

                        if (empire.any) {
                            val incoming = empire.take(damage)

                            var result = Empire.damaged ++ incoming

                            val destroyed = result.diff(result.distinct)
                            val damaged = incoming.diff(destroyed).diff(destroyed)

                            result = result.diff(destroyed).diff(destroyed)

                            destroyed --> (hunger || dead | Empire.reserve)

                            if (destroyed.any)
                                Blights.log("destroyed", destroyed, "in", s)

                            if (damaged.any)
                                Blights.log("damaged", damaged, "in", s)

                            Empire.damaged = result
                        }

                        if (damage > empire.num) {
                            factions.foreach { f =>
                                val units = f.at(s)
                                val ships = units.ships
                                val targets = ships.fresh ++ units.flagship.any.??(Flagship.scheme(f).reverse./~(_.$)./~(u => u.fresh.?($(u, u)).|($(u)))) ++ ships

                                val incoming = targets.take(damage - empire.num)

                                val result = f.damaged ++ incoming

                                val destroyed = result.diff(result.distinct)
                                val damaged = incoming.distinct.diff(destroyed)

                                destroyed.foreach { u =>
                                    u.piece @@ {
                                        case Ship => u --> (hunger || dead | f.reserve)
                                        case _ => u --> (hunger | f.reserve)
                                    }
                                }

                                if (destroyed.cities.any)
                                    f.recalculateSlots()

                                if (destroyed.any)
                                    Blights.log("destroyed", destroyed.intersperse(Comma), "in", s)

                                if (damaged.any)
                                    Blights.log("damaged", damaged.intersperse(Comma), "in", s)

                                f.damaged = result.distinct.diff(destroyed)
                            }
                        }

                        factions./ { f =>
                            if (f.objective.has(KeepThePeopleSafe))
                                if (f.at(s).bunkers.any)
                                    f.advance(1, $("protecting people"))
                        }
                    }
                }
            }

            Then(AdjustResourcesAction(then))

        case FateCrisesAction(cluster, symbol, then) =>
            log("Fate Crises".styled(Blights)(styles.title))

            Then(factions.foldLeft(CourtCrisesAction(cluster, symbol, then) : ForcedAction)((q, f) => FateCrisesFactionAction(f, cluster, symbol, $, q)))

        case FateCrisesFactionAction(f, cluster, symbol, done, then) =>
            val l = f.lores.$.of[FateCrisis] ++ f.loyal.$./~(_.as[GuildCard]./~(_.effect.as[FateCrisis]))

            if (l.diff(done).any)
                Ask(f).group("Resolve", "Crisis".styled(Blights)(styles.title))
                    .each(l.diff(done))(c => FateCrisisAction(f, c, cluster, symbol, FateCrisesFactionAction(f, cluster, symbol, done :+ c, then)).as(c))
            else
                Then(then)

        case CourtCrisesAction(cluster, symbol, then) =>
            log("Court Crises".styled(Blights)(styles.title), "", System(cluster, symbol))

            CourtCrisesContinueAction(cluster, symbol, 1, 0, then)

        case CourtCrisesContinueAction(cluster, symbol, lane, skip, then) if game.market.has(Market(lane)).not =>
            Then(then)

        case CourtCrisesContinueAction(cluster, symbol, lane, skip, then) =>
            if (Market(lane).none)
                Then(ReplenishMarketAction(CourtCrisesContinueAction(cluster, symbol, lane + 1, 0, then)))
            else
                Market(lane).reverse.drop(skip).take(2) @@ {
                    case Nil => Force(CourtCrisesContinueAction(cluster, symbol, lane + 1, 0, then))
                    case (v : VoxCard) :: rest => Then(CourtCrisesPerformAction(cluster, symbol, lane, skip, rest.none, v, then))
                    case _ => Force(CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then))
                }

        case BuryCrisisVoxCardAction(v, lane, then) =>
            (v : CourtCard) --> game.court

            log(v, "was buried")

            if (Market(lane).none) {
                game.court.starting.$ --> Market(lane)

                log(Market(lane).$, "was drawn instead")

                ReturnAgentsCourtCardAction(lane, then)
            }
            else
                then

        case UnderTopCrisisVoxCardAction(v, lane, then) =>
            game.court.take(1) ++ $(v) ++ game.court.drop(1) --> game.court

            log(v, "was placed under the top card")

            if (Market(lane).none) {
                game.court.starting.$ --> Market(lane)

                log(Market(lane).$, "was drawn instead")

                ReturnAgentsCourtCardAction(lane, then)
            }
            else
                then

        case DiscardCrisisVoxCardAction(v, lane, then) =>
            (v : CourtCard) --> game.discourt

            log(v, "was discarded")

            if (Market(lane).none) {
                game.court.starting.$ --> Market(lane)

                log(Market(lane).$, "was drawn instead")

                ReturnAgentsCourtCardAction(lane, then)
            }
            else
                then

        // UNIMPLEMENTED CRISES
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v, then) =>
            log("TODO PERFORM CRISES ON", v)

            CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

        // EDICTS
        case ResolveEdictsAction(then) =>
            log("Executed", "Edicts".styled(Empire)(styles.title))

            Then(ResolveNextEdictAction("", then))

        case ResolveNextEdictAction(index, then) =>
            val next = game.edicts.%(_.priority > index).some./(_.minBy(_.priority))

            if (next.any) {
                log("Executed", next)

                Then(ResolveEdictAction(next.get.priority, then))
            }
            else
                Then(then)

        case ResolveEdictAction(priority, then) if $(PolicyOfPeace, PolicyOfEscalation, PolicyOfWar).exists(_.priority == priority) =>
            val primus = factions.%(_.primus).single

            val policies = $(PolicyOfPeace, PolicyOfEscalation, PolicyOfWar)

            if (primus.any) {
                val f = primus.get

                implicit val convert = (x : Edict) => x.img

                YYSelectObjectsAction(f, policies)
                    .withGroup("Govern the Imperial Reach".styled(styles.titleW))
                    .withThen(p =>
                        if (p.priority == priority)
                            EnforcePolicyAction(f, priority, then).as("Enforce", p).!(p == PolicyOfWar && f.hasLore(OathOfPeace))
                        else
                            SwitchPolicyAction(f, priority, p.priority, then).as("Switch to", p).!(p == PolicyOfWar && f.hasLore(OathOfPeace)))("~~~")
            }
            else
                ResolveNextEdictAction(priority, then)

        case SwitchPolicyAction(f, priority, neu, then) =>
            val policies = $(PolicyOfPeace, PolicyOfEscalation, PolicyOfWar)

            game.edicts :-= policies.%(_.priority == priority).only
            game.edicts :+= policies.%(_.priority == neu).only

            game.edicts = game.edicts.sortBy(_.priority)

            f.log("switched to", policies.%(_.priority == neu).only)

            ResolveNextEdictAction(neu, then)

        case EnforcePolicyAction(f, priority, then) if priority == PolicyOfPeace.priority =>
            f.log("enforced", PolicyOfPeace)

            CollectDemandAction(f, $(Relic, Psionic), factions.%(_.regent), PeaceBonusAction(f, priority, then))

        case EnforcePolicyAction(f, priority, then) if priority == PolicyOfEscalation.priority =>
            f.log("enforced", PolicyOfEscalation)

            CollectDemandAction(f, $(Material, Fuel, Weapon), factions.%(_.regent), EscalationBonusAction(f, priority, then))

        case EnforcePolicyAction(f, priority, then) if priority == PolicyOfWar.priority =>
            f.log("enforced", PolicyOfWar)

            CollectTrophyDemandAction(f, factions.%(_.regent).but(f), WarBonusAction(f, priority, then))

        case PeaceBonusAction(f, priority, then) =>
            factions.%(_.regent).foreach { e =>
                val bonus = (e.pooled(City) < 2).??(2) + (e.pooled(City) < 1).??(3)

                if (bonus > 0) {
                    e.power += bonus

                    e.log("scored city bonus", bonus.power, "with", PolicyOfPeace)
                }
            }

            ResolveNextEdictAction(priority, then)

        case EscalationBonusAction(f, priority, then) =>
            val l1 = systems.%(s => Empire.at(s).ships.fresh.any)
            val l2 = systems.%(s => f.at(s).starports.any)

            val l = l1.some.|(l2)

            val next = ResolveNextEdictAction(priority, then)

            if (l.any && Empire.reserve.$.ships.any)
                Ask(f).group("Place", 2.hlb, Ship.sof(Empire), "with", PolicyOfEscalation)
                    .each(l)(s => EmpireShipsAction(f, $(s, s), next).as(s))
            else {
                if (Empire.reserve.$.ships.any)
                    Empire.log("had no more ships in supply")
                else
                if (l.none)
                    f.log("could not place", Ship.sof(Empire))

                Then(next)
            }

        case WarBonusAction(f, priority, then) =>
            val next = ResolveNextEdictAction(priority, then)

            val n = Supply(Weapon).num + ImperialTrust.%(_.is(Weapon)).num

            if (n == 0) {
                log("There were no", "Weapons".styled(Weapon), "in supply")

                Then(next)
            }
            else {
                val l = factions.%(_.regent)

                Ask(f).group("Distribute", "Weapons".styled(Weapon), "with", PolicyOfWar)
                    .each(l.combinations(n.upTo(l.num)).$)(l => DistributeWeaponsAction(f, l, next).as(l.commaAnd))
                    .needOk
            }

        case DistributeWeaponsAction(f, l, then) =>
            l.foreach { e =>
                if (Supply(Weapon).any) {
                    e.gain(Weapon)

                    e.log("obtained", ResourceLock(ResourceToken(Weapon, 0), None), "with", PolicyOfWar)
                }
                else {
                    ImperialTrust.%(_.is(Weapon)).first --> Supply(Weapon)

                    e.gain(Weapon)

                    e.log("obtained", ResourceLock(ResourceToken(Weapon, 0), None), "with", PolicyOfWar, "from", ImperialTrust)
                }
            }

            AdjustResourcesAction(then)

        case EmpireShipsAction(f, ss, then) =>
            ss.distinct.foreach { s =>
                val l = Empire.reserve.$.ships.take(ss.count(s))

                l --> s

                f.log("placed", l.comma, "in", s)

                if (l.any) {
                    factions.%(_.objective.has(ProveYourself)).foreach { f =>
                        if (game.declared.keys.exists(a => game.declared(a).any && f.ambitionValue(a) > f.rivals./(_.ambitionValue(a)).max))
                            f.advance(l.num, $("for imperial ships"))
                    }
                }
            }

            then

        case CollectDemandAction(f, resources, l, then) if l.none =>
            AdjustResourcesAction(then)

        case CollectDemandAction(f, resources, l, then) =>
            Ask(f).group("Collect Demand")
                .some(l) { e =>
                    val next = CollectDemandAction(f, resources, l.but(e), then)

                    val slots = e.spendable.resources.%<(r => resources.exists(r.is))

                    if (slots.any)
                        slots./((r, k) => CollectResourceAction(f, e, r, k, next).as("Collect", r -> k, "from", e)(e))
                    else
                    if (e == f)
                        $(next.as("Forgive", e)(e))
                    else
                    if (e.pool(Agent))
                        $(CollectFavorAction(f, e, next).as("Collect", "Favor".styled(e), "from", e)(e))
                    else
                        $(CollectExpelAction(f, e, next).as("Expel", e, "from", Empire)(e), next.as("Forgive", e)(e))
                }
                .needOk

        case CollectTrophyDemandAction(f, l, then) if l.none =>
            AdjustResourcesAction(then)

        case CollectTrophyDemandAction(f, l, then) =>
            Ask(f).group("Collect Demand")
                .some(l) { e =>
                    val next = CollectTrophyDemandAction(f, l.but(e), then)

                    val trophies = e.trophies.%(u => u.faction.regent.not && u.faction != Empire)
                    val captives = e.captives.%(u => u.faction.regent.not && u.faction != Empire)

                    if (trophies.any || captives.any)
                        trophies./(u => CollectTrophyAction(f, e, u, next).as("Collect trophy", u)(e)) ++
                        captives./(u => CollectCaptiveAction(f, e, u, next).as("Collect captive", u)(e))
                    else
                    if (e == f)
                        $(next.as("Forgive", e)(e))
                    else
                    if (e.pool(Agent))
                        $(CollectFavorAction(f, e, next).as("Collect", "Favor".styled(e), "from", e)(e))
                    else
                        $(CollectExpelAction(f, e, next).as("Expel", e, "from", Empire)(e), next.as("Forgive", e)(e))
                }
                .needOk

        case CollectResourceAction(f, e, r, k, then) =>
            r --> ImperialTrust

            f.log("collected", r -> k, "from", e)

            f.taken :+= r

            then

        case CollectTrophyAction(f, e, u, then) =>
            u --> f.trophies

            f.log("collected trophy", u, "from", e)

            then

        case CollectCaptiveAction(f, e, u, then) =>
            u --> f.captives

            f.log("collected captive", u, "from", e)

            then

        case CollectFavorAction(f, e, then) =>
            e.reserve --> Agent --> f.favors

            f.log("collected", "Favor".styled(e), "from", e)

            then

        case CollectExpelAction(f, e, then) =>
            f.log("expelled", e, "from", Empire)

            BecomeOutlawAction(e, then)

        // REGENT / OUTLAW
        case BecomeOutlawAction(f, then) =>
            f.regent = false

            f.log("became an", "Outlaw".styled(Free))

            f.lores.foreach {
                case c @ ImperialAuthority =>
                    c --> game.court

                    f.log("buried", c)

                case _ =>
            }

            f.loyal.foreach {
                case c @ GuildCard(_, ImperialOfficers) =>
                    (c : CourtCard) --> game.court

                    f.log("buried", c)

                case c @ GuildCard(_, RogueAdmirals) =>
                    (c : CourtCard) --> game.court

                    f.log("buried", c)

                case c @ GuildCard(_, TaxCollectors) =>
                    (c : CourtCard) --> game.court

                    f.log("buried", c)

                case _ =>
            }

            if (f.primus) {
                f.primus = false
                f.recalculateSlots()

                val l = factions.%(_.regent)

                if (l.any)
                    Ask(f).group("First Regent".hlb)
                        .each(l.%(_.power == l./(_.power).max))(e => FirstRegentAction(e, then).as(e))
                else {
                    log(Empire, "dissolved")

                    val l = ImperialTrust.$.of[ResourceToken]

                    if (l.any) {
                        l.foreach { r =>
                            r --> r.supply
                        }

                        f.power -= l.num

                        f.log("returned", l.intersperse(Comma))
                        f.log("lost", l.num.power, "dissolving", ImperialTrust)
                    }

                    then
                }
            }
            else
                then

        case BecomeRegentAction(f, then) =>
            f.regent = true

            if (factions.but(f).%(_.regent).none) {
                f.primus = true
                f.recalculateSlots()

                f.log("became the", "First Regent".styled(Empire)(styles.title))
            }
            else
                f.log("became an", "Regent".styled(Empire))

            then

        // FLAGSHIP
        case SetupFlagshipMainAction(f, l, then) =>
            Ask(f).group("Place", Flagship.of(f), "in")
                .each(systems.%(f.present).some.|(systems.%(_.gate)))(s => SetupFlagshipAction(f, s, l, then).as(s))

        case SetupFlagshipAction(f, s, l, then) =>
            systems.foreach { s =>
                f.at(s).buildings.foreach { b =>
                    val damaged = b.damaged

                    f.log("replaced", b, "with", SomePieceOf(Free, b.piece, damaged), "in", s)

                    b --> f.reserve

                    val n = Free.reserve --> b.piece

                    if (damaged) {
                        f.damaged :-= b

                        Free.damaged :+= n
                    }

                    n --> s
                }
            }

            if (game.laws.has(FlagshipUpgradesAidA).not) {
                game.laws :+= FlagshipUpgradesAidA
                game.laws :+= FlagshipUpgradesAidB
            }

            f.flagship = |(f.reserve --> Flagship.of(f))

            f.flagship.foreach(_ --> s)

            f.log("placed", Flagship.of(f), "in", s)

            Flagship.scheme(f).foreach { q =>
                game.figures.register(q)
            }

            Flagship.functions(f).zp(l).foreach { (q, p) =>
                p.foreach { p =>
                    f.reserve --> p.of(f) --> q

                    f.log("upgraded", q, "with", p.of(f))
                }
            }

            f.recalculateSlots()

            Then(then)

        case SlipstreamDriveFlagshipMainAction(f, s, u, then) =>
            MoveFromAction(f, s, $(u), true, NoCost, |(Slipstream), CancelAction, then)

        case SlipstreamDriveOtherMainAction(f, s, u, then) =>
            MoveFromAction(f, s, MovementExpansion.movable(f, s, f.regent && f.officers, f.hasLore(BlightFury) || f.hasLore(BlightSociety)).but(u), true, NoCost, |(Slipstream), CancelAction, then)

        case BuildFlagshipAction(f, x, q, p, then) =>
            val s = f.flagship.get.system

            f.pay(x)

            val u = f.reserve --> p

            if (f.rivals.exists(_.rules(s)) || campaign.&&(f.regent.not && Empire.rules(s)))
                f.damaged :+= u

            u --> q

            f.log("upgraded", q, "with", u, x)

            if (p == City)
                f.recalculateSlots()

            then

        case RepairFlagshipAction(f, x, q, u, then) =>
            f.pay(x)

            u.faction.damaged :-= u

            f.log("repaired", u, "as", q, x)

            then

        case MayResettleAction(f, then) =>
            Ask(f).group(Flagship.of(f))
                .add(ResettleAction(f, then).as("Resettle"))
                .skip(then)

        case ResettleAction(f, then) =>
            val s = f.flagship.get.system

            Flagship.scheme(f)./~(_.$) --> f.reserve

            if (f.pool(Ship)) {
                f.reserve --> Ship --> s

                f.flagship.get --> f.reserve

                f.flagship = None

                if (game.laws.has(FlagshipUpgradesAidA) && factions.%(_.flagship.any).none) {
                    game.laws :-= FlagshipUpgradesAidA
                    game.laws :-= FlagshipUpgradesAidB
                }

                f.log("resettled", Flagship.of(f), "in", s)

                if (f.abilities.has(PlantingBanners)) {
                    f.abilities :-= PlantingBanners

                    f.log("lost", PlantingBanners)
                }
            }
            else
                f.log("had no", Ship.of(f), "to resettle")

            ResettleBuildingsMainAction(f, $(City, Starport).%(f.pool), then)

        case ResettleBuildingsMainAction(f, Nil, then) =>
            ReplenishShipsLoyalMainAction(f, 8, then)

        case ResettleBuildingsMainAction(f, bb, then) =>
            val l = systems.%(s => game.freeSlots(s) > 0).some.|(systems.%(s => Free.at(s).buildings.any))

            Ask(f)
                .some(l) { s =>
                    if (game.freeSlots(s) > 0)
                        bb./(b => ResettleBuildingsAction(f, b, s, ResettleBuildingsMainAction(f, bb :- b, then)).as(b.of(f), game.showFigure(Figure(f, b, 0)))("Place in", s))
                    else
                        bb./~(b => Free.at(s).buildings./(u => ResettleBuildingsReplaceAction(f, b, s, u, ResettleBuildingsMainAction(f, bb :- b, then)).as(b.of(f), game.showFigure(Figure(f, b, 0)), "in place of", u, game.showFigure(u))("Place in", s)))
                }
                .bailw(ReplenishShipsLoyalMainAction(f, 8, then)) {
                     f.log("could not completely resettle")
                }

        case ResettleBuildingsAction(f, p, s, then) =>
            f.reserve --> p --> s

            f.log("placed", p.of(f), "in", s)

            then

        case ResettleBuildingsReplaceAction(f, p, s, u, then) =>
            f.log("placed", p.of(f), "in", s, "in place of", u)

            u --> u.faction.reserve

            val n = f.reserve --> p

            n --> s

            if (u.damaged)
                u.faction.damaged :-= u

            if (game.unslotted.has(u)) {
                game.unslotted :-= u
                game.unslotted :+= n
            }

            then

        // REPLENISH
        case ReplenishShipsMainAction(f, n, then) =>
            if (systems./(f.at(_).ships.num).sum < n && f.pool(Ship))
                Ask(f).group("Replenish to", n.hlb, Ship.sof(f))
                    .each(systems)(s => ShipsInSystemsAction(f, $(s), ReplenishShipsMainAction(f, n, then)).as(s))
            else
                Then(then)

        case ReplenishShipsLoyalMainAction(f, n, then) =>
            if (systems./(f.at(_).ships.num).sum < n && f.pool(Ship))
                Ask(f).group("Replenish to", n.hlb, Ship.sof(f))
                    .each(systems.%(f.present))(s => ShipsInSystemsAction(f, $(s), ReplenishShipsLoyalMainAction(f, n, then)).as(s))
            else
                Then(then)

        case ScrapShipsMainAction(f, 0, then) =>
            Then(then)

        case ScrapShipsMainAction(f, n, then) =>
            val l = f.reserve.$.ships.take(n)

            Ask(f).group(f.fates, "scraps", n.hlb, "ship".s(n))
                .when(l.any)(ScrapShipsAction(f, None, l, ScrapShipsMainAction(f, n - l.num, then)).as(l./(game.showFigure), "from supply"))
                .some(systems) { s =>
                    f.at(s).ships./(u => ScrapShipsAction(f, |(s), $(u), ScrapShipsMainAction(f, n - 1, then)).as(u, game.showFigure(u), "in", s))
                }
                .needOk

        case ScrapShipsAction(f, s, l, then) =>
            f.log("scrapped", l.intersperse(Comma), s./("from" -> _).|("from supply"))

            l.foreach { u =>
                f.damaged :-= u

                u --> Scrap
            }

            then

        // INTERMISSION
        case IntermissionAction =>
            log(DoubleLine)
            log(SingleLine)
            log(DoubleLine)

            log("End of", ("Act " ~ game.act.times("I".txt)).styled(styles.titleW))

            game.current = None

            factions.reverse.foldLeft(IntermissionClearCourtAction : ForcedAction)((q, f) => FateResolveAction(f, q))

        case FateResolveAction(f, then) =>
            val fate = f.fates.only

            log(DottedLine)

            if (f.progress > 0) {
                f.log("failed as", fate, "to", f.objective, "by", f.progress.hlb)

                f.objective = None

                f.failed ++= f.fates

                f.fates = $

                f.power -= f.progress

                if (options.has(HostTest))
                    f.power -= f.progress * 99

                f.log("lost", f.progress.power)

                Then(FateFailAction(f, fate, game.act, then))
            }
            else {
                f.log("completed", f.objective, "as", fate)

                f.objective = None

                Then(FateDoneAction(f, fate, game.act, then))
            }

        case FateSetupInitAction(f, fate, act, then) =>
            FateInitAction(f, fate, act, then)

        case FateSetupFailAction(f, fate, act, then) =>
            FateFailAction(f, fate, act, then)

        case FateSetupDoneAction(f, fate, act, then) =>
            FateDoneAction(f, fate, act, then)

        case IntermissionClearCourtAction =>
            log(DottedLine)

            game.market.foreach { m =>
                val agents = Influence(m.index).$

                if (agents.any) {
                    agents.foreach { u =>
                        u --> u.faction.reserve
                    }

                    log("Returned agents from", m.first)
                }

                val l = m.$.notOf[ImperialCard]

                if (l.any) {
                    l --> game.court

                    l.foreach { c =>
                        log("Returned", c, "to the court deck")
                    }
                }
            }

            CourtDiscard.$.some.foreach { l =>
                l --> CourtScrap

                log("Scrapped", l.intersperse(Comma))
            }

            Then(IntermissionClearPiecesAction)

        case IntermissionClearPiecesAction =>
            factions.foreach { f =>
                f.captives.$.some.foreach { l =>
                    l.foreach { u =>
                        u --> u.faction.reserve
                    }

                    f.log("returned captives", l.comma)
                }
            }

            factions.foreach { f =>
                f.trophies.$.some.foreach { l =>
                    l.foreach { u =>
                        u --> u.faction.reserve

                        if (u.piece == City)
                            if (u.faction.pooled(City) > 2)
                                u.faction.as[Faction].foreach { f =>
                                    f.adjust = true
                                }
                    }

                    f.log("returned trophies", l.comma)
                }
            }

            $(ImperialTrust, MerchantLeagueSlots, ArsenalKeepersSlots, GreenVaultSlots).%(game.resources.has).foreach { s =>
                val l = $(s).resources.content

                if (l.any) {
                    log(s, "emptied")

                    l.foreach(r => r --> r.resource.supply)
                }
            }

            factions.foreach(_.recalculateSlots())

            Then(IntermissionRepairDestroyAction)

        case IntermissionRepairDestroyAction =>
            log(DottedLine)

            systems.foreach { s =>
                Blights.at(s).damaged.some./ { l =>
                    log("Repaired", l.comma, "in", s)

                    Blights.damaged = Blights.damaged.diff(l)
                }
            }

            systems.foreach { s =>
                (factions ++ $(Empire, Free)).foreach { f =>
                    f.at(s).damaged.some./ { l =>
                        log("Destroyed", l.comma, "in", s)

                        f.damaged = f.damaged.diff(l)

                        l.foreach { u =>
                            u --> u.faction.reserve
                        }
                    }
                }
            }

            factions.foreach { f =>
                f.flagship.foreach { flagship =>
                    Flagship.scheme(f).reverse./~(_.$)./~(u => u.damaged.?(u)).some./ { l =>
                        log("Destroyed", l.comma, "on", flagship)

                        f.damaged = f.damaged.diff(l)

                        l.foreach { u =>
                            u --> u.faction.reserve
                        }
                    }
                }
            }

            systems.foreach { s =>
                if (Blights.present(s))
                    if ((factions ++ $(Empire, Free)).exists(_.at(s).ships.any).not)
                        (factions ++ $(Empire, Free)).foreach { f =>
                            val l = f.at(s).buildinglikes

                            if (l.any) {
                                Blights.log("destroyed defenseless", l.comma, "in", s)

                                f.damaged = f.damaged.diff(l)

                                l.foreach { u =>
                                    u --> u.faction.reserve

                                    game.onRemoveFigure(u)
                                }
                            }
                        }
            }

            factions.reverse.foldLeft(IntermissionInitiativePowerAction : ForcedAction)((q, f) => CheckNoFleetAction(f, q))

        case IntermissionInitiativePowerAction =>
            val f = factions.%(f => f.power >= factions.but(f)./(_.power).max).first

            if (factions.starting.has(f).not) {
                game.factions = factions.dropWhile(_ != f) ++ factions.takeWhile(_ != f)

                f.log("got the initiative with the most power")
            }
            else
                f.log("held the initiative with the most power")

            factions.foreach { f =>
                if (f.power > 1) {
                    f.log("lost", (f.power /↓ 2).power)

                    f.power = f.power /↑ 2
                }
            }

            IntermissionNextActAction

        case IntermissionNextActAction =>
            game.chapter = 0

            Then(FactionsSetupAction)

        // DEBUG
        case DebugGainResourceAction(f, r, then) =>
            if (r.supply.none)
                PreludeHold(r).$ --> r.supply

            f.gain(r, $("<<<", "DEBUG".styled(xstyles.error)))

            if (f.overflow.none)
                f.adjust = false

            AdjustResourcesAction(then)

        case DebugFailAction(f, then) =>
            // f.progress = 999

            f.hand --> game.deck

            f.log("discarded hand", "<<<", "DEBUG".styled(xstyles.error))

            then

        case DebugSucceedAction(f, then) =>
            f.progress = 0

            f.hand --> game.deck

            f.log("completed objective", "<<<", "DEBUG".styled(xstyles.error))

            then

        case DebugRedealCourtAction(f, then) =>
            game.market.drop(1).foreach { m =>
                m.first --> game.court

                game.court.first --> m

                m.$.dropLast --> m
            }

            f.log("redealt the court", "<<<", "DEBUG".styled(xstyles.error))

            then

        case DebugPipsAction(f, then) =>
            f.log("gained 20 pips", "<<<", "DEBUG".styled(xstyles.error))

            then

        case DebugCrisesAction(f, then) =>
            f.log("launched Crises", "<<<", "DEBUG".styled(xstyles.error))

            Ask(f).group("Crises").each(systems.%!(_.gate))(s => BlightCrisesMainAction(systems, $, $, FateCrisesAction(s.cluster, s.symbol, then)).as(s))

        case DebugEdictsAction(f, then) =>
            f.log("launched Edicts", "<<<", "DEBUG".styled(xstyles.error))

            ResolveEdictsAction(then)

        case DebugSummitAction(f, then) =>
            f.log("launched Summit", "<<<", "DEBUG".styled(xstyles.error))

            StartSummitAction(f, then)

        case DebugUnlockAction(f, then) =>
            f.log("unlocked all secured cards", "<<<", "DEBUG".styled(xstyles.error))

            f.secured = $

            then

        case DebugSuitAction(f, suit, then) =>
            f.log("changed leading suit to", suit, "<<<", "DEBUG".styled(xstyles.error))

            factions.first.displayed = factions.first.displayed.of[ActionCard]./(_.copy(suit = suit))

            then


        // ENDGAME
        case CheckWinAction =>
            if (game.act == 3)
                Milestone(CartelCleanUpAction(BlightCheckWinAction))
            else
            if (game.chapter == 3)
                Milestone(CartelCleanUpAction(IntermissionAction))
            else
                Milestone(CartelCleanUpAction(CleanUpChapterAction(StartChapterAction)))

        case BlightCheckWinAction =>
            val cfates = factions.%(_.progress <= 0).%(_.fates.only.act == 3).%(_.power > 0)
            val pretenders = cfates.some.|((game.chapter == 4).??(factions))
            val winner = pretenders.%(_.power == pretenders./(_.power).max).starting

            if (winner.any)
                Milestone(ArcsBlightedReachGameOverAction(hrf.HRF.version, game.seating, game.options, winner.get, cfates.any, game.chapter + 6, factions./(f => f -> f.power).to(ListMap), factions./(f => f -> f.past).to(ListMap)))
            else
                Milestone(CleanUpChapterAction(StartChapterAction))

        case ArcsBlightedReachGameOverAction(_, _, _, winner, _, _, _, _) =>
            val winners = $(winner)

            game.seized = |(winner)
            game.current = |(winner)
            game.isOver = true

            winners.foreach(f => f.log("won"))

            GameOver(winners, "Game Over", winners./~(f => $(GameOverWonAction(null, f))))


        // ...
        case _ => UnknownContinue
    }
}
