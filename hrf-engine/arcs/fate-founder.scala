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


case object Founder extends Fate("Founder", "fate02", 1) {
    override val expansion = FounderExpansion
}


case object InspireConfidence extends Objective("f02-01b", "Inspire Confidence")

case object ParadeFleets extends GuildEffect("Parade Fleets", Fuel, 999)
case object BookOfLiberation extends Lore("f02-04", "Book of Liberation")
case object PoliticalIntrigue extends VoxEffect("Political Intrigue")


case class ParadeFleetsMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class ParadeFleetsNoShipsAction(self : Faction, then : ForcedAction) extends ForcedAction
case class CanBecomeOutlawAction(self : Faction, then : ForcedAction) extends ForcedAction


object FounderExpansion extends FateExpansion(Founder) {
    val deck = $(
        GuildCard("f02-02", ParadeFleets),
        BookOfLiberation,
        VoxCard("f02-05", PoliticalIntrigue),
        VoxCard("f02-06", PoliticalIntrigue),
        VoxCard("f02-07", PoliticalIntrigue),
        GuildCard("f02-08", ConstructionUnion),
        GuildCard("f02-09", ConstructionUnion),
        GuildCard("f02-10", AdminUnion),
        GuildCard("f02-11", AdminUnion),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // FOUNDER I
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(InspireConfidence)

            f.progress = game.factions.num @@ {
                case 2 => 18
                case 3 => 16
                case 4 => 14
            }

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(ParadeFleets)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        case ParadeFleetsMainAction(f, then) =>
            if (f.pool(Ship))
                Ask(f).group("Place", min(2, f.pooled(Ship)).hlb, Ship.sof(f), "in")
                    .each(systems.%(Free.at(_).cities.any))(s => ShipsInSystemsAction(f, $(s, s), CanBecomeOutlawAction(f, then)).as(s))
                    .needOk
            else
                Then(ParadeFleetsNoShipsAction(f, then))

        case ParadeFleetsNoShipsAction(f, then) =>
            f.log("had no", Ship.of(f), "for", ParadeFleets)

            Then(then)

        case CanBecomeOutlawAction(f, then) =>
            Ask(f)
                .add(BecomeOutlawAction(f, then).as("Become Outlaw".hh)("Outlaw".styled(styles.title)).!(f.regent.not))
                .skip(then)

        case FateFailAction(f, `fate`, 1, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(ConstructionUnion)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(AdminUnion)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case FateDoneAction(f, `fate`, 1, then) =>
            FateDeck(fate) --> BookOfLiberation --> f.lores

            f.log("got", f.lores.last)

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(PoliticalIntrigue)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        // FOUNDER II
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, PoliticalIntrigue), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            if (symbol != Arrow) {
                factions.zp(factions.drop(1) ++ factions.take(1)).foreach { (f, e) =>
                    if (e.pool(Agent)) {
                        e.reserve --> Agent --> f.favors

                        f.log("took", "Favor".styled(e), "from", e)
                    }
                }
            }

            if (symbol != Hex) {
                factions.zp(factions.drop(1) ++ factions.take(1)).foreach { (e, f) =>
                    if (e.pool(Agent)) {
                        e.reserve --> Agent --> f.favors

                        f.log("took", "Favor".styled(e), "from", e)
                    }
                }
            }

            next

        case GainCourtCardAction(f, v @ VoxCard(_, PoliticalIntrigue), lane, main, then) =>
            var next : ForcedAction = DiscardVoxCardAction(f, v, then)

            f.rivals.foreach { e =>
                if (e.pool(Agent)) {
                    e.reserve --> Agent --> f.favors

                    f.log("took", "Favor".styled(e), "from", e)
                }
            }

            next


        // ...
        case _ => UnknownContinue
    }
}
