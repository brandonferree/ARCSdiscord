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


case object Pathfinder extends Fate("Pathfinder", "fate09", 2) {
    override val expansion = PathfinderExpansion
}


case object Portal extends Piece
case object ClueRight extends Piece {
    override val name = "Right Clue"
}
case object ClueWrong extends Piece {
    override val name = "Wrong Clue"
}

case object Pilgrim extends Piece with Effect with Elementary {
    def elem = "Pilgrim".hh
}


case object FindThePortal extends Objective("f09-01b", "Find the Portal")

case object UncoveringClues extends Ability("f09-02", "Uncovering Clues")
case object PortalSeekers extends GuildEffect("Portal Seekers", Relic, 2)
case object CallToPilgrimage extends VoxEffect("Call to Pilgrimage")


case object SeeThePilgrimsDelivered extends Objective("f09-07b", "See the Pilgrims Delivered")

case object Pilgrims extends Ability("f09-08", "Pilgrims")
case object SeekThePortal extends VoxEffect("Seek the Portal")


case class PortalSetupMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class PortalSetupAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction

case class UncoverClueAction(self : Faction, x : Cost, s : System, then : ForcedAction) extends ForcedAction

case class CallToPilgrimageContinueAction(l : $[Faction], then : ForcedAction) extends ForcedAction
case class CallToPilgrimageMainAction(f : Faction, then : ForcedAction) extends ForcedAction

case class CallToPilgrimageSuitContinueAction(l : $[Faction], suit : Resource, then : ForcedAction) extends ForcedAction
case class CallToPilgrimageSuitAction(f : Faction, suit : Resource, then : ForcedAction) extends ForcedAction

case class PilgrimSetupMainAction(self : Faction, then : ForcedAction) extends ForcedAction
case class PilgrimSetupAction(self : Faction, random : Symbol, then : ForcedAction) extends RandomAction[Symbol]

case class PilgrimMoveInitAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class PilgrimMoveMainAction(self : Faction, l : $[System], cancel : Boolean, then : ForcedAction) extends ForcedAction with Soft
case class PilgrimMoveFromAction(self : Faction, s : System, l : $[System], then : ForcedAction) extends ForcedAction with Soft
case class PilgrimMoveAction(self : Faction, s : System, d : System, l : $[System], then : ForcedAction) extends ForcedAction
case class PilgrimMoveDoneAction(self : Faction, then : ForcedAction) extends ForcedAction

case class UseThePortalAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction

case class ScrapPortalShipsMainAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction with Soft
case class ScrapPortalShipAction(self : Faction, s : System, u : Figure, then : ForcedAction) extends ForcedAction
case class ScrapLoyalShipMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft


object PathfinderExpansion extends FateExpansion(Pathfinder) {
    val deck = $(
        VoxCard("f09-05", CallToPilgrimage),
        GuildCard("f09-06", PortalSeekers),
        VoxCard("f09-09", SeekThePortal),
        VoxCard("f09-10", SeekThePortal),
        VoxCard("f09-11", SeekThePortal),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // PATHFINDER II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(FindThePortal)

            f.progress = 8

            f.log("objective was set to", f.progress.hlb)

            f.abilities :+= UncoveringClues

            f.log("gained", f.abilities.last)

            val units = game.figures.register(FatePieces(fate), content =
                1.to(1)./(Figure(Neutrals, Portal, _)) ++
                1.to(8)./(Figure(Neutrals, ClueRight, _)) ++
                1.to(10)./(Figure(Neutrals, ClueWrong, _)) ++
                1.to(6)./(Figure(f, Pilgrim, _))
            )

            SetupFlagshipMainAction(f, $(|(Starport), None, None, None, None, |(City)), PortalSetupMainAction(f, then))

        case PortalSetupMainAction(f, then) =>
            val neighbour = (factions ++ factions).reverse.dropWhile(_ != f).drop(1).first

            Ask(neighbour).group("Place", "Portal".styled(styles.titleW), "in")
                .each(systems.%!(_.gate))(s => PortalSetupAction(neighbour, s, then).as(s))

        case PortalSetupAction(f, s, then) =>
            game.portal = |(s)

            f.log("hid", "Portal".hl.styled(styles.title), "location")

            then

        case UncoverClueAction(f, x, s, then) =>
            f.pay(x)

            if (game.portal.has(s)) {
                if (FatePieces(fate).$.hasA(Portal)) {
                    FatePieces(fate) --> Portal --> s

                    systems.foreach { s =>
                        s.$.piece(ClueRight) --> Scrap
                        s.$.piece(ClueWrong) --> Scrap
                    }
                }

                f.log("found", Portal.of(f), "in", s, x)

                if (f.objective.has(FindThePortal))
                    f.advance(8, $("finding portal"))

                f.abilities :-= UncoveringClues

                f.log("lost", UncoveringClues)
            }
            else
            if (game.portal.exists(p => p.cluster == s.cluster || p.symbol == s.symbol)) {
                if (FatePieces(fate).$.hasA(Portal))
                    FatePieces(fate) --> ClueRight --> s

                f.log("found", ClueRight.of(f), "in", s, x)

                if (f.objective.has(FindThePortal))
                    if (f.progress > 1)
                        f.advance(1, $("finding a clue"))
            }
            else {
                if (FatePieces(fate).$.hasA(Portal))
                    FatePieces(fate) --> ClueWrong --> s

                f.log("found", ClueWrong.of(f), "in", s, x)
            }

            s.$.cities.foreach { _ =>
                game.resources(s).single.foreach { r =>
                    f.gain(r, $("searching"))
                }
            }

            val l = f.reserve.$.ships.take(2 * s.$.starports.num)

            if (l.any) {
                l --> s

                f.log("placed", l.intersperse(Comma), "in", s)
            }

            AdjustResourcesAction(then)

        case FateFailAction(f, `fate`, 2, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PortalSeekers)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FatePieces(fate) --> Portal --> game.portal.get

            f.log("found", Portal.of(f), "in", game.portal.get)

            systems.foreach { s =>
                s.$.piece(ClueRight) --> Scrap
                s.$.piece(ClueWrong) --> Scrap
            }

            f.abilities :-= UncoveringClues

            f.log("lost", UncoveringClues)

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(CallToPilgrimage)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            val city = f.pool(City)
            val starport = f.pool(Starport)

            if (city || starport) {
                val s = f.flagship.get.system
                val prefix = f.short + "-"
                val suffix = (f.rivals.exists(_.rules(s)) || (f.regent.not && Empire.rules(s))).??("-damaged")

                Ask(f).group("Upgrage", Flagship.of(f))
                    .some(Flagship.functions(f)./~(u => u.none.?(u.?).|((u.$.fresh.any && u.armor.none).?(u.armor)))) { u =>
                        city.$(BuildFlagshipAction(f, NoCost, u, City, then).as(City.of(f), Image(prefix + "city" + suffix, styles.qbuilding), "as", u)) ++
                        starport.$(BuildFlagshipAction(f, NoCost, u, Starport, then).as(Starport.of(f), Image(prefix + "starport" + suffix, styles.qbuilding), "as", u))
                    }
            }
            else {
                f.log("had no buildings for an upgrade")

                Then(then)
            }

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, CallToPilgrimage), then) =>
            log("Crisis", v)

            val next = CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

            if (symbol == Arrow)
                CallToPilgrimageContinueAction(factions, then)
            else
                next

        case CallToPilgrimageContinueAction(Nil, then) =>
            then

        case CallToPilgrimageContinueAction(l, then) =>
            CallToPilgrimageMainAction(l.first, CallToPilgrimageContinueAction(l.drop(1), then))

        case CallToPilgrimageMainAction(f, then) =>
            Ask(f).group(CallToPilgrimage)
                .each(f.loyal.of[GuildCard]) { c =>
                    DiscardGuildCardAction(f, c, then).as(c).!(c.keys >= 999)
                }
                .bailout(then.as("Done"))

        case GainCourtCardAction(f, v @ VoxCard(_, CallToPilgrimage), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, ShuffleCourtDeckAction(then))

            val ee = factions.dropWhile(_ != f).drop(1) ++ factions.takeWhile(_ != f)
            val l = $(Material, Fuel, Weapon, Relic, Psionic).%(r => f.loyal.of[GuildCard].exists(_.suit == r))

            Ask(f).group(CallToPilgrimage, "suit")
                .each(l) { r =>
                    CallToPilgrimageSuitContinueAction(ee, r, next).as(r)
                }
                .bailout(next.as("No Guild Cards"))
                .needOk

        case CallToPilgrimageSuitContinueAction(Nil, suit, then) =>
            then

        case CallToPilgrimageSuitContinueAction(l, suit, then) =>
            CallToPilgrimageSuitAction(l.first, suit, CallToPilgrimageSuitContinueAction(l.drop(1), suit, then))

        case CallToPilgrimageSuitAction(f, suit, then) =>
            val l = f.loyal.of[GuildCard].%(_.keys < 999).%(_.suit == suit)

            l.foldLeft(then)((q, c) => DiscardGuildCardAction(f, c, q))

        // PATHFINDER III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(SeeThePilgrimsDelivered)

            f.progress = 0

            f.abilities :+= Pilgrims

            f.log("gained", f.abilities.last)

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(SeekThePortal)).take(1) ++ game.court.$ --> game.court

            f.log("placed", game.court.first, "on top of the court deck")

            PilgrimSetupMainAction(f, then)

        case PilgrimSetupMainAction(f, then) =>
            Random[Symbol]($(Arrow, Crescent, Hex), PilgrimSetupAction(f, _, then))

        case PilgrimSetupAction(f, s, then) =>
            systems.%(_.symbol == s).foreach { s =>
                FatePieces(fate) --> Pilgrim --> s

                log(Pilgrim.of(f), "was placed in", s)
            }

            Then(then)

        case PilgrimMoveInitAction(f, then) =>
            PilgrimMoveMainAction(f, systems./~(s => s.$.piece(Pilgrim)./(_ => s)), true, then)

        case PilgrimMoveMainAction(f, l, cancel, then) =>
            Ask(f).group("Move", "Pilgrim".styled(f), "from")
                .each(l)(s => PilgrimMoveFromAction(f, s, l, then).as(s).!!!)
                .cancelIf(cancel)
                .doneIf(cancel.not)(PilgrimMoveDoneAction(f, then))

        case PilgrimMoveFromAction(f, s, l, then) =>
            Ask(f).group("Move", "Pilgrim".styled(f), "from", s, "to")
                .each(systems.intersect(game.connected(s)).%(s => f.rules(s) || f.at(s).flagship.any))(d => PilgrimMoveAction(f, s, d, l :- s, then).as(d))
                .cancel

        case PilgrimMoveAction(f, s, d, l, then) =>
            s --> Pilgrim.of(f) --> d

            f.log("moved", "Pilgrim".styled(f), "from", s, "to", d)

            PilgrimMoveMainAction(f, l, false, then)

        case PilgrimMoveDoneAction(f, then) =>
            f.used :+= Pilgrims

            then

        case UseThePortalAction(f, x, then) =>
            f.pay(x)

            val l = game.portal.get.$.ofc(f).piece(Pilgrim)

            l --> FatePieces(fate)

            f.log("sent", l./(_ => "Pilgrim".styled(f)).comma, "through the", "Portal".hl)

            val c = FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(SeekThePortal)).take(1)

            if (c.any) {
                c ++ game.court.$ --> game.court

                f.log("placed", game.court.first, "on top of the court deck")
            }

            then

        case GainCourtCardAction(f, v @ VoxCard(_, SeekThePortal), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, ShuffleCourtDeckAction(then))

            val s = game.portal.get

            if (f.rules(s))
                ScrapPortalShipsMainAction(f, s, next)
            else
                Then(next)

        case ScrapPortalShipsMainAction(f, s, then) =>
            Ask(f).group(SeekThePortal, "scraps")
                .each(s.$.ships)(u => ScrapPortalShipAction(f, s, u, then).as(game.showFigure(u)))
                .done(then)

        case ScrapPortalShipAction(f, s, u, then) =>
            u --> Scrap

            f.power += 2

            f.log("sent", u, "through the", "Portal".hl, "for", 2.power)

            ScrapPortalShipsMainAction(f, s, then)

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, SeekThePortal), then) =>
            log("Crisis", v)

            val next = CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

            factions.reverse.foldLeft(next : ForcedAction)((q, f) => ScrapLoyalShipMainAction(f, q))

        case ScrapLoyalShipMainAction(f, then) =>
            Ask(f).group(f, "scraps a ship")
                .some(systems) { s =>
                    f.at(s).ships./(u => ScrapShipsAction(f, |(s), $(u), then).as(u, game.showFigure(u), "in", s))
                }
                .needOk
                .bail(then)

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            if (f.rulesAOE(game.portal.get)) {
                f.grand += 1

                f.log("fulfilled a grand ambition controlling the", "Portal".hh)
            }

            if (FatePieces(fate).$.piece(Pilgrim).num > game.chapter) {
                f.grand += 1

                f.log("fulfilled a grand ambition sending", "Pilgrims".hh)
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
