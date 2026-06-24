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


trait Color extends NamedToString with Styling with Elementary with Record {
    def id = name
    def short = name.take(1)
    def style = short.toLowerCase
    override def elem : Elem = name.styled(this).styled(styles.condensed)
}

trait Faction extends BasePlayer with Color

case object Red extends Faction
case object White extends Faction
case object Blue extends Faction
case object Yellow extends Faction


trait Resource extends NamedToString with Styling with Record {
    override def elem = name.styled(this)
    val supply = Supply(this)
    lazy val token = elem ~ Image(this.id, styles.token)
    val order : Int
}

case object Material extends Resource { val order = 100 }
case object Fuel extends Resource { val order = 200 }
case object Weapon extends Resource { val order = 300 }
case object Relic extends Resource { val order = 400 }
case object Psionic extends Resource { val order = 500 }

case object Nothingness extends ResourceLike {
    val id = "nothingness"
    def is(r : Resource) = false
    def isResource = false
    def isGolem = false
    def elem = ""
    def order : Int = 9999
    def token = Empty
}

object Resources {
    def all = $[Resource](Material, Fuel, Weapon, Relic, Psionic)
}

trait ResourceLike extends Elementary with PairElementary with Styling with Record {
    def id : String
    def is(r : Resource) : Boolean
    def isResource : Boolean
    def isGolem : Boolean
    def pairElem(b : Any) = b match {
        case b : ResourceSlot => |(ResourceLikeInSlot(this, b))
        case _ => None
    }
    def order : Int
    def token : Elem
}

case class ResourceToken(resource : Resource, index : Int) extends ResourceLike {
    def id = resource.id
    def elem = resource.elem
    def is(r : Resource) = resource == r
    def isResource = true
    def isGolem = false
    val supply = Supply(resource)
    def elem(implicit game : Game) = resource.elem ~ Image(resource.id, styles.token)
    def order : Int = resource.order + index
    def token = resource.token
}

trait ResourceSlot extends Record {
    def capacity(f : Faction)(implicit game : Game) : Int = this.num
    def canHold(t : ResourceLike)(implicit game : Game) : Boolean = true
    def canHold(r : Resource)(implicit game : Game) : Boolean = canHold(ResourceToken(r, 0))
    def canHoldMore(implicit game : Game) = true
    val raidable : |[Int] = None
    val stealable : Boolean = false
    val spendable : Boolean = false
    val tradeable : Boolean = false
}

object ResourceSlot {
    implicit class ResourceSlotsEx(l : $[ResourceSlot])(implicit game : Game) {
        def desc = l./~(s => s.$./(_ -> s))
        def content : $[ResourceLike] = l./~(_.$)
        def resources = desc.resources
        def golems = desc.golems
    }

    implicit class ResourceLikeSlotsEx(l : $[(ResourceLike, ResourceSlot)])(implicit game : Game) {
        def content : $[ResourceLike] = l.lefts
        def resources : $[(ResourceToken, ResourceSlot)] = l./~((r, s) => r.as[ResourceToken]./(_ -> s))
        def golems : $[(GolemToken, ResourceSlot)] = l./~((r, s) => r.as[GolemToken]./(_ -> s))
        def ofr(r : Resource) = resources.%<(_.resource == r)
    }

    implicit class ResourceTokenSlotsEx(l : $[(ResourceToken, ResourceSlot)])(implicit game : Game) {
        def content : $[ResourceToken] = l.lefts
        def ofr(r : Resource) = l.%<(_.resource == r)
    }
}

case object NoSlot extends ResourceSlot

case class CityResourceSlot(faction : Faction, index : Int, keys : Int) extends ResourceSlot {
    override def capacity(f : Faction)(implicit game : Game) = 1
    override def canHoldMore(implicit game : Game) = this.$.none
    override val raidable = |(keys)
    override val tradeable = true
    override val spendable = true
    override val stealable = true
}

case class Overflow(faction : Faction) extends ResourceSlot

case object AncientHoldingsSlot extends ResourceSlot {
    override def capacity(f : Faction)(implicit game : Game) = 1
    override def canHoldMore(implicit game : Game) = this.$.none
    override val raidable = |(4)
    override val tradeable = true
    override val spendable = true
    override val stealable = true
}

case class Supply(resource : Resource) extends ResourceSlot
case class PreludeHold(resource : Resource) extends ResourceSlot
case object PlanetResourcesOverrides extends ResourceSlot


trait BattleResult extends Record
case object OwnDamage extends BattleResult
case object Intercept extends BattleResult
case object HitShip extends BattleResult
case object HitBuilding extends BattleResult
case object RaidKey extends BattleResult

trait BattleDie extends Record {
    val die : CustomDie[$[BattleResult]]
    val ownApprox : $[$[BattleResult]]
    val enemyApprox : $[$[BattleResult]]
}

case object Skirmish extends BattleDie {
    val die = Die.from($, $, $, $(HitShip), $(HitShip), $(HitShip))
    val ownApprox = $($, $(HitShip), $, $(HitShip), $, $(HitShip))
    val enemyApprox = ownApprox.reverse
}
case object Assault extends BattleDie {
    val die = Die.from($, $(OwnDamage, HitShip), $(Intercept, HitShip), $(OwnDamage, HitShip, HitShip), $(HitShip, HitShip), $(OwnDamage, HitShip))
    val ownApprox = $($(OwnDamage, HitShip), $(Intercept, HitShip), $, $(OwnDamage, HitShip), $(OwnDamage, HitShip, HitShip), $(HitShip, HitShip))
    val enemyApprox = ownApprox.reverse
}
case object Raid extends BattleDie {
    val die = Die.from($(Intercept, RaidKey, RaidKey), $(OwnDamage, RaidKey), $(Intercept), $(OwnDamage, HitBuilding), $(HitBuilding, RaidKey), $(OwnDamage, HitBuilding))
    val ownApprox = $($(OwnDamage, HitBuilding), $(Intercept, RaidKey), $(OwnDamage, HitBuilding, RaidKey), $(HitBuilding, RaidKey), $(OwnDamage), $(Intercept, RaidKey))
    val enemyApprox = ownApprox.reverse
}


trait AmbitionBoxContent
case class AmbitionMarker(high : Int, low : Int) extends AmbitionBoxContent with Record
case class Conspired(faction : Faction) extends AmbitionBoxContent
case class Revealed(faction : Faction) extends AmbitionBoxContent


trait Piece extends Record {
    def name = toString
    def plural = name + "s"
    def of(f : Color) = SomePieceOf(f, this, false)
    def of(f : Color, damaged : Boolean) = SomePieceOf(f, this, damaged)
    def sof(f : Color) : Elem = (f.name + " " + plural).styled(f)
}

trait PieceOf {
    def faction : Color
    def piece : Piece
}


trait Building extends Piece
case object Slot extends Building
case object City extends Building
case object Starport extends Building
case object Ship extends Piece
case object Agent extends Piece

case class SomePieceOf(faction : Color, piece : Piece, damaged : Boolean) extends PieceOf with Elementary {
    def elem = (damaged.??("Damaged ") + faction.as[Faction].?(_.name + " ") + faction.is[Empire.type].??("Imperial ") + faction.is[Free.type].??("Free ") + piece.name).styled(faction)
}

case class Figure(faction : Color, piece : Piece, index : Int) extends GameElementary {
    override def toString = "" + faction + "/" + piece + "/" + index
    def fresh(implicit game : Game) = faction.damaged.has(this).not
    def damaged(implicit game : Game) = faction.damaged.has(this)
    def elem(implicit game : Game) = (damaged.??("Damaged ") + game.unslotted.has(this).??("Cloud ") + faction.as[Faction].?(_.name + " ") + faction.is[Empire.type].??("Imperial ") + faction.is[Free.type].??("Free ") + piece.name).styled(faction) ~
        game.options.has(DebugInterface).?((" (" + toString + ")").spn(xstyles.smaller50))
    def region(implicit tracker : IdentityTracker[Region, Figure]) = tracker.find(this)
    def system(implicit tracker : IdentityTracker[Region, Figure]) = tracker.find(this)./~(_.as[System]).|!("figure " + this + " is not in any system but in " + tracker.find(this))
}


trait Ambition extends NamedToString with Elementary with Record {
    override def elem = this.toString.styled(styles.titleW)
    val strength : Int
}

case object Tycoon extends Ambition { val strength = 2 }
case object Tyrant extends Ambition { val strength = 3 }
case object Warlord extends Ambition { val strength = 4 }
case object Keeper extends Ambition { val strength = 5 }
case object Empath extends Ambition { val strength = 6 }

trait Suit extends NamedToString with Styling with Record {
    val sortKey : Int
    val sub : $[Suit] = $(this)
}

case object Administration extends Suit { val sortKey = 2 }
case object Aggression extends Suit { val sortKey = 4 }
case object Construction extends Suit { val sortKey = 1 }
case object Mobilization extends Suit { val sortKey = 3 }

case object Faithful extends Suit {
    val sortKey = 5
    override val sub : $[Suit] = $(Faithful, Zeal, Wisdom)
}
case object Zeal extends Suit { val sortKey = 6 }
case object Wisdom extends Suit { val sortKey = 7 }

case object Event extends Suit { val sortKey = 999 }

trait StandardAction extends Record
case object Tax extends StandardAction
case object Build extends StandardAction
case object Move extends StandardAction
case object Repair extends StandardAction
case object Influence extends StandardAction
case object Secure extends StandardAction
case object Battle extends StandardAction

trait DeckCardLocation
case class Hand(faction : Faction) extends DeckCardLocation
case class Played(faction : Faction) extends DeckCardLocation
case class Blind(faction : Faction) extends DeckCardLocation
case object Deck extends DeckCardLocation


trait DeckCard extends Elementary with Record {
    def suit : Suit
    def id : String
    def img = Image(id, styles.card)
    def strength : Int
    def pips : Int
}

case class ActionCard(suit : Suit, strength : Int, pips : Int) extends DeckCard {
    def id = suit.id + "-" + strength
    def name = suit.name + " " + strength
    def elem = (" " + suit + " " + strength + " ").pre.spn(styles.outlined).styled(suit)
    def zeroed(b : Boolean) = (" " + suit + " " + b.?(0).|(strength) + " ").pre.spn(styles.outlined).styled(suit)
}

case class EventCard(index : Int) extends DeckCard {
    def suit = Event
    def id = "event"
    def name = "Event"
    def elem = (" " + suit + " ").pre.spn(styles.outlined)
    def strength = 0
    def pips = 0
}

object DeckCards {
    def deck = $(
        ActionCard(Administration, 1, 4),
        ActionCard(Administration, 2, 4),
        ActionCard(Administration, 3, 3),
        ActionCard(Administration, 4, 3),
        ActionCard(Administration, 5, 3),
        ActionCard(Administration, 6, 2),
        ActionCard(Administration, 7, 1),
        ActionCard(Aggression, 1, 3),
        ActionCard(Aggression, 2, 3),
        ActionCard(Aggression, 3, 2),
        ActionCard(Aggression, 4, 2),
        ActionCard(Aggression, 5, 2),
        ActionCard(Aggression, 6, 2),
        ActionCard(Aggression, 7, 1),
        ActionCard(Construction, 1, 4),
        ActionCard(Construction, 2, 4),
        ActionCard(Construction, 3, 3),
        ActionCard(Construction, 4, 3),
        ActionCard(Construction, 5, 2),
        ActionCard(Construction, 6, 2),
        ActionCard(Construction, 7, 1),
        ActionCard(Mobilization, 1, 4),
        ActionCard(Mobilization, 2, 4),
        ActionCard(Mobilization, 3, 3),
        ActionCard(Mobilization, 4, 3),
        ActionCard(Mobilization, 5, 2),
        ActionCard(Mobilization, 6, 2),
        ActionCard(Mobilization, 7, 1),
    )
}


trait CourtLocation
case object CourtDeck extends CourtLocation
case object SideDeck extends CourtLocation
case class Market(index : Int) extends CourtLocation
case object CourtDiscard extends CourtLocation
case object CourtScrap extends CourtLocation
case class DiscardAfterRound(faction : Faction) extends CourtLocation
case class ReclaimAfterRound(faction : Faction) extends CourtLocation
case class Loyal(faction : Faction) extends CourtLocation
case class FateDeck(fate : Fate) extends CourtLocation
case class NoFateDeck(faction : Faction) extends CourtLocation
case class LoreCards(faction : Faction) extends CourtLocation
case object LoreDeck extends CourtLocation
case object DraftLores extends CourtLocation
case object UnusedLores extends CourtLocation


trait Effect extends Record

trait CourtCard extends Record with Elementary {
    def id : String
    def name : String
    def elem = name.styled(styles.titleW)
    def img = Image(id, styles.card)
}

case class GuildCard(id : String, effect : GuildEffect) extends CourtCard {
    def name = effect.name
    def suit = effect.suit
    def keys = effect.keys
}

case class VoxCard(id : String, effect : VoxEffect) extends CourtCard {
    def name = effect.name
}

abstract class GuildEffect(val name : String, val suit : Resource, val keys : Int) extends Effect with Elementary {
    def elem = name.styled(styles.titleW)
}

abstract class VoxEffect(val name : String) extends Effect with Elementary {
    def elem = name.styled(styles.titleW)
}

case class CardId(id : String)


trait LoyalGuild { self : GuildEffect => }



trait Cost extends GameElementary with Record {
    def elemLog(implicit game : Game) : Elem = elem
}

case object Pip extends Cost {
    def elem(implicit game : Game) = "with card action"
}

case object NoCost extends Cost {
    def elem(implicit game : Game) = Empty
}

case object AlreadyPaid extends Cost {
    def elem(implicit game : Game) = Empty
}


case class PayResource(resource : ResourceToken, slot : ResourceSlot) extends Cost {
    def elem(implicit game : Game) = "with " ~ ResourceLikeInSlot(resource, slot).elem
}

case class ReleaseCaptive(u : Figure) extends Cost {
    def elem(implicit game : Game) = "with " ~ u.elem
}

case class ReleaseTrophy(u : Figure) extends Cost {
    def elem(implicit game : Game) = "with " ~ u.elem
}

case class MultiCost(l : $[Cost]) extends Cost {
    def elem(implicit game : Game) = l./(_.elem).commaAnd.join(" ")
    override def elemLog(implicit game : Game) = "with " ~ elem
}

object MultiCost {
    def apply(l : Cost*) : MultiCost = MultiCost(l.$.but(NoCost))
}

case class ResourceRef(resource : Resource, lock : |[Int]) extends Elementary {
    def elem = resource.elem ~ Image(resource.name, styles.token) ~ lock./(n => Image("keys-" + n, styles.token))
}

case class ResourceLock(resource : ResourceLike, lock : |[Int]) extends Elementary {
    def elem = resource.elem ~ Image(resource.id, styles.token) ~ lock./(n => Image("keys-" + n, styles.token))
}

case class ResourceLikeInSlot(resource : ResourceLike, slot : ResourceSlot) extends Elementary {
    def elem = resource.elem ~ Image(resource.id, styles.token) ~ (slot @@ {
        case NoSlot => Empty
        case CityResourceSlot(_, _, keys) => Image("keys-" + keys, styles.token)
        case AncientHoldingsSlot => Image("keys-" + 4, styles.token)
        case MerchantLeagueSlots => " from " ~ MerchantLeague.elem
        case GolemHearthSlots => " from " ~ GolemHearth.elem ~ Image("keys-" + 2 + "-golem", styles.token)
        case PirateHoardSlots => " from " ~ PirateHoard.elem ~ Image("keys-" + 2 + "-hoard", styles.token)
        case WellOfEmpathySlots => " from " ~ WellOfEmpathy.elem ~ Image("keys-" + 2 + "-empathy", styles.token)
        case ArsenalKeepersSlots => " from " ~ ArsenalKeepers.elem ~ Image("keys-" + 2 + "-arsenal", styles.token)
        case GreenVaultSlots => " from " ~ GreenVault.elem
        case ImperialTrust => " from " ~ ImperialTrust.elem
        case _ => slot.toString ~ " // TODO DESCRIBE SLOT"
    })
}

case class ResourceInSlot(resource : ResourceToken, slot : ResourceSlot) extends Elementary {
    def elem = ResourceLikeInSlot(resource, slot).elem
}

trait Region {
    def -->(p : Piece)(implicit tracker : IdentityTracker[Region, Figure]) = tracker.get(this).%(_.piece == p).take(1).single.|!(p.name + " not found in " + this)
    def -->(p : PieceOf)(implicit tracker : IdentityTracker[Region, Figure]) = tracker.get(this).%(u => u.piece == p.piece && u.faction == p.faction).take(1).single.|!(p.piece.name + " of " + p.faction.name + " not found in " + this)
}

trait SpecialRegion extends Region
case class Reserve(f : Color) extends SpecialRegion
case class Outrage(f : Faction) extends SpecialRegion
case class Trophies(f : Faction) extends SpecialRegion
case class Captives(f : Faction) extends SpecialRegion
case class Favors(f : Faction) extends SpecialRegion
case object Scrap extends SpecialRegion
case class Influence(n : Int) extends SpecialRegion
case class FatePieces(fate : Fate) extends SpecialRegion
case class Exchange(f : Color) extends SpecialRegion


trait Symbol extends NamedToString with Record {
    def smb = (this @@ {
        case Gate => 0x2727.toChar.toString
        case Arrow => 0x2B9D.toChar.toString
        case Crescent => 0x263E.toChar.toString
        case Hex => 0x2B22.toChar.toString
    })
}
case object Gate extends Symbol
case object Arrow extends Symbol
case object Crescent extends Symbol
case object Hex extends Symbol

case class System(cluster : Int, symbol : Symbol) extends Region with Elementary with Record {
    def name = (cluster == 7).?("Twisted Passage").|(symbol.name + " " + cluster)
    def gate = symbol == Gate
    def elem = (cluster == 7).?(name.styled(styles.cluster).hlb).|(symbol.name.hh ~ " " ~ cluster.styled(styles.cluster).hlb ~ symbol.smb.hl)
    def unstyledElem = (cluster == 7).?(name).|(name ~ symbol.smb)
    def darkElem = (cluster == 7).?(name).|(name ~ symbol.smb)
}


trait Board {
    val name : String
    def systems : $[System]

    def starting : $[(System, System, $[System])]

    def connected(c : System) : $[System]

    lazy val distance = systems./(a => a -> systems./(b => b -> {
        var n = 0
        var l = $(a)
        while (!l.contains(b)) {
            n += 1
            l = l./~(connected)
        }
        n
    }).toMap).toMap
}

trait BaseBoard extends Board with Record {
    val clusters : $[Int]

    def nextCluster(g : Int) : Int = {
        var n = ((g + 1) - 1) % 6 + 1
        if (n.in(clusters).not)
            nextCluster(n)
        else
            n
    }

    def prevCluster(g : Int) : Int = {
        var n = ((g - 1) + 5) % 6 + 1
        if (n.in(clusters).not)
            prevCluster(n)
        else
            n
    }

    def slots(s : System): Int = s @@ {
        case System(_, Gate) => 0
        case System(1, Arrow) => 2
        case System(1, Crescent) => 1
        case System(1, Hex) => 2
        case System(2, Arrow) => 1
        case System(2, Crescent) => 1
        case System(2, Hex) => 2
        case System(3, Arrow) => 1
        case System(3, Crescent) => 1
        case System(3, Hex) => 2
        case System(4, Arrow) => 2
        case System(4, Crescent) => 2
        case System(4, Hex) => 1
        case System(5, Arrow) => 1
        case System(5, Crescent) => 1
        case System(5, Hex) => 2
        case System(6, Arrow) => 1
        case System(6, Crescent) => 2
        case System(6, Hex) => 1
    }

    def resource(s : System) : Resource = s @@ {
        case System(1, Arrow) => Weapon
        case System(1, Crescent) => Fuel
        case System(1, Hex) => Material
        case System(2, Arrow) => Psionic
        case System(2, Crescent) => Weapon
        case System(2, Hex) => Relic
        case System(3, Arrow) => Material
        case System(3, Crescent) => Fuel
        case System(3, Hex) => Weapon
        case System(4, Arrow) => Relic
        case System(4, Crescent) => Fuel
        case System(4, Hex) => Material
        case System(5, Arrow) => Weapon
        case System(5, Crescent) => Relic
        case System(5, Hex) => Psionic
        case System(6, Arrow) => Material
        case System(6, Crescent) => Fuel
        case System(6, Hex) => Psionic
    }

    lazy val systems : $[System] = clusters./~(i => $(System(i, Gate), System(i, Arrow), System(i, Crescent), System(i, Hex)))

    def connected(s : System) = s @@ {
        case System(i, Gate) => $(System(nextCluster(i), Gate), System(prevCluster(i), Gate), System(i, Arrow), System(i, Crescent), System(i, Hex))
        case System(i, Crescent) => $(System(i, Gate), System(i, Arrow), System(i, Hex))
        case System(i, Arrow) => $(System(i, Gate), System(i, Crescent)) ++ (i == 6 && clusters.has(5)).$(System(5, Hex)) ++ (i == 3 && clusters.has(2)).$(System(2, Hex))
        case System(i, Hex) => $(System(i, Gate), System(i, Crescent)) ++ (i == 5 && clusters.has(6)).$(System(6, Arrow)) ++ (i == 2 && clusters.has(3)).$(System(3, Arrow))
    }
}

object Piece {
    implicit class PieceCast(val p : Piece) {
        def building = p match {
            case b : Building => |(b)
            case _ => None
        }
    }
}

//[[ PINKER
object Figure {
    implicit class FiguresEx(val l : $[Figure]) extends AnyVal {
        def ofc(f : Color) = l.%(_.faction == f)
        def piece(p : Piece) = l.%(_.piece == p)
        def one(p : Piece) = l.%(_.piece == p).head
        def colors = l./(_.faction).distinct
        def count(p : Piece) = l.%(u => u.piece == p).num
        def hasA(p : Piece) = l.exists(u => u.piece == p)
        def hasBuilding = l.exists(u => u.piece.is[Building])
        def sublist(s : $[Piece]) = {
            var rest = s
            l./~{ u =>
                if (rest.contains(u.piece)) {
                    rest = rest :- (u.piece)
                    |(u)
                }
                else
                    None
            }
        }
        def sub(n : Int, p : Piece) = sublist(n.times(p))

        def buildings = l.%(u => u.piece == City || u.piece == Starport)
        def buildinglikes = l.%(u => u.piece == City || u.piece == Starport || u.piece == Banner || u.piece == Bunker)
        def ships = l.%(u => u.piece == Ship)
        def shiplikes = l.%(u => u.piece == Ship || u.piece == Flagship)
        def flagships = l.%(u => u.piece == Flagship)
        def flagship = l.%(u => u.piece == Flagship).single
        def cities = l.%(u => u.piece == City)
        def starports = l.%(u => u.piece == Starport)
        def banners = l.%(u => u.piece == Banner)
        def bunkers = l.%(u => u.piece == Bunker)
        def rumors = l.%(u => u.piece.is[Rumor])
        def blights = l.%(u => u.piece == Blight)
        def agents = l.%(u => u.piece == Agent)

        def fresh(implicit game : Game) = l.%(u => u.faction.damaged.has(u).not)
        def damaged(implicit game : Game) = l.%(u => u.faction.damaged.has(u))

        def comma : $[Any] = l./~(e => $(Comma, e)).drop(1)
    }

    implicit class FiguresExEx[T](val t : T)(implicit val conv : T => $[Figure]) {
        def l = conv(t)
        def ofc(f : Color) = l.%(_.faction == f)
        def piece(p : Piece) = l.%(_.piece == p)
        def one(p : Piece) = l.%(_.piece == p).head
        def colors = l./(_.faction).distinct
        def count(p : Piece) = l.%(u => u.piece == p).num
        def hasA(p : Piece) = l.exists(u => u.piece == p)
        def hasBuilding = l.exists(u => u.piece.is[Building])
        def sublist(s : $[Piece]) = {
            var rest = s
            l./~{ u =>
                if (rest.contains(u.piece)) {
                    rest = rest :- (u.piece)
                    |(u)
                }
                else
                    None
            }
        }
        def sub(n : Int, p : Piece) = sublist(n.times(p))

        def buildings = l.%(u => u.piece == City || u.piece == Starport)
        def buildinglikes = l.%(u => u.piece == City || u.piece == Starport || u.piece == Banner)
        def ships = l.%(u => u.piece == Ship)
        def shiplikes = l.%(u => u.piece == Ship || u.piece == Flagship)
        def flagships = l.%(u => u.piece == Flagship)
        def flagship = l.%(u => u.piece == Flagship).single
        def cities = l.%(u => u.piece == City)
        def starports = l.%(u => u.piece == Starport)
        def banners = l.%(u => u.piece == Banner)
        def bunkers = l.%(u => u.piece == Bunker)
        def blights = l.%(u => u.piece == Blight)
        def agents = l.%(u => u.piece == Agent)

        def fresh(implicit game : Game) = l.%(u => u.faction.damaged.has(u).not)
        def damaged(implicit game : Game) = l.%(u => u.faction.damaged.has(u))

        def comma : $[Any] = l./~(e => $(Comma, e)).drop(1)
    }

}
//]]


trait ViewCard extends ViewObject[DeckCard] { self : UserAction =>
    def d : DeckCard
    def obj = d
}

trait ViewLeader extends ViewObject[Leader] { self : UserAction =>
    def l : Leader
    def obj = l
}

trait ViewLore extends ViewObject[Lore] { self : UserAction =>
    def l : Lore
    def obj = l
}

trait ViewSetup extends ViewObject[String] { self : UserAction =>
    def s : String
    def obj = s
}


trait Key

/*
trait BuildKey extends Key {
    val piece : Piece
    val color : Color
    val system : System
}
*/

trait SoftKeys

trait ThenDesc { self : ForcedAction =>
    def desc : Elem
}


trait GameImplicits {
    type Rolled = $[$[BattleResult]]

    implicit def factionToState(f : Faction)(implicit game : Game) : FactionState = game.states(f).as[FactionState].get
    implicit def blightsToState(f : Blights.type)(implicit game : Game) : BlightsState = game.states(f).as[BlightsState].get
    implicit def empireToState(f : Empire.type)(implicit game : Game) : EmpireState = game.states(f).as[EmpireState].get
    implicit def freeToState(f : Free.type)(implicit game : Game) : FreeState = game.states(f).as[FreeState].get
    implicit def neutralsToState(f : Neutrals.type)(implicit game : Game) : NeutralsState = game.states(f).as[NeutralsState].get
    implicit def colorToState(f : Color)(implicit game : Game) : ColorState = game.states(f)
    implicit def regionToContent(r : Region)(implicit game : Game) : $[Figure] = game.figures.get(r)
    implicit def cardLocationToContent(r : DeckCardLocation)(implicit game : Game) : $[DeckCard] = game.cards.get(r)
    implicit def courtLocationToContent(r : CourtLocation)(implicit game : Game) : $[CourtCard] = game.courtiers.get(r)
    implicit def resourceSlotToContent(r : ResourceSlot)(implicit game : Game) : $[ResourceLike] = game.resources.get(r)

    def log(s : Any*)(implicit game : Game) {
        game.log(s : _*)
    }

    implicit class FactionEx(f : Color)(implicit game : Game) {
        def log(s : Any*) { if (game.logging) game.log((f +: s.$) : _*) }
    }

    implicit def descCard(g : Game, d : DeckCard) = d.img
    implicit def descCourt(g : Game, c : CourtCard) = c.img

    def options(implicit game : Game) = game.options
    def colors(implicit game : Game) = game.colors
    def factions(implicit game : Game) = game.factions
    def board(implicit game : Game) = game.board
    def systems(implicit game : Game) = game.systems
    def current(implicit game : Game) = game.current
    def campaign(implicit game : Game) = game.campaign

    implicit def cards(implicit game : Game) = game.cards
    implicit def courtiers(implicit game : Game) = game.courtiers
    implicit def figures(implicit game : Game) = game.figures
    implicit def resourcesX(implicit game : Game) = game.resources

    implicit class FateDeckEx(val k : FateDeck)(implicit val tracker : IdentityTracker[CourtLocation, CourtCard]) extends KeyEx[CourtLocation, FateDeck, CourtCard]
    implicit class MarketEx(val k : Market)(implicit val tracker : IdentityTracker[CourtLocation, CourtCard]) extends KeyEx[CourtLocation, Market, CourtCard]

    implicit class GuildCardEx(val u : GuildCard)(implicit val tracker : IdentityTracker[CourtLocation, CourtCard]) extends ElemEx[CourtLocation, CourtCard, GuildCard]
    implicit class VoxCardEx(val u : GuildCard)(implicit val tracker : IdentityTracker[CourtLocation, CourtCard]) extends ElemEx[CourtLocation, CourtCard, GuildCard]

    implicit class ResourceTokenEx(val u : ResourceToken)(implicit val tracker : IdentityTracker[ResourceSlot, ResourceLike]) extends ElemEx[ResourceSlot, ResourceLike, ResourceToken]
    implicit class GolemTokenEx(val u : GolemToken)(implicit val tracker : IdentityTracker[ResourceSlot, ResourceLike]) extends ElemEx[ResourceSlot, ResourceLike, GolemToken]
}


//[[ BLACKER
abstract class ColorState(val faction : Color)(implicit game : Game) {
    val reserve : Region

    var damaged : $[Figure] = $

    var regent = false

    def cloned(implicit g : Game) : ColorState

    def ruleValue(s : System) : Int

    def rules(s : System) = faction.ruleValue(s) > game.colors.but(faction)./(_.ruleValue(s)).max

    def at(s : System) = game.figures.get(s).%(_.faction == faction)

    def present(s : System) = game.figures.get(s).exists(_.faction == faction)

    def targetable(s : System) = game.figures.get(s).exists(u => u.faction == faction && u.piece.in(Ship, City, Starport, Flagship, Blight, Banner))

    def pooled(p : Piece) = reserve.$./(_.piece).count(p)

    def pool(p : Piece) = reserve.$.exists(_.piece == p)
}

class BlightsState(override val faction : Blights.type)(implicit game : Game) extends ColorState(faction)(game) {
    override def ruleValue(s : System) : Int = 0

    val reserve : Region = game.figures.register(Reserve(faction), _.faction == faction,
        1.to(24*2)./(Figure(faction, Blight, _))
    )

    def cloned(implicit g : Game) : BlightsState = {
        val s = new BlightsState(faction)(g)
        s.damaged = this.damaged
        s.regent = this.regent
        s
    }
}

class EmpireState(override val faction : Empire.type)(implicit game : Game) extends ColorState(faction)(game) {
    override def ruleValue(s : System) : Int = Empire.at(s).fresh.any.??(99)

    override def rules(s : System) = Empire.at(s).fresh.any

    val reserve : Region = game.figures.register(Reserve(faction), _.faction == faction,
        1.to(15)./(Figure(faction, Ship, _))
    )

    def cloned(implicit g : Game) : EmpireState = {
        val s = new EmpireState(faction)(g)
        s.damaged = this.damaged
        s.regent = this.regent
        s
    }
}

class FreeState(override val faction : Free.type)(implicit game : Game) extends ColorState(faction)(game) {
    override def ruleValue(s : System) : Int = 0

    val reserve : Region = game.figures.register(Reserve(faction), _.faction == faction,
        1.to(28*2)./(Figure(faction, City, _)) ++
        1.to(14*2)./(Figure(faction, Starport, _))
    )

    def cloned(implicit g : Game) : FreeState = {
        val s = new FreeState(faction)(g)
        s.damaged = this.damaged
        s.regent = this.regent
        s
    }
}

class NeutralsState(override val faction : Neutrals.type)(implicit game : Game) extends ColorState(faction)(game) {
    override def ruleValue(s : System) : Int = 0

    val reserve : Region = game.figures.register(Reserve(faction), _.faction == faction)

    def cloned(implicit g : Game) : NeutralsState = {
        val s = new NeutralsState(faction)(g)
        s.damaged = this.damaged
        s.regent = this.regent
        s
    }
}

class FactionState(f : Faction)(implicit game : Game) extends ColorState(f)(game) {
    override val faction : Faction = f

    val reserve : Region = figures.register(Reserve(f), _.faction == f,
        1.to(5)./(Figure(f, City, _)) ++
        1.to(5)./(Figure(f, Starport, _)) ++
        1.to(15)./(Figure(f, Ship, _)) ++
        1.to(10)./(Figure(f, Agent, _)) ++
        1.to(1)./(Figure(f, Flagship, _))
    )

    val exchange = campaign.?(figures.register(Exchange(f), _.faction == f,
        6.to(6)./(Figure(f, City, _)) ++
        6.to(6)./(Figure(f, Starport, _)) ++
        16.to(16)./(Figure(f, Ship, _)) ++
        11.to(11)./(Figure(f, Agent, _))
    ))

    var outraged : $[Resource] = $

    val trophies = game.figures.register(Trophies(f))

    val captives = game.figures.register(Captives(f))

    val favors = game.figures.register(Favors(f))

    var fates : $[Fate] = $
    var failed : $[Fate] = $
    var past : $[Fate] = $
    var leader : |[Leader] = None
    val lores = game.courtiers.register(LoreCards(f))

    var abilities : $[Ability] = $

    var power = 0
    var grand = 0

    var progress = 0
    var objective : |[Objective] = None

    var primus = false

    var flagship : |[Figure] = None

    var spent : $[Resource] = $

    val citySlots = $(
        CityResourceSlot(f, 1, 3),
        CityResourceSlot(f, 2, 1),
        CityResourceSlot(f, 3, 1),
        CityResourceSlot(f, 4, 2),
        CityResourceSlot(f, 5, 1),
        CityResourceSlot(f, 6, 3),
    )

    citySlots.foreach(s => game.resources.register(s))
    game.resources.register(Overflow(f))

    var anyBattle : Boolean = false

    val hand = game.cards.register(Hand(f))
    val played = game.cards.register(Played(f))
    val blind = game.cards.register(Blind(f))

    var displayed : |[DeckCard] = None
    var taking : $[DeckCard] = $
    var takingBlind : $[DeckCard] = $

    val loyal = game.courtiers.register(Loyal(f))
    val discardAfterRound = courtiers.register(DiscardAfterRound(f))
    val reclaimAfterRound = courtiers.register(ReclaimAfterRound(f))

    object taxed {
        var cities : $[Figure] = $
        var slots : $[System] = $
    }

    var worked : $[Figure] = $

    var used : $[Effect] = $
    var secured : $[GuildCard] = $

    var lead : Boolean = false
    var zeroed : Boolean = false
    var declared : Boolean = false
    var surpass : Boolean = false
    var copy : Boolean = false
    var pivot : Boolean = false
    var mirror : Boolean = false

    var adjust : Boolean = true
    var taken : $[ResourceLike] = $
    var outgoing : $[ResourceLike] = $

    var seen : $[DeckCard] = $

    var guess : |[System] = None

    def rivals = game.factions.but(f)
    def others = game.colors.but(f)

    // ambitions, objectives, editcs
    def rulesAOE(s : System) = Empire.at(s).fresh.any.?(f.primus).|(f.rules(s))

    override def ruleValue(s : System) = (campaign && f.regent && game.current.has(f) && Empire.at(s).fresh.any && (f.at(s).any || f.officers)).?(999).|(f.at(s).use(l => l.diff(damaged).count(Ship) + l.flagship.any.??(1)))

    def hasGuild(e : GuildEffect) = loyal.exists(_.as[GuildCard].?(_.effect == e))

    def hasTrait(e : LeaderEffect) = leader.exists(_.effects.has(e))

    def hasLore(e : Lore) = lores.has(e)

    def canPrelude(e : GuildEffect) : Boolean = loyal.exists(_.as[GuildCard].%(_.effect == e).%!(secured.has).any) && used.has(e).not

    override def pooled(p : Piece) = super.pooled(p) - (p == Agent).??(outraged.num)

    override def pool(p : Piece) = (p == Agent).?(pooled(p) > 0).|(super.pool(p))

    def officers = f.regent && (f.hasGuild(ImperialOfficers) || f.hasGuild(RogueAdmirals))

    def canHarm(e : Color, s : System) : Boolean = {
        if (campaign.not)
            return true

        if (f == e)
            return true

        if (f.regent && e == Empire && f.hasGuild(RogueAdmirals).not)
            return false

        if (e == Free && f.hasLore(BookOfLiberation))
            return false

        e.as[Faction].foreach { e =>
            if (f.regent && e.regent && Empire.at(s).any && f.hasGuild(RogueAdmirals).not)
                return false

            if (game.seats.contains(s.cluster) && game.market.exists(_.exists(_.as[VoxCard]./(_.effect).has(FeastDay))))
                return false

            if (s == GateWraithExpansion.Passage)
                return false
        }

        if (e != Blights)
            if (game.ceasefire.has(s.cluster))
                if (hasSpendableResource(Weapon).not)
                    return false

        true
    }

    var boardable : $[ResourceSlot] = $
    var displayable : $[ResourceSlot] = $
    var spendable : $[ResourceSlot] = $
    var adjustable : $[ResourceSlot] = $
    var raidable : $[ResourceSlot] = $
    var stealable : $[ResourceSlot] = $
    var tradeable : $[ResourceSlot] = $
    var countable : $[ResourceSlot] = $
    var unavailable : $[ResourceSlot] = $
    val overflow = Overflow(f)

    def recalculateSlots() {
        val old = displayable

        val n = $(6, 6, 6, 4, 3, 2)(pooled(City))
        unavailable = citySlots.drop(n)
        boardable = citySlots.take(n)
        displayable = hasLore(AncientHoldings).$(AncientHoldingsSlot) ++ boardable
        spendable = displayable ++ hasLore(GolemHearth).$(GolemHearthSlots) ++ hasLore(PirateHoard).$(PirateHoardSlots) ++ hasLore(WellOfEmpathy).$(WellOfEmpathySlots)
        adjustable = spendable ++ $(overflow)

        val raidableOnly = hasGuild(ArsenalKeepers).$(ArsenalKeepersSlots)
        val tradeableOnly = hasGuild(MerchantLeague).$(MerchantLeagueSlots)
        val stealableOnly = primus.$(ImperialTrust)
        val countableOnly = hasLore(GreenVault).$(GreenVaultSlots)

        raidable  = spendable ++ raidableOnly
        tradeable = spendable ++ tradeableOnly
        stealable = raidable ++ stealableOnly
        countable = stealable ++ tradeableOnly ++ countableOnly

        unavailable.content.foreach { r =>
            r --> f.overflow
        }

        if (old.any && old != displayable) {
            val loss = old.diff(displayable)
            val gain = displayable.diff(old)

            if (loss.num > 0)
                adjust = true

            loss.num @@ {
                case 0 =>
                case 1 => f.log("covered a resource slot")
                case 2 => f.log("covered two resource slots")
                case n => f.log("covered", n.hh, "resource slots")
            }

            gain.num @@ {
                case 0 =>
                case 1 => f.log("opened a resource slot")
                case 2 => f.log("opened two resource slots")
                case n => f.log("opened", n.hh, "resource slots")
            }
        }
    }

    private def add(r : Resource) : Boolean = {
        val token = Supply(r).starting

        if (token.any) {
            take(token.get)

            true
        }
        else
            false
    }

    def steal(token : ResourceLike) {
        if (f.hasLore(PirateHoard) && token.isResource) {
            val s = PirateHoardSlots

            token --> s

            game.resources.sortBy(s)(_.order)

            taken :+= token

            adjust = true
        }
        else
            take(token)
    }

    def take(token : ResourceLike) {
        token --> overflow

        f.spendable
            .%(_.canHoldMore)
            .%(_.canHold(token))
            .%(_ != PirateHoardSlots)
            .some
            ./(_.maxBy(_.raidable.|(999)))
            .foreach { s =>
                token --> s

                game.resources.sortBy(s)(_.order)
            }

        taken :+= token

        adjust = true
    }

    def gain(r : Resource) {
        add(r)
    }

    def gain(r : Resource, message : $[Any]) {
        gain("gained", r, message)
    }

    def gain(verb : String, r : Resource, message : $[Any]) {
        if (add(r))
            f.log(verb, r.token, message)
        else
            f.log("could not gain", r, message)
    }

    def hasStealable(enemy : Faction, x : Resource) : Boolean = {
        if (stealable.exists(_.exists(_.is(x))).not)
            return false

        if (f.hasGuild(SwornGuardians))
            return false

        if (game.declared.contains(Keeper) && hasLore(KeepersTrust) && enemy.hasCountableResource(x))
            return false

        return true
    }

    def hasResource(x : Resource) : Boolean = countable.exists(_.exists(_.is(x)))

    def hasSpendableResource(x : Resource) : Boolean = spendable.exists(_.exists(_.is(x)))

    def hasCountableResource(x : Resource) : Boolean = x @@ {
        case Material => hasResource(Material) || (hasGuild(MaterialCartel) && Supply(Material).any)
        case Fuel => hasResource(Fuel) || (hasGuild(FuelCartel) && Supply(Fuel).any)
        case Weapon => hasResource(Weapon) || (hasGuild(WeaponCartel) && Supply(Weapon).any)
        case Relic => hasResource(Relic) || (hasGuild(RelicCartel) && Supply(Relic).any)
        case Psionic => hasResource(Psionic) || (hasGuild(PsionicCartel) && Supply(Psionic).any)
    }

    def numResources(x : Resource) : Int = countable./(_.count {
        case ResourceToken(r, _) if r == x => true
        case _ => false
    }).sum

    def countableResources(x : Resource) : Int = numResources(x) + x.@@ {
        case Material => hasGuild(MaterialCartel).??(Supply(x).num)
        case Fuel => hasGuild(FuelCartel).??(Supply(x).num)
        case Weapon => hasGuild(WeaponCartel).??(Supply(x).num)
        case Relic => hasGuild(RelicCartel).??(Supply(x).num)
        case Psionic => hasGuild(PsionicCartel).??(Supply(x).num)
    }

    def countableResourceIcons(x : Resource) : Int = countableResources(x) + f.loyal.of[GuildCard].count(_.suit == x)

    def ambitionValue(a : Ambition) : Int = a @@ {
        case Tycoon => countableResourceIcons(Material) + countableResourceIcons(Fuel)
        case Tyrant => captives.num + hasLore(GreenVault).??(GreenVaultSlots.num)
        case Warlord => trophies.num
        case Keeper => countableResourceIcons(Relic)
        case Empath => countableResourceIcons(Psionic)
        case Edenguard => systems.%!(_.gate).%(game.resources(_).use(l => l.has(Material) || l.has(Fuel))).%(faction.rules).num
        case Blightkin => systems.%(Blights.at(_).fresh.any).%(faction.rules).num
    }

    def pay(cost : Cost) {
        cost @@ {
            case ReleaseCaptive(u) =>
                u --> u.faction.reserve

            case ReleaseTrophy(u) =>
                u --> u.faction.reserve

            case MultiCost(l) =>
                l.foreach(pay)

            case Pip =>

            case NoCost =>

            case AlreadyPaid =>

            case PayResource(resource, slot) =>
                resource --> PreludeHold(resource.as[ResourceToken].get.resource)

                if (f.objective.has(CommitToPacifism) && game.declared.contains(Empath))
                    f.advance(1, $("spending", Psionic))

            case resource : ResourceToken =>
                resource --> PreludeHold(resource.resource)

                if (f.objective.has(CommitToPacifism) && game.declared.contains(Empath))
                    f.advance(1, $("spending", Psionic))

            case golem : GolemToken =>

            case _ =>
                println("skipping payment " + cost)
        }
    }

    def advance(n : Int, m : $[Any]) {
        if (n != 0 && (progress > 0 || objective.has(ControlTheProceedings))) {
            progress -= n

            f.log("advanced objective by", n.hlb, m)
        }
    }

    def canExchangeSlotContents(a : (|[ResourceLike], ResourceSlot), b : (|[ResourceLike], ResourceSlot)) : Boolean = (a, b) @@ {
        case ((|(ResourceToken(a, _)), _), (|(ResourceToken(b, _)), _)) if a == b => false
        case ((|(t), _), (_, s)) if s.canHold(t).not => false
        case ((_, s), (|(t), _)) if s.canHold(t).not => false
        case ((_, PirateHoardSlots), (|(x), _)) if PirateHoardSlots.has(x).not => false
        case ((|(x), _), (_, PirateHoardSlots)) if PirateHoardSlots.has(x).not => false
        case _ => true
    }

    def isLordIn(n : Int) = game.seats.get(n).?(_.faction == f)

    def isVassalIn(n : Int) = game.seats.contains(n) && isLordIn(n).not && systems.%(_.cluster == n).exists(s => f.at(s).use(l => l.flagship.any || l.cities.any))

    def canUseGuildActions = f.hasGuild(GuildOverseers).not || game.declared.keys.exists { a =>
        val s = f.ambitionValue(a)
        (s > 0) && f.rivals.%(_.ambitionValue(a) >= s).num <= 1
    }

    def cloned(implicit g : Game) : FactionState = {
        val s = new FactionState(faction)(g)

        s.damaged = this.damaged
        s.regent = this.regent

        s.outraged = this.outraged
        s.fates = this.fates
        s.failed = this.failed
        s.past = this.past
        s.leader = this.leader
        s.abilities = this.abilities
        s.power = this.power
        s.grand = this.grand
        s.progress = this.progress
        s.objective = this.objective
        s.primus = this.primus
        s.flagship = this.flagship
        s.spent = this.spent
        s.anyBattle = this.anyBattle
        s.displayed = this.displayed
        s.taking = this.taking
        s.takingBlind = this.takingBlind
        s.taxed.cities = this.taxed.cities
        s.taxed.slots = this.taxed.slots
        s.worked = this.worked
        s.used = this.used
        s.secured = this.secured
        s.lead = this.lead
        s.zeroed = this.zeroed
        s.declared = this.declared
        s.surpass = this.surpass
        s.copy = this.copy
        s.pivot = this.pivot
        s.mirror = this.mirror
        s.adjust = this.adjust
        s.taken = this.taken
        s.outgoing = this.outgoing
        s.seen = this.seen

        s
    }
}
//]]

trait Expansion {
    def perform(a : Action, soft : Void)(implicit game : Game) : Continue

    implicit class ActionMatch(val a : Action) {
        def @@(t : Action => Continue) = t(a)
        def @@(t : Action => Boolean) = t(a)
    }
}

case object NoHand extends HiddenInfo
case object NoLeadersAndLores extends HiddenInfo

case class ViewCardInfoAction(self : Faction, d : DeckCard) extends BaseInfo(Break ~ self.elem ~ " Hand")(d.img) with ViewCard with OnClickInfo { def param = d }
case class ViewLeaderInfoAction(l : Leader) extends BaseInfo(Break ~ "Leaders")(l.img) with ViewLeader with OnClickInfo { def param = l }
case class ViewLoreInfoAction(l : Lore) extends BaseInfo(Break ~ "Lore")(l.img) with ViewLore with OnClickInfo { def param = l }
case class ViewSetupInfoAction(s : String) extends BaseInfo(Break ~ "Setup")(Image(s, styles.setupCard)) with ViewSetup with OnClickInfo { def param = s }


class Game(val setup : $[Faction], val options : $[Meta.O]) extends BaseGame with ContinueGame with LoggedGame {
    private implicit val game = this

    var isOver = false

    val campaign = options.of[CampaignOption].any
    val landl = options.of[LeadersAndLoreOption].any

    var expansions : $[Expansion] =
        campaign.$(FatesCommonExpansion, SummitExpansion, BlightExpansion, LoreExpansion) ++
        landl.$(LeadersExpansion, LoreExpansion) ++
        (campaign.not).$(BaseExpansion) ++
        $(GuildsExpansion, BattleExpansion, MovementExpansion, CommonExpansion)

    var seating : $[Faction] = setup
    var factions : $[Faction] = setup
    var colors : $[Color] = campaign.$(Empire) ++ setup ++ campaign.$(Blights, Free)
    var states = Map[Color, ColorState]()

    implicit val resources = new IdentityTracker[ResourceSlot, ResourceLike]

    Resources.all.foreach(r => resources.register(Supply(r), content = 1.to(5)./(i => ResourceToken(r, i))))

    Resources.all.foreach(r => resources.register(PreludeHold(r)))

    resources.register(PlanetResourcesOverrides)
    resources.register(AncientHoldingsSlot)

    if (campaign)
        resources.register(ImperialTrust)

    implicit val courtiers = new IdentityTracker[CourtLocation, CourtCard]

    var leaders : $[Leader] = $
    val allLores = game.courtiers.register(LoreDeck, content = Lores.all)
    val lores = game.courtiers.register(DraftLores)
    val unusedLores = game.courtiers.register(UnusedLores)

    implicit val figures = new IdentityTracker[Region, Figure]
    val scrap = game.figures.register(Scrap)

    implicit val cards = new IdentityTracker[DeckCardLocation, DeckCard]

    val deck = cards.register(Deck, content =
        DeckCards.deck.%(d => factions.num == 4 || (d.strength > 1 && d.strength < 7)) ++
        campaign.$(EventCard(1), EventCard(2), EventCard(3)).take(factions.num - 1)
    )

    var seen : $[(Int, Faction, |[DeckCard])] = $

    val court = courtiers.register(CourtDeck, content = campaign.?(BlightCards.court).|(BaseCards.base))
    val sidedeck = courtiers.register(SideDeck, content = campaign.??(BlightCards.sidedeck))
    val market = campaign.?(0).|(1).to(4)./(n => Market(n))
    market.foreach(m => courtiers.register(m))
    val council : CourtLocation = Market(0)
    val extraMarket = Market(999)
    courtiers.register(extraMarket)
    val discourt = courtiers.register(CourtDiscard)
    courtiers.register(CourtScrap)

    var act : Int = 0
    var chapter : Int = 0
    var round : Int = 0
    var passed : Int = 0

    var lead : |[ActionCard] = None
    var seized : |[Faction] = None
    var decided : |[Color] = None

    var edicts : $[Edict] = $
    var laws : $[Law] = $

    var drafts : $[NegotiationDraft] = $
    var draftsCount : Int = 0
    var negotiators : $[Faction] = $

    val markers : $[AmbitionMarker] = $(AmbitionMarker(2, 0), AmbitionMarker(3, 2), AmbitionMarker(5, 3), AmbitionMarker(4, 2), AmbitionMarker(6, 3), AmbitionMarker(9, 4), AmbitionMarker(4, 2), AmbitionMarker(6, 3))

    var ambitions : $[Ambition] = $(Tycoon, Tyrant, Warlord, Keeper, Empath)
    var ambitionable : $[AmbitionMarker] = $
    var declared : Map[Ambition, $[AmbitionMarker]] = Map()
    var conspired : Map[Ambition, $[Conspired]] = Map()
    var revealed : Map[Ambition, $[Revealed]] = Map()

    var winners : Map[Ambition, Faction] = Map()

    val board : BaseBoard = factions.num @@ {
        case _ if campaign => BoardFull
        case 3 if options.has(Setup3PMixUp) => Board3MixUp
        case 3 if options.has(Setup3PFrontiers) => Board3Frontiers
        case 3 if options.has(Setup3PCoreConflict) => Board3CoreConflict
        case 4 if options.has(Setup4PMixUp1) => Board4MixUp1
        case 4 if options.has(Setup4PMixUp2) => Board4MixUp2
    }

    var systems = board.systems

    board.systems.foreach(r => figures.register(r))

    var broken : $[Int] = $
    var exit : |[Symbol] = None
    var ceasefire : $[Int] = $
    var unrumored : $[Int] = $
    var feudal : Map[Int, Int] = Map()

    var countedMoves : Int = 0

    def connected(s : System) = {
        if (s.cluster == 7) {
            systems.%(s => exit.has(s.symbol))
        }
        else {
            val l = board.connected(s)

            if (s.gate.not) {
                if (broken.has(s.cluster))
                    l./ {
                        case System(_, Gate) => GateWraithExpansion.Passage
                        case x => x
                    }
                else
                    l
            }
            else
                l./ {
                    case System(i, Gate) if broken.has(i) => GateWraithExpansion.Passage
                    case x => x
                }.distinct
        }
    }

    market.foreach(m => figures.register(Influence(m.index)))
    figures.register(Influence(extraMarket.index))

    var protoGolems : Map[System, GolemType] = Map()
    var portal : |[System] = None

    var seats : Map[Int, Figure] = Map()

    def availableNum(r : Resource) = Supply(r).num

    def available(r : Resource) = Supply(r).any

    def at(s : System) = figures.get(s)

    var starting : $[(System, System, $[System])] = $

    var overridesSoft : Map[System, Resource] = Map()
    var overridesHard : Map[System, Resource] = Map()

    def resources(s : System) : $[Resource] = overridesHard.get(s)./($(_)) || overridesSoft.get(s)./($(_)) ||
        s.gate.?(systems.%(_.cluster == s.cluster).but(s).%(_.$.cities.any)./~(resources)) |
        $(board.resource(s))

    var unslotted : $[Figure] = $

    var brokenPlanets : $[System] = $

    def freeSlots(s : System) : Int = brokenPlanets.has(s).not.??(board.slots(s) - figures.get(s).%(_.piece.is[Building]).diff(unslotted).num)

    var current : |[Faction] = None

    var highlightFaction : $[Faction] = $

    def viewHand(f : Faction) = f.hand./(ViewCardInfoAction(f, _))
    def viewLeaders(l : $[Leader]) = l./(ViewLeaderInfoAction(_))
    def viewLores(l : $[Lore]) = l./(ViewLoreInfoAction(_))

    def info(waiting : $[Faction], self : |[Faction], actions : $[UserAction]) : $[Info] = {
        self.%(states.contains).%(_.fates.has(Conspirator))./~( f =>
            ambitions./~(a => conspired.get(a)./~(l => l./(e => Info(a, dt.Arrow, e))))
        ) ++
        factions.%(states.contains).%(_.fates.has(Pirate))./~( e =>
            self.has(e).??(systems./~(s => s.$.rumors./(r => Info(s, dt.Arrow, r.piece.name.hh)))) ++
            self.but(e)./~(f => systems.%(f.at(_).agents.any)./~(s => s.$.rumors./(r => Info(s, dt.Arrow, r.piece.name.hh))))
        ) ++
        self.%(states.contains)./~( f =>
            actions.has(NoHand).not.??(viewHand(f))
        ) ++
        actions.has(NoLeadersAndLores).not.??(viewLeaders(leaders)) ++
        actions.has(NoLeadersAndLores).not.??(viewLores(lores.$.of[Lore])) ++
        (options.has(RandomizeStartingSystems).not && campaign.not).??(
            (chapter == 0 && options.has(Setup3PMixUp)).$(ViewSetupInfoAction("setup-3p-01")) ++
            (chapter == 0 && options.has(Setup3PFrontiers)).$(ViewSetupInfoAction("setup-3p-02")) ++
            (chapter == 0 && options.has(Setup3PCoreConflict)).$(ViewSetupInfoAction("setup-3p-04")) ++
            (chapter == 0 && options.has(Setup4PMixUp1)).$(ViewSetupInfoAction("setup-4p-01")) ++
            (chapter == 0 && options.has(Setup4PMixUp2)).$(ViewSetupInfoAction("setup-4p-02"))
        )
    }

    def convertForLog(s : $[Any]) : $[Any] = s./~{
        case Empty => None
        case NotInLog(_) => None
        case AltInLog(_, m) => |(m)
        case f : Faction => |(f.elem)
        case d : ActionCard if d.suit.in(Zeal, Wisdom) => |(OnClick(d.copy(suit = Faithful), d.elem.spn(xlo.pointer)))
        case d : DeckCard => |(OnClick(d, d.elem.spn(xlo.pointer)))
        case c : CourtCard => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case c : Leader => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case c : Lore => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case c : Fate => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case c : Ability => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case c : Edict => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case c : Law => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case c : Objective => |(OnClick(c, c.elem.spn(xlo.pointer)))
        case r : IntermissionReport => |(OnClick(r, "INTERMISSION".styled(styles.title)(styles.intermission).styled(xstyles.larger150).spn(xlo.pointer)))
        case (r : ResourceLike, s : ResourceSlot) => |(ResourceLikeInSlot(r, s))
        case l : $[Any] => convertForLog(l)
        case Some(x) => convertForLog($(x))
        case x => |(x)
    }

    override def log(s : Any*) {
        super.log(convertForLog(s.$) : _*)
    }

    def showFigure(u : Figure) : Image = game.showFigure(u, u.faction.damaged.has(u).??(1))

    def showFigure(u : Figure, hits : Int) : Image = {
        val prefix = u.faction @@ {
            case Empire => "imperial-"
            case _ if hits >= 2 => ""
            case Free => "free-"
            case f if hits < 2 => u.faction.short.toLowerCase + "-"
            case _ => ""
        }

        val suffix = (hits == 1).??("-damaged") + (hits >= 2).??("-empty")

        u.piece match {
            case Agent => Image(prefix + "agent" + suffix, styles.qship)
            case City => Image(prefix + "city" + seats.values.$.has(u).??("-seat") + suffix, styles.qbuilding)
            case Starport => Image(prefix + "starport" + suffix, styles.qbuilding)
            case Ship => Image(prefix + "ship" + suffix, styles.qship)
            case Flagship => Image(prefix + "flagship", styles.qflagship)
            case Banner => Image("banner" + suffix, styles.qship)
            case Bunker => Image("bunker" + suffix, styles.qship)
            case Portal => Image("portal", styles.qship)
            case Pilgrim => Image("pilgrim", styles.qship)
            case ClueRight => Image("clue-right", styles.qship)
            case ClueWrong => Image("clue-wrong", styles.qship)
            case Witness => Image("witness", styles.qship)
            case BrokenWorld  => Image("broken-world", styles.qship)
            case Blight => Image("blight" + suffix, styles.qship)
            case Slot => Image("city-empty", styles.qbuilding)
            case ProtoGolem(_) => Image("golem-sleep", styles.qship)
            case HammerFragment | HammerToken => Image("hammer", styles.qship)
            case BrokenWorld => Image("broken-world", styles.qship)
            case Witness => Image("witness", styles.qship)
        }
    }

    def onRemoveFigure(u : Figure) {
        if (game.seats.values.$.has(u)) {
            val cluster = game.seats.keys.$.%(game.seats(_) == u).only

            game.seats -= cluster

            game.feudal = game.feudal.filterNot(_._2 == cluster)
        }
    }

    def build(f : Faction, x : Cost, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        + BuildMainAction(f, x, then).as("Build".styled(f), x)(group).!!!

        if (f.abilities.has(BuildingBunkers)) {
            + BuildBunkerMainAction(f, x, then).as("Build Bunker".styled(f), x, then.as[ThenDesc]./(_.desc))(group).!(f.pool(Agent).not, "no agents").!!!
        }
    }

    def buildAlt(f : Faction, x : Cost, guilds : Boolean, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        if (f.hasLore(LivingStructures)) {
            + NurtureMainAction(f, x, then).as("Nurture".styled(f), x)(group).!!!
        }

        if (f.hasLore(TyrantsAuthority) && game.declared.contains(Tyrant)) {
            + AnnexMainAction(f, x, then).as("Annex".styled(f), x)(group).!!!
        }

        if (f.hasLore(SongOfTheBanner) && systems.exists(_.$.hasA(Banner)) && f.used.has(CallBodies).not) {
            + CallBodiesMainAction(f, x, then).as("Call Bodies".styled(f), x)(group).!!!
        }

        if (f.hasLore(PlanetHammer)) {
            + PrepareBreakWorldMainAction(f, x, then).as("Prepare Break World".styled(f), x)(group).!!!
        }

        if (guilds.not)
            return

        if (f.hasGuild(ForgeworldRefugees)) {
            + ResettleRefugeesMainAction(f, x, Material, ForgeworldRefugees, then).as("Resettle".styled(f), Material, x)(group).!!!
        }

        if (f.hasGuild(BlazeworldRefugees)) {
            + ResettleRefugeesMainAction(f, x, Fuel, BlazeworldRefugees, then).as("Resettle".styled(f), Fuel, x)(group).!!!
        }

        if (f.hasGuild(DeadworldRefugees)) {
            + ResettleRefugeesMainAction(f, x, Weapon, DeadworldRefugees, then).as("Resettle".styled(f), Weapon, x)(group).!!!
        }

        if (f.hasGuild(LostworldRefugees)) {
            + ResettleRefugeesMainAction(f, x, Relic, LostworldRefugees, then).as("Resettle".styled(f), Relic, x)(group).!!!
        }

        if (f.hasGuild(HeartworldRefugees)) {
            + ResettleRefugeesMainAction(f, x, Psionic, HeartworldRefugees, then).as("Resettle".styled(f), Psionic, x)(group).!!!
        }

        if (f.hasGuild(SporeShips)) {
            + SpawnMainAction(f, x, then).as("Spawn".styled(f), x)(group).!!!
        }

        if (f.hasGuild(SporeGuides)) {
            + SeedInitAction(f, x, then).as("Seed".styled(f), x)(group)
        }

        if (f.hasGuild(MiningInterest)) {
            + ManufactureMainAction(f, x, then).as("Manufacture".styled(f), x)(group)
        }

        if (f.hasGuild(ShippingInterest)) {
            + SynthesizeMainAction(f, x, then).as("Synthesize".styled(f), x)(group)
        }

        if (f.hasGuild(PrisonWardens) && f.captives.any) {
            + PressgangMainAction(f, x, then).as("Press Gang".styled(f), x)(group)
        }
    }

    def repair(f : Faction, x : Cost, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        + RepairMainAction(f, x, then).as("Repair".styled(f), x)(group).!(campaign.not && f.damaged.none).use(a => campaign.?(a.!!!).|(a))
    }

    def repairAlt(f : Faction, x : Cost, guilds : Boolean, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        if (f.hasLore(LivingStructures)) {
            + PruneMainAction(f, x, then).as("Prune".styled(f), x)(group).!!!
        }

        if (f.abilities.has(HammerFragments)) {
            + RepairHammerMainAction(f, x, then).as("Repair Hammer".styled(f), x)(group).!!!
        }
    }

    def move(f : Faction, x : Cost, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        + MoveMainAction(f, x, None, false, true, then).as("Move".styled(f), x, then.as[ThenDesc]./(_.desc))(group).!!!

        if (f.abilities.has(BreakingGates)) {
            + BreakGateMainAction(f, x, then).as("Break Gate".styled(f), x, then.as[ThenDesc]./(_.desc))(group).!!!
        }
    }

    def moveAlt(f : Faction, x : Cost, guilds : Boolean, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        if (f.abilities.has(Pilgrims)) {
            + UseThePortalAction(f, x, then).as("Use the Portal".styled(f), x)(group).!(game.portal.exists(f.rules).not)
        }

        if (f.hasLore(SurvivalOverrides)) {
            + MartyrMainAction(f, x, then).as("Martyr".styled(f), x)(group).!!!
        }

        if (f.hasLore(ForceBeams)) {
            + GuideMainAction(f, x, then).as("Guide".styled(f), x)(group).!!!
        }

        if (guilds.not)
            return

        if (f.hasGuild(GuildInvestigators) && CourtDiscard.$.of[GuildCard].any) {
            + RecoverMainAction(f, x, then).as("Recover".styled(f), x)(group).!!!
        }
    }

    def battle(f : Faction, x : Cost, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        + BattleMainAction(f, x, None, false, true, then).as("Battle".styled(f), x, then.as[ThenDesc]./(_.desc))(group).!!!
    }

    def battleAlt(f : Faction, x : Cost, guilds : Boolean, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        if (f.hasLore(GalacticRifles)) {
            + FireRiflesMainAction(f, x, then).as("Fire Rifles".styled(f), x)(group).!!!
        }

        if (f.hasLore(PlanetHammer) && f.used.has(PlanetHammer).not) {
            systems.%!(_.gate).%(_.$.piece(HammerToken).any).foreach { s =>
                + BreakWorldAction(f, x, s, then).as("Break World".styled(f), x)(group).!(s.$.buildinglikes.exists(u => f.canHarm(u.faction, s).not))
            }
        }

        if (f.hasLore(BlightFury)) {
            + AngerInitAction(f, x, then).as("Anger".styled(f), x)(group)
        }

        if (guilds.not)
            return

        if (f.hasGuild(CourtEnforcers)) {
            val limit = f.countableResources(Weapon) + f.loyal.of[GuildCard].count(_.suit == Weapon)

            val l = market.%(m => Influence(m.index).%(_.faction != f).use(l => l.any && l.num < limit))

            + AbductMainAction(f, l./(_.index), x, then).as("Abduct".styled(f), x)(group).!(l.none)
        }

        if (f.hasGuild(HappyHosts)) {
            val l = systems./~(s => s.$.%(_.faction != f).ships.damaged.some.%(_ => f.rules(s)).|($))

            + PacifyAction(f, l, x, then).as("Pacify".styled(f), x)(group).!(l.none)
        }
    }

    def secure(f : Faction, x : Cost, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        + SecureMainAction(f, x, None, false, true, then).as("Secure".styled(f), x, then.as[ThenDesc]./(_.desc))(group).!!!
    }

    def secureAlt(f : Faction, x : Cost, guilds : Boolean, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        if (guilds.not)
            return

        if (f.hasGuild(Dealmakers)) {
            + BargainMainAction(f, x, None, false, true, then).as("Bargain".styled(f), x)(group).!!!
        }
    }

    def influence(f : Faction, x : Cost, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        + InfluenceMainAction(f, x, None, false, true, then).as("Influence".styled(f), x, then.as[ThenDesc]./(_.desc))(group).!(f.pool(Agent).not, "no agents")
    }

    def influenceAlt(f : Faction, x : Cost, guilds : Boolean, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        if (f.abilities.has(SpreadingTheFaith) && BelieverCourtDeck.any) {
            + TeachMainAction(f, x, then).as("Teach".styled(f), x)(group).!!!
        }

        if (f.hasLore(GolemBeacon)) {
            + AwakenMainAction(f, x, then).as("Awaken".styled(f), x)(group).!!!
        }

        if (guilds.not)
            return

        if (f.hasGuild(Informants) && f.hand.any && f.used.has(Informants).not) {
            + SpyMainAction(f, x, then).as("Spy".styled(f), x)(group)
        }

        if (f.hasGuild(PrisonWardens) && f.captives.any) {
            + ExecuteMainAction(f, x, then).as("Execute".styled(f), x)(group)
        }

        if (f.hasGuild(MindManagers) && f.captives.any) {
            + ManipulateMainAction(f, x, then).as("Manipulate".styled(f), x)(group).!!!
        }
    }

    def tax(f : Faction, x : Cost, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        + TaxMainAction(f, x, None, then).as("Tax".styled(f), x)(group).!!!
    }

    def taxAlt(f : Faction, x : Cost, guilds : Boolean, then : ForcedAction)(implicit builder : ActionCollector, group : Elem) {
        if (f.hasLore(SongOfTheBanner) && systems.exists(_.$.hasA(Banner)) && f.used.has(CallSpirits).not) {
            + CallSpiritsMainAction(f, x, then).as("Call Spirits".styled(f), x)(group).!!!
        }

        if (f.hasLore(ClaimsOfNobility)) {
            + ClaimFiefMainAction(f, x, true, then).as("Claim Fief".styled(f), x)(group).!!!
        }

        if (f.hasLore(WardensLevy)) {
            + LevyMainAction(f, x, then).as("Levy".styled(f), x)(group).!!!
        }

        if (guilds.not)
            return

        if (f.hasGuild(ElderBroker) && systems.exists(s => f.rules(s) && f.rivals.exists(e => e.at(s).cities.any))) {
            + TradeMainAction(f, x, then).as("Trade".styled(f), x)(group).!!!
        }

        if (f.hasGuild(BlightReapers)) {
            + HarvestMainAction(f, x, then).as("Harvest".styled(f), x)(group).!!!
        }
    }

    def loggedPerform(action : Action, soft : Void) : Continue = {
        // println("> " + action)

        val c = action.as[SelfPerform]./(_.perform(soft)).|(internalPerform(action, soft, 0))

        highlightFaction = c match {
            case Ask(f, _) => $(f)
            case MultiAsk(a, _) => a./(_.faction)
            case _ => Nil
        }

        // println("< " + c)

        c
    }

    def internalPerform(action : Action, soft : Void, i : Int) : Continue = {
        expansions.foreach { e =>
            e.perform(action, soft) @@ {
                case UnknownContinue =>
                case Force(another) =>
                    if (action.isSoft.not && another.isSoft)
                        soft()

                    return another.as[SelfPerform]./(_.perform(soft)).|(internalPerform(another, soft, i + 1))
                case TryAgain => return internalPerform(action, soft, i + 1)
                case c => return c
            }
        }

        throw new Error("unknown continue on " + action)
    }

    def clonedRerun() : Game = {
        val g = new Game(setup, options)

        actions.foreach(a => g.performRawRecord(a, false))

        g
    }

    def cloned() : Game = {
        val g = new Game(setup, options)

        g.isOver = this.isOver
        g.expansions = this.expansions
        g.seating = this.seating
        g.factions = this.factions
        g.colors = this.colors
        g.leaders = this.leaders
        g.seen = this.seen
        g.act = this.act
        g.chapter = this.chapter
        g.round = this.round
        g.passed = this.passed
        g.lead = this.lead
        g.seized = this.seized
        g.decided = this.decided
        g.edicts = this.edicts
        g.laws = this.laws
        g.drafts = this.drafts
        g.draftsCount = this.draftsCount
        g.negotiators = this.negotiators
        g.ambitions = this.ambitions
        g.ambitionable = this.ambitionable
        g.declared = this.declared
        g.conspired = this.conspired
        g.revealed = this.revealed
        g.winners = this.winners
        g.systems = this.systems
        g.broken = this.broken
        g.exit = this.exit
        g.ceasefire = this.ceasefire
        g.unrumored = this.unrumored
        g.feudal = this.feudal
        g.countedMoves = this.countedMoves
        g.protoGolems = this.protoGolems
        g.portal = this.portal
        g.seats = this.seats
        g.starting = this.starting
        g.overridesSoft = this.overridesSoft
        g.overridesHard = this.overridesHard
        g.unslotted = this.unslotted
        g.brokenPlanets = this.brokenPlanets
        g.current = this.current
        g.highlightFaction = this.highlightFaction

        g.states = this.states.keys.$./(k => k -> this.states(k).cloned(g)).toMap

        g.resources.copyFrom(this.resources)
        g.courtiers.copyFrom(this.courtiers)
        g.figures.copyFrom(this.figures)
        g.cards.copyFrom(this.cards)

        g.states.values.$.of[FactionState].foreach(_.recalculateSlots())

        g
    }

    def cleanFor(f : Faction) = this

    // override def finalize() {
    //     println("finalize()")
    // }
}
