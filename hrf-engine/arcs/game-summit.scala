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


trait NegotiationItem extends Record with GameElementary {
    def elem(implicit game : Game) : Elem
    def participants : $[Faction]
}

trait SingleNegotiationItem extends NegotiationItem {
    def to : Faction
    def from : Faction
    def participants = $(from)
    def cost : Int = 1
}

trait MultiNegotiationItem extends NegotiationItem {
    def broker : Faction
    def unwilling : $[Faction]
}

case class NegotiationShip(from : Faction, to : Faction, s : System, damaged : Boolean) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc(Ship.of(from, damaged), "in", s) }
case class NegotiationCity(from : Faction, to : Faction, s : System, damaged : Boolean, slotted : Boolean) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc(City.of(from, damaged), "in", s, slotted.not.?("(unslotted)")) }
case class NegotiationStarport(from : Faction, to : Faction, s : System, damaged : Boolean, slotted : Boolean) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc(Starport.of(from, damaged), "in", s, slotted.not.?("(unslotted)")) }
case class NegotiationSeat(from : Faction, to : Faction, s : System) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc("Seat", City.of(from, game.seats(s.cluster).damaged), "in", s) }
case class NegotiationAgent(from : Faction, to : Faction, index : Int) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc(Agent.of(from), "on", Market(index).$) }
case class NegotiationFavor(from : Faction, to : Faction, faction : Faction) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc("Favor of", faction, "from", from) }
case class NegotiationCaptive(from : Faction, to : Faction, faction : Faction) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc("Captive of", faction, "from", from) }
case class NegotiationTrophy(from : Faction, to : Faction, faction : Color, p : Piece) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc("Trophy", p.of(faction), "from", from) }
case class NegotiationResource(from : Faction, to : Faction, resource : ResourceLike, slot : ResourceSlot) extends SingleNegotiationItem { override def cost = slot.raidable.|(1) ; def elem(implicit game : Game) = game.desc(resource.isResource.?("Resource"), resource.isGolem.?("Golem"), ResourceLikeInSlot(resource, slot)) }
case class NegotiationEmpireInvitation(from : Faction, to : Faction) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc("Invitation to the", Empire) }
case class NegotiationBrokerPeace(broker : Faction, participants : $[Faction], unwilling : $[Faction], cluster : Int) extends MultiNegotiationItem { def elem(implicit game : Game) = game.desc("Broker Peace".hh, "in cluster", cluster.styled(styles.cluster).hl, "by", broker, participants.some./("with help of" -> _.commaAnd), unwilling./(e => ("with", broker, "returning favor of", e))) }
case class NegotiationForgive(from : Faction, to : Faction, outrage : Resource) extends SingleNegotiationItem { override val cost = 0 ; def elem(implicit game : Game) = game.desc("Forgive".hh, outrage, "Outrage", "of", from) }
case class NegotiationRumor(from : Faction, to : Faction, s : System) extends SingleNegotiationItem { def elem(implicit game : Game) = game.desc("Rumor in", s, "from", from) }

class NegotiationFactionSystemState(val flagship : Boolean, var agent : Boolean, var shipsFresh : Int, var shipsDamaged : Int, var citiesFresh : Int, var citiesDamaged : Int, var starportsFresh : Int, var starportsDamaged : Int, var citiesFreshUnslotted : Int, var citiesDamagedUnslotted : Int, var starportsFreshUnslotted : Int, var starportsDamagedUnslotted : Int, var seat : Int) { def rule = flagship.??(1) + shipsFresh }
class NegotiationFactionCourtState(var agents : Int)
class NegotiationFactionFavorsState(var favors : Int)
class NegotiationFactionCaptivesState(var captives : Int)
class NegotiationFactionReserveState(var ships : Int, var cities : Int, var starports : Int, var agents : Int)
class NegotiationFactionTrophiesState(var ships : Int, var cities : Int, var starports : Int, var agents : Int, var blights : Int)

class NegotiationFactionState(self : Faction)(implicit val game : Game) {
    var regent : Boolean = self.regent
    var primus : Boolean = self.primus
    var outraged : $[Resource] = self.outraged
    val reserve : NegotiationFactionReserveState = new NegotiationFactionReserveState(self.pooled(Ship), self.pooled(City), self.pooled(Starport), self.pooled(Agent).atLeast(0))

    val systems : Map[System, NegotiationFactionSystemState] = game.systems./(s => s -> self.at(s).use(l =>
        new NegotiationFactionSystemState(
            l.flagship.any,
            l.agents.any,
            l.ships.fresh.num, l.ships.damaged.num,
            l.cities.fresh.diff(game.unslotted).%!(game.seats.values.$.has).num, l.cities.damaged.diff(game.unslotted).%!(game.seats.values.$.has).num, l.starports.fresh.diff(game.unslotted).num, l.starports.damaged.diff(game.unslotted).num,
            l.cities.fresh.intersect(game.unslotted).%!(game.seats.values.$.has).num, l.cities.damaged.intersect(game.unslotted).%!(game.seats.values.$.has).num, l.starports.fresh.intersect(game.unslotted).num, l.starports.damaged.intersect(game.unslotted).num,
            l.cities.%(game.seats.values.$.has).num
        )
    )).toMap
    val market : Map[Int, NegotiationFactionCourtState] = game.market./(m => m.index -> new NegotiationFactionCourtState(Influence(m.index).$.ofc(self).num)).toMap
    val favors : Map[Faction, NegotiationFactionFavorsState] = factions.but(self)./(f => f -> new NegotiationFactionFavorsState(Favors(self).$.ofc(f).num)).toMap
    val consents : Map[Faction, NegotiationFactionFavorsState] = factions.but(self)./(f => f -> new NegotiationFactionFavorsState(0)).toMap
    val captives : Map[Faction, NegotiationFactionCaptivesState] = factions.but(self)./(f => f -> new NegotiationFactionCaptivesState(Captives(self).$.ofc(f).num)).toMap
    val trophies : Map[Color, NegotiationFactionTrophiesState] = colors.but(self)./(f => f -> Trophies(self).$.ofc(f).use(l =>
        new NegotiationFactionTrophiesState(l.ships.num, l.cities.num, l.starports.num, l.agents.num, l.blights.num))).toMap
    var resources : $[(ResourceLike, ResourceSlot)] = self.tradeable.desc

    def validate() : $[Elem] =
        (reserve.ships < 0).$(game.desc("Not enough", Ship.sof(self))) ++
        (reserve.cities < 0).$(game.desc("Not enough", City.sof(self))) ++
        (reserve.starports < 0).$(game.desc("Not enough", Starport.sof(self))) ++
        (reserve.agents < 0).$(game.desc("Not enough", Agent.sof(self)))
}

class NegotiationState(implicit val game : Game) {
    val factions : Map[Faction, NegotiationFactionState] = game.factions./(f => f -> new NegotiationFactionState(f)).toMap
    val market : $[Market] = game.market
    val empire : $[System] = game.systems.%(Empire.rules)

    var ceasefire : $[Int] = game.ceasefire
    var participants : $[Faction] = $
    var unwilling : $[Faction] = $

    def validate() : $[Elem] =
        factions.values.$./~(_.validate())

    def listMultiItems(_to : Faction, _from : $[Faction]) : $[MultiNegotiationItem] = {
        implicit val ungame1 : Game = null
        implicit val ungame2 : Game = null

        val result = new collection.mutable.ListBuffer[MultiNegotiationItem]

        if (game.laws.has(PeaceAccords)) {
            val singleRulers : Map[System, $[$[Faction]]] = game.systems./(s => s -> empire.has(s).?(game.factions.%(f => factions(f).primus)./($)).|{
                game.factions.%(f => factions(f).systems(s).rule > game.factions.but(f)./(e => factions(e).systems(s).rule).max)./($)
            })./>(_./(_.but(_to))).toMap

            val combinations = 1.to(_from.num)./~(i => _from.combinations(i).$./(_to +: _))

            val multiRulers : Map[System, $[$[Faction]]] = game.systems./(s => s -> empire.has(s).?(game.factions.%(f => factions(f).primus)./($)).|{
                val cc = combinations.%(l => l./(f => factions(f).systems(s).rule).sum > game.factions.diff(l)./(e => factions(e).systems(s).rule).maxOr(0))./(_.but(_to))
                cc.%(c => cc.forall(x => c.diff(x).none || x.diff(c).any))
            })./>(_./(_.but(_to))).toMap

            $(1, 2, 3, 4, 5, 6).diff(ceasefire).foreach { i =>
                val ll = game.systems.%(_.cluster == i).combinations(2).$./~{ case $(a, b) =>
                    singleRulers(a)./~(ar => singleRulers(b)./(br => NegotiationBrokerPeace(_to, $, (ar ++ br).distinct, i))) ++
                    singleRulers(a)./~(ar => multiRulers(b)./(br => NegotiationBrokerPeace(_to, br, ar, i))) ++
                    multiRulers(a)./~(ar => singleRulers(b)./(br => NegotiationBrokerPeace(_to, ar, br, i))) ++
                    multiRulers(a)./~(ar => multiRulers(b)./(br => NegotiationBrokerPeace(_to, (ar ++ br).distinct, $, i)))
                }.distinct
                .%(n => n.participants.diff(_to +: _from).none)
                .%(n => n.unwilling.intersect(participants).none)
                .%(n => n.unwilling.forall(e => factions(_to).favors(e).favors >= 1 || factions(_to).consents(e).favors >= 1))

                val l = ll.%(a => ll.exists(b =>
                    b.participants.diff(a.participants).none && b.unwilling.diff(a.unwilling).none &&
                    (a.participants.diff(b.participants).any || a.unwilling.diff(b.unwilling).any)
                ).not)

                result ++= l
            }
        }

        result.$
    }

    def listItems(_from : Faction, _to : Faction) : $[SingleNegotiationItem] = {
        implicit val ungame1 : Game = null
        implicit val ungame2 : Game = null

        val result = new collection.mutable.ListBuffer[SingleNegotiationItem]

        val from = factions(_from)
        val to = factions(_to)

        if (from.primus && to.regent.not)
            result += NegotiationEmpireInvitation(_from, _to)

        if (game.laws.has(ReconcileWithTheOutraged)) {
            from.outraged.foreach { r =>
                result += NegotiationForgive(_from, _to, r)
            }
        }

        val lastShip = from.systems.values./(s => s.shipsFresh + s.shipsDamaged).sum <= 1
        val lastCity = from.systems.values./(s => s.citiesFresh + s.citiesDamaged).sum <= 1
        val lastStarport = from.systems.values./(s => s.starportsFresh + s.starportsDamaged).sum <= 1

        val fullShip = to.reserve.ships == 0
        val fullCity = to.reserve.cities == 0
        val fullStarport = to.reserve.starports == 0

        if (to.reserve.ships > -1 && from.systems.values./(s => s.shipsFresh + s.shipsDamaged).sum > 1) {
            from.systems.foreach { case (s, t) =>
                if (t.shipsFresh > 0)
                    result += NegotiationShip(_from, _to, s, false)

                if (t.shipsDamaged > 0)
                    result += NegotiationShip(_from, _to, s, true)
            }
        }

        if (to.reserve.cities > -1 && from.systems.values./(s => s.citiesFresh + s.citiesDamaged + s.citiesFreshUnslotted + s.citiesDamagedUnslotted).sum > 1) {
            from.systems.foreach { case (s, t) =>
                if (t.shipsFresh == 0 && t.shipsDamaged == 0) {
                    if (t.citiesFresh > 0)
                        result += NegotiationCity(_from, _to, s, false, true)

                    if (t.citiesDamaged > 0)
                        result += NegotiationCity(_from, _to, s, true, true)

                    if (t.citiesFreshUnslotted > 0)
                        result += NegotiationCity(_from, _to, s, false, false)

                    if (t.citiesDamagedUnslotted > 0)
                        result += NegotiationCity(_from, _to, s, true, false)
                }
            }
        }

        if (to.reserve.starports > -1 && from.systems.values./(s => s.starportsFresh + s.starportsDamaged + s.starportsFreshUnslotted + s.starportsDamagedUnslotted).sum > 1) {
            from.systems.foreach { case (s, t) =>
                if (t.shipsFresh == 0 && t.shipsDamaged == 0) {
                    if (t.starportsFresh > 0)
                        result += NegotiationStarport(_from, _to, s, false, true)

                    if (t.starportsDamaged > 0)
                        result += NegotiationStarport(_from, _to, s, true, true)

                    if (t.starportsFreshUnslotted > 0)
                        result += NegotiationStarport(_from, _to, s, false, false)

                    if (t.starportsDamagedUnslotted > 0)
                        result += NegotiationStarport(_from, _to, s, true, false)
                }
            }
        }

        if (to.reserve.agents > -1) {
            from.systems.foreach { case (s, t) =>
                if (t.agent && to.systems(s).agent.not)
                    result += NegotiationRumor(_from, _to, s)
            }
        }

        if (to.reserve.agents > -1) {
            market.foreach { m =>
                if (from.market(m.index).agents > 0)
                    result += NegotiationAgent(_from, _to, m.index)
            }
        }

        if (from.reserve.agents > -1)
            result += NegotiationFavor(_from, _to, _from)

        from.favors.foreach { case (e, t) =>
            if (t.favors > 0)
                result += NegotiationFavor(_from, _to, e)
        }

        from.captives.foreach { case (e, t) =>
            if (t.captives > 0)
                result += NegotiationCaptive(_from, _to, e)
        }

        from.trophies.foreach { case (c, t) =>
            if (t.ships > 0)
                result += NegotiationTrophy(_from, _to, c, Ship)

            if (t.cities > 0)
                result += NegotiationTrophy(_from, _to, c, City)

            if (t.starports > 0)
                result += NegotiationTrophy(_from, _to, c, Starport)

            if (t.agents > 0)
                result += NegotiationTrophy(_from, _to, c, Agent)

            if (t.blights > 0)
                result += NegotiationTrophy(_from, _to, c, Blight)

        }

        from.resources.distinct.foreach { case (r, k) =>
            r match {
                case r : ResourceToken =>
                    result += NegotiationResource(_from, _to, r, k)

                case r : GolemToken =>
                    if (false)
                        result += NegotiationResource(_from, _to, r, k)
            }
        }

        result.$
    }

    def applyItem(n : NegotiationItem) {
        implicit val ungame1 : Game = null
        implicit val ungame2 : Game = null

        n @@ {
            case n : SingleNegotiationItem =>
                participants = (participants ++ $(n.from, n.to)).distinct

            case n : MultiNegotiationItem =>
                participants = (participants ++ $(n.broker) ++ n.participants).distinct
                unwilling = (unwilling ++ n.unwilling).distinct
        }

        n @@ {
            case NegotiationShip(from, to, s, false) =>
                factions(from).systems(s).shipsFresh -= 1
                factions(from).reserve.ships += 1
                factions(to  ).reserve.ships -= 1
                // !! factions(to  ).systems(s).shipsFresh += 1

            case NegotiationShip(from, to, s, true) =>
                factions(from).systems(s).shipsDamaged -= 1
                factions(from).reserve.ships += 1
                factions(to  ).reserve.ships -= 1
                // !! factions(to  ).systems(s).shipsDamaged += 1

            case NegotiationCity(from, to, s, false, true) =>
                factions(from).systems(s).citiesFresh -= 1
                factions(from).reserve.cities += 1
                factions(to  ).reserve.cities -= 1
                // !! factions(to  ).systems(s).citiesFresh += 1

            case NegotiationCity(from, to, s, true, true) =>
                factions(from).systems(s).citiesDamaged -= 1
                factions(from).reserve.cities += 1
                factions(to  ).reserve.cities -= 1
                // !! factions(to  ).systems(s).citiesDamaged += 1

            case NegotiationCity(from, to, s, false, false) =>
                factions(from).systems(s).citiesFreshUnslotted -= 1
                factions(from).reserve.cities += 1
                factions(to  ).reserve.cities -= 1
                // !! factions(to  ).systems(s).citiesFreshUnslotted += 1

            case NegotiationCity(from, to, s, true, false) =>
                factions(from).systems(s).citiesDamagedUnslotted -= 1
                factions(from).reserve.cities += 1
                factions(to  ).reserve.cities -= 1
                // !! factions(to  ).systems(s).citiesDamagedUnslotted += 1

            case NegotiationSeat(from, to, s) =>
                factions(from).systems(s).seat -= 1
                factions(from).reserve.cities += 1
                factions(to  ).reserve.cities -= 1
                // !! factions(to  ).systems(s).citiesDamagedUnslotted += 1

            case NegotiationStarport(from, to, s, false, true) =>
                factions(from).systems(s).starportsFresh -= 1
                factions(from).reserve.starports += 1
                factions(to  ).reserve.starports -= 1
                // !! factions(to  ).systems(s).starportsFresh += 1

            case NegotiationStarport(from, to, s, true, true) =>
                factions(from).systems(s).starportsDamaged -= 1
                factions(from).reserve.starports += 1
                factions(to  ).reserve.starports -= 1
                // !! factions(to  ).systems(s).starportsDamaged += 1

            case NegotiationStarport(from, to, s, false, false) =>
                factions(from).systems(s).starportsFreshUnslotted -= 1
                factions(from).reserve.starports += 1
                factions(to  ).reserve.starports -= 1
                // !! factions(to  ).systems(s).starportsFresh += 1

            case NegotiationStarport(from, to, s, true, false) =>
                factions(from).systems(s).starportsDamagedUnslotted -= 1
                factions(from).reserve.starports += 1
                factions(to  ).reserve.starports -= 1
                // !! factions(to  ).systems(s).starportsDamaged += 1

            case NegotiationRumor(from, to, s) =>
                factions(to  ).systems(s).agent = true
                factions(to  ).reserve.agents -= 1
                // !! factions(to  ).market(c).agents += 1

            case NegotiationAgent(from, to, c) =>
                factions(from).market(c).agents -= 1
                factions(from).reserve.agents += 1
                factions(to  ).reserve.agents -= 1
                // !! factions(to  ).market(c).agents += 1

            case NegotiationFavor(from, to, faction) =>
                if (faction == from)
                    factions(from).reserve.agents -= 1
                else
                    factions(from).favors(faction).favors -= 1

                if (faction == to) {
                    // !! factions(to  ).reserve.agents += 1
                }
                else {
                    factions(to  ).consents(faction).favors += 1

                    // ?? factions(to  ).favors(faction).favors += 1
                }

            case NegotiationCaptive(from, to, faction) =>
                factions(from).captives(faction).captives -= 1

                if (faction == to) {
                    factions(to  ).reserve.agents += 1
                }
                else {
                    // !! factions(to  ).captives(faction).captives += 1
                }

            case NegotiationTrophy(from, to, faction, Ship) =>
                factions(from).trophies(faction).ships -= 1

                if (faction == to) {
                    factions(to  ).reserve.ships += 1
                }
                else {
                    // !! factions(to  ).trophies(faction).ships += 1
                }

            case NegotiationTrophy(from, to, faction, City) =>
                factions(from).trophies(faction).cities -= 1

                if (faction == to) {
                    factions(to  ).reserve.cities += 1
                }
                else {
                    // !! factions(to  ).trophies(faction).cities += 1
                }

            case NegotiationTrophy(from, to, faction, Starport) =>
                factions(from).trophies(faction).starports -= 1

                if (faction == to) {
                    factions(to  ).reserve.starports += 1
                }
                else {
                    // !! factions(to  ).trophies(faction).starports += 1
                }

            case NegotiationTrophy(from, to, faction, Agent) =>
                factions(from).trophies(faction).agents -= 1

                if (faction == to) {
                    factions(to  ).reserve.agents += 1
                }
                else {
                    // !! factions(to  ).trophies(faction).agents += 1
                }

            case NegotiationTrophy(from, to, faction, Blight) =>
                factions(from).trophies(faction).blights -= 1
                // !! factions(to  ).trophies(faction).blights += 1

            case NegotiationResource(from, to, resource, slot) =>
                factions(from).resources :-= resource -> slot

                // !! factions(to  ).resources :+= resource -> to.overflow

            case NegotiationEmpireInvitation(from, to) =>
                factions(to).regent = true

            case NegotiationForgive(from, to, outrage) =>
                factions(from).outraged :-= outrage

                factions(to).favors(from).favors += 1

            case NegotiationBrokerPeace(to, rest, unwilling, cluster) =>
                unwilling.foreach { e =>
                    if (factions(to).consents(e).favors >= 1)
                        factions(to).consents(e).favors -= 1
                    else
                    if (factions(to).favors(e).favors >= 1)
                        factions(to).favors(e).favors -= 1
                }

                ceasefire :+= cluster
        }
    }
}

case class NegotiationDraft(initator : Faction, index : Int, parties : $[Faction], approved : $[Faction], items : $[NegotiationItem])

case class StartSummitAction(self : Faction, then : ForcedAction) extends ForcedAction

case class CallToOrderAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class ReturnFavorsMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class ReturnFavorsAction(self : Faction, item : NegotiationItem, favors : $[Faction], then : ForcedAction) extends ForcedAction
case class PetitionCouncilAction(self : Faction, then : ForcedAction) extends ForcedAction
case class LeaveEmpireAction(self : Faction, then : ForcedAction) extends ForcedAction
case class FirstRegentAction(self : Faction, then : ForcedAction) extends ForcedAction
case class ReviveEmpireAction(self : Faction, then : ForcedAction) extends ForcedAction

case class NegotiationsAction(self : Faction, then : ForcedAction) extends ForcedAction
case class ContinueNegotiationsAction(participants : $[Faction], active : $[Faction], then : ForcedAction) extends ForcedAction
case class AbandonNegotiationsAction(self : Faction, then : ForcedAction) extends ForcedAction
case class WaitNegotiationsAction(self : Faction, then : ForcedAction) extends ForcedAction
case class NegotiateMainAction(self : Faction, l : $[Faction], then : ForcedAction) extends ForcedAction with Soft
case class NegotiateComposeAction(self : Faction, l : $[Faction], items : $[NegotiationItem], then : ForcedAction) extends ForcedAction with Soft with SelfExplode with SelfValidate {
    def validate(target : Action) = target @@ {
        case NegotiateComposeAction(f, ll, _, _) => self == f && ll.toSet.equals(l.toSet)
        case NegotiateSubmitAction(f, _, _) => self == f && true // TODO
        case _ => false
    }

    def explode(withSoft : Boolean)(implicit game : Game) = {
        // TODO

        $
    }
}
case class NegotiateApproveAction(self : Faction, draft : Int, then : ForcedAction) extends ForcedAction
case class NegotiateReconsiderAction(self : Faction, draft : Int, then : ForcedAction) extends ForcedAction
case class NegotiateRejectAction(self : Faction, draft : Int, then : ForcedAction) extends ForcedAction

case class NegotiateCommitAction(draft : Int, then : ForcedAction) extends ForcedAction
case class NegotiateExecuteAction(items : $[NegotiationItem], then : ForcedAction) extends ForcedAction
case class NegotiateCleanUpAction(then : ForcedAction) extends ForcedAction

case class NegotiateSubmitAction(self : Faction, items : $[NegotiationItem], then : ForcedAction) extends ForcedAction


object SummitExpansion extends Expansion {
    def executeNegotiationItem(i : NegotiationItem, forced : Boolean)(implicit game : Game) = i @@ {
        case NegotiationShip(from, to, s, damaged) =>
            val u = from.at(s).ships.%(_.damaged == damaged).first
            u --> from.reserve
            val n = to.reserve.$.ships.first
            n --> s

            if (damaged) {
                from.damaged :-= u
                to.damaged :+= n
            }

        case NegotiationCity(from, to, s, damaged, slotted) =>
            val u = from.at(s).cities.%(_.damaged == damaged).%(u => game.unslotted.has(u) != slotted).%!(game.seats.values.$.has).first
            u --> from.reserve
            val n = to.reserve.$.cities.first
            n --> s

            if (damaged) {
                from.damaged :-= u
                to.damaged :+= n
            }

            if (game.unslotted.has(u)) {
                game.unslotted :-= u
                game.unslotted :+= n
            }

            game.seats.keys.foreach { i =>
                if (game.seats.get(i).has(u))
                    game.seats += i -> n
            }

        case NegotiationSeat(from, to, s) =>
            val u = from.at(s).cities.%(game.seats.values.$.has).first
            u --> from.reserve
            val n = to.reserve.$.cities.first
            n --> s

            if (u.damaged) {
                from.damaged :-= u
                to.damaged :+= n
            }

            if (game.unslotted.has(u)) {
                game.unslotted :-= u
                game.unslotted :+= n
            }

            game.seats.keys.foreach { i =>
                if (game.seats.get(i).has(u))
                    game.seats += i -> n
            }

        case NegotiationStarport(from, to, s, damaged, slotted) =>
            val u = from.at(s).starports.%(_.damaged == damaged).%(u => game.unslotted.has(u) != slotted).first
            u --> from.reserve
            val n = to.reserve.$.starports.first
            n --> s

            if (damaged) {
                from.damaged :-= u
                to.damaged :+= n
            }

            if (game.unslotted.has(u)) {
                game.unslotted :-= u
                game.unslotted :+= n
            }

        case NegotiationRumor(from, to, s) =>
            to.reserve --> Agent.of(to) --> s

        case NegotiationAgent(from, to, c) =>
            Influence(c) --> Agent.of(from) --> from.reserve

            to.reserve --> Agent.of(to) --> Influence(c)

        case NegotiationFavor(from, to, faction) =>
            val u = (faction == from).?(from.reserve).|(from.favors) --> Agent.of(faction)

            u --> (faction == to).?(to.reserve).|(to.favors)

            if (forced.not)
                if (to.objective.has(CloseDeals))
                    to.advance(2, $("trading"))

        case NegotiationCaptive(from, to, faction) =>
            val u = from.captives --> Agent.of(faction)

            u --> (faction == to).?(to.reserve).|(to.captives)

        case NegotiationTrophy(from, to, faction, unit) =>
            val u = from.trophies --> unit.of(faction)

            u --> (faction == to).?(to.reserve).|(to.trophies)

        case NegotiationResource(from, to, resource, lock) =>
            to.take(resource)

            from.outgoing :+= resource

            if (forced.not)
                if (to.outgoing.has(resource).not)
                    if (to.objective.has(CloseDeals))
                        to.advance(2, $("trading"))

        case NegotiationEmpireInvitation(from, to) =>
            to.regent = true

        case NegotiationForgive(from, to, outrage) =>
            from.outraged :-= outrage

            val u = from.reserve --> Agent.of(from)

            u --> to.favors

        case NegotiationBrokerPeace(to, rest, unwilling, cluster) =>
            unwilling.foreach { e =>
                to.favors --> Agent.of(e) --> e.reserve
            }

            game.ceasefire :+= cluster

    }

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // SUMMIT
        case StartSummitAction(f, then) =>
            f.log("started a", "Summit".styled(styles.title)(styles.intermission).hl)

            log(DoubleLine)

            log("Call to Order".styled(styles.titleW))

            CallToOrderAction(f, then)

        case CallToOrderAction(f, then) =>
            Ask(f).group("Call to Order".hlb)
                .add(ReturnFavorsMainAction(f, then).as("Return Favors").!(f.favors.none))
                .add(PetitionCouncilAction(f, then).as("Petition the Council").!(game.council.has(ImperialCouncilDecided).not))
                .add(LeaveEmpireAction(f, then).as("Leave Empire").!(f.regent.not))
                .add(ReviveEmpireAction(f, then).as("Revive Empire").!(factions.exists(_.regent)))
                .done(NegotiationsAction(f, then))
                .needOk

        case PetitionCouncilAction(f, then) =>
            game.council --> ImperialCouncilDecided --> game.sidedeck

            game.sidedeck --> ImperialCouncilInSession --> game.council

            game.council.$.dropLast --> game.council

            f.log("petitioned the council")

            CallToOrderAction(f, then)

        case ReturnFavorsMainAction(f, then) =>
            val state = new NegotiationState

            Ask(f).group("Return Favors".hlb)
                .some(f.rivals.%(f.favors.$.ofc(_).any)) { e =>
                    val n = f.favors.$.ofc(e).num

                    val l = state.listItems(e, f).%!(_.as[NegotiationFavor].?(_.faction == e))

                    l./ { i =>
                        val st = new NegotiationState
                        st.applyItem(i)
                        val vl = st.validate()

                        ReturnFavorsAction(f, i, i.cost.times(e), then)
                            .as(i.elem, "for", i.cost.hlb, "Favor" + (i.cost != 1).??("s"))("From", i.from, "to", i.to, "(" ~ n.hlb, "Favor" + (n != 1).??("s") + ")")
                            .!(i.cost > n)
                            .!(vl.any, vl.starting.?(_.text))
                    }
                }
                .add {
                    val l = state.listMultiItems(f, factions.but(f)).%(_.participants.none)
                    l./ { i =>
                        val st = new NegotiationState
                        st.applyItem(i)
                        val vl = st.validate()

                        ReturnFavorsAction(f, i, i.unwilling, then)
                            .as(i.elem, "for", i.unwilling.num.hlb, "Favor" + (i.unwilling.num != 1).??("s"))("...")
                            // .!(i.unwilling.num > n)
                            .!(vl.any, vl.starting.?(_.text))
                    }
                }
                .group(" ")
                .cancel
                .needOk

        case ReturnFavorsAction(f, i, cost, then) =>
            cost.foreach { e =>
                val favor = NegotiationFavor(f, e, e)

                executeNegotiationItem(favor, true)
            }

            cost.distinct.foreach { e  =>
                f.log("called in", cost.count(e).times("Favor".styled(e)).comma, "of", e)
            }

            executeNegotiationItem(i, true)

            log(i.elem, i.as[SingleNegotiationItem]./("to" -> _.to))

            factions.foreach(_.recalculateSlots())

            AdjustResourcesAction(CallToOrderAction(f, then))

        case LeaveEmpireAction(f, then) =>
            f.log("left the", Empire)

            Then(BecomeOutlawAction(f, CallToOrderAction(f, then)))

        case ReviveEmpireAction(f, then) =>
            f.log("revived the", Empire)

            BecomeRegentAction(f, then)

        case FirstRegentAction(f, then) =>
            f.primus = true

            f.log("became the", "First Regent".styled(Empire)(styles.title))

            then

        case NegotiationsAction(f, then) =>
            log(DoubleLine)
            log("Negotiations".styled(styles.titleW))
            log(DottedLine)

            game.negotiators = factions

            factions.foreach(_.outgoing = $)

            ContinueNegotiationsAction(factions, factions, then)

        case ContinueNegotiationsAction(participants, active, then) if participants.none =>
            log("Negotiations ended")
            log(DoubleLine)

            game.drafts = $
            game.draftsCount = 0
            game.negotiators = $

            factions.foreach(_.outgoing = $)

            then

        case ContinueNegotiationsAction(participants, active, then) if active.none =>
            log("Negotiations stalled")
            log(DottedLine)

            ContinueNegotiationsAction(participants, participants, then)

        case ContinueNegotiationsAction(participants, active, then) =>
            val next = ContinueNegotiationsAction(participants, participants, then)

            val l = participants

            MultiAsk(active./(f =>
                Ask(f)
                    .some(game.drafts) { d =>
                        // val g = $("Deal Draft", "#".hl ~ game.act.hl ~ "." ~ game.chapter.hl ~ "." ~ game.round.hl ~ "." ~ d.index.hlb, d.approved.some./(l => $(Comma, "approved by", d.approved.commaAnd)))
                        val g = $[Any]("Deal Draft", "#".hl ~ d.index.hlb, d.approved.some./(l => $(Comma, "approved by", d.approved.commaAnd)))
                        d.items./(i => Info(i.elem, i.as[SingleNegotiationItem]./("to" -> _.to))(g)) ++
                        d.parties.has(f).$(
                            d.approved.has(f).?(NegotiateReconsiderAction(f, d.index, next).as("Reconsider".styled(xstyles.warning))(g)).|(NegotiateApproveAction(f, d.index, next).as("Approve".styled(Blights))(g)),
                            NegotiateRejectAction(f, d.index, next).as("Reject".styled(styles.hit))(g)
                        )
                    }
                    .group("Negotiations".hl)
                    .each(l.but(f))(e => NegotiateMainAction(f, $(f, e), next).as("Negotiate".hh, "with", e))
                    .when(l.num > 2)(NegotiateMainAction(f, l, next).as("Negotiate".hh, "with", "multiple parties".hh))
                    .when(f.objective.has(CloseDeals))(ExportMainAction(f, ContinueNegotiationsAction(l, active, then)).as("Export")(Magnate).!!!)
                    .add(WaitNegotiationsAction(f, ContinueNegotiationsAction(l, active.but(f), then)).as("Wait")(" "))
                    .add(AbandonNegotiationsAction(f, ContinueNegotiationsAction(l.but(f), active.but(f), then)).as("Withdraw".styled(xstyles.error))("  "))
                    .needOk
            ), MultiAskPolicy.BotPriority)

        case WaitNegotiationsAction(f, then) =>
            // f.log("waited") !!

            then

        case AbandonNegotiationsAction(f, then @ ContinueNegotiationsAction(l, _, _)) =>
            f.log("withdrew from the negotiations")
            log(DottedLine)

            if (l.num >= 2) {
                val (remaining, invalid) = game.drafts.partition(_.parties.has(f).not)

                invalid.foreach { o =>
                    log("Deal draft", "#".hl ~ o.index.hlb, "was no longer valid")
                }

                game.drafts = remaining

                log(DottedLine)
            }

            then

        case NegotiateMainAction(f, l, then) =>
            NegotiateComposeAction(f, l, $, then)

        case NegotiateComposeAction(f, l, items, then) =>
            val state = new NegotiationState

            items.foreach(state.applyItem)

            val v = state.validate()

            var ask = Ask(f)

            ask = ask
                .group("Compose Deal".hh)
                .each(items)(i => Info(i.elem, i.as[SingleNegotiationItem]./("to" -> _.to)))
                .add(NegotiateSubmitAction(f, items, then).as("Submit".hlb, v./(HorizontalBreak -> _)).!(v.any).!(items.none))

            if (items.num < 12) {
                l.combinations(2).$./~(c => $(c(0) -> c(1), c(1) -> c(0)))./{ (a, b) =>
                    ask = ask
                        .group("From", a, "to", b)
                        .each(state.listItems(a, b))(i => NegotiateComposeAction(f, l, items :+ i, then).as(i.elem).noClear)
                }

                ask = ask
                    .group("...")
                    .each(state.listMultiItems(f, l))(i => NegotiateComposeAction(f, l, items :+ i, then).as(i.elem).noClear)
            }

            ask.group(" ").cancel

        case NegotiateSubmitAction(f, items, then) =>
            val state = new NegotiationState

            items.foreach(state.applyItem)

            game.draftsCount += 1

            game.drafts :+= NegotiationDraft(f, game.draftsCount, state.participants.distinct, $(f), items)

            // f.log("drafted deal", "#".hl ~ game.act.hl ~ "." ~ game.chapter.hl ~ "." ~ game.round.hl ~ "." ~ game.draftsCount.hlb)

            f.log("drafted deal", "#".hl ~ game.draftsCount.hlb)
            items.foreach { i => log("#".hl ~ game.draftsCount.hlb, ">", i.elem, i.as[SingleNegotiationItem]./("to" -> _.to)) }
            log(DottedLine)

            then

        case NegotiateRejectAction(f, n, then) =>
            game.drafts = game.drafts./~{ d =>
                if (d.index == n)
                    None
                else
                    Some(d)
            }

            f.log("rejected deal", "#".hl ~ n.hlb)
            log(DottedLine)

            then

        case NegotiateReconsiderAction(f, n, then) =>
            game.drafts = game.drafts./{ d =>
                if (d.index == n)
                    d.copy(approved = d.approved.but(f))
                else
                    d
            }

            f.log("reconsidered deal", "#".hl ~ n.hlb)
            log(DottedLine)

            then

        case NegotiateApproveAction(f, n, then) =>
            game.drafts = game.drafts./{ d =>
                if (d.index == n)
                    d.copy(approved = d.approved :+ f)
                else
                    d
            }

            f.log("approved deal", "#".hl ~ n.hlb)
            log(DottedLine)

            val result = game.drafts.%(_.index == n).%(d => d.parties.diff(d.approved).none).single

            if (result.any)
                Then(NegotiateCommitAction(n, then))
            else
                then

        case NegotiateCommitAction(n, then) =>
            val d = game.drafts.%(_.index == n).only

            game.drafts = game.drafts.%(_.index != n)

            log("Executed deal", "#".hl ~ n.hlb, "agreed by", d.approved.commaAnd)
            log(DottedLine)

            factions.foreach { f =>
                f.exchange.get.$.ships.last --> f.reserve
                f.exchange.get.$.cities.last --> f.reserve
                f.exchange.get.$.starports.last --> f.reserve
                f.exchange.get.$.agents.last --> f.reserve
            }

            NegotiateExecuteAction(d.items, then)

        case NegotiateExecuteAction(Nil, then) =>
            factions.foreach { f =>
                f.reserve.$.ships.last --> f.exchange.get
                f.reserve.$.cities.last --> f.exchange.get
                f.reserve.$.starports.last --> f.exchange.get
                f.reserve.$.agents.last --> f.exchange.get
            }

            factions.foreach(_.recalculateSlots())

            AdjustResourcesAction(NegotiateCleanUpAction(then))

        case NegotiateExecuteAction(items, then) =>
            val i = items.first

            log(i.elem, i.as[SingleNegotiationItem]./("to" -> _.to))

            executeNegotiationItem(i, false)

            NegotiateExecuteAction(items.drop(1), then)

        case NegotiateCleanUpAction(then) =>
            val (remaining, invalid) = game.drafts.partition { o =>
                val main = o.parties.first
                val rest = o.parties.dropFirst
                val combination = o.parties.combinations(2).$./~(c => $(c(0) -> c(1), c(1) -> c(0)))

                val state = new NegotiationState
                var ok = true

                o.items.foreach { i =>
                    if (ok) {
                        if (combination.exists { case (a, b) => state.listItems(a, b).contains(i) } || state.listMultiItems(main, rest).contains(i))
                            state.applyItem(i)
                        else
                            ok = false
                    }
                }

                ok
            }

            invalid.foreach { o =>
                log("Deal draft", "#".hl ~ o.index.hlb, "was no longer valid")
            }

            if (invalid.any)
                log(DottedLine)

            game.drafts = remaining./(_.copy(approved = $))

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
