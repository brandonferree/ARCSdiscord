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


case object Board4MixUp1 extends BaseBoard {
    val name = "4 Players / Mix Up 1"
    val clusters = $(1, 2, 4, 5, 6)

    val starting : $[(System, System, $[System])] = $(
        (System(4, Arrow), System(6, Hex), $(System(1, Gate))),
        (System(4, Hex), System(5, Hex), $(System(6, Gate))),
        (System(5, Arrow), System(1, Hex), $(System(4, Gate))),
        (System(6, Arrow), System(1, Arrow), $(System(5, Gate))),
    )

}

case object Board4MixUp2 extends BaseBoard {
    val name = "4 Players / Mix Up 2"
    val clusters = $(1, 2, 3, 5, 6)

    val starting : $[(System, System, $[System])] = $(
        (System(5, Hex), System(3, Arrow), $(System(2, Gate))),
        (System(3, Hex), System(5, Crescent), $(System(1, Gate))),
        (System(2, Hex), System(1, Hex), $(System(3, Gate))),
        (System(1, Arrow), System(2, Arrow), $(System(5, Gate))),
    )
}

case object Board3MixUp extends BaseBoard {
    val name = "3 Players / Mix Up"
    val clusters = $(2, 3, 5, 6)

    val starting : $[(System, System, $[System])] = $(
        (System(3, Hex), System(5, Crescent), $(System(2, Gate))),
        (System(2, Arrow), System(5, Hex), $(System(3, Gate))),
        (System(2, Hex), System(3, Arrow), $(System(5, Gate))),
    )

}

case object Board3Frontiers extends BaseBoard {
    val name = "3 Players / Frontiers"
    val clusters = $(1, 4, 5, 6)

    val starting : $[(System, System, $[System])] = $(
        (System(1, Hex), System(4, Hex), $(System(6, Gate))),
        (System(5, Hex), System(1, Crescent), $(System(5, Gate))),
        (System(4, Crescent), System(6, Arrow), $(System(1, Gate))),
    )

}

case object Board3CoreConflict extends BaseBoard {
    val name = "3 Players / Core Conflict"
    val clusters = $(1, 2, 4, 5)

    val starting : $[(System, System, $[System])] = $(
        (System(1, Hex), System(2, Crescent), $(System(1, Gate))),
        (System(2, Hex), System(1, Crescent), $(System(2, Gate))),
        (System(1, Arrow), System(2, Arrow), $(System(4, Gate))),
    )

}

case object LoyalEngineers    extends GuildEffect("Loyal Engineers",    Material, 3) with LoyalGuild
case object MiningInterest    extends GuildEffect("Mining Interest",    Material, 2)
case object MaterialCartel    extends GuildEffect("Material Cartel",    Material, 2)
case object AdminUnion        extends GuildEffect("Admin Union",        Material, 2)
case object ConstructionUnion extends GuildEffect("Construction Union", Material, 2)
case object FuelCartel        extends GuildEffect("Fuel Cartel",        Fuel,     2)
case object LoyalPilots       extends GuildEffect("Loyal Pilots",       Fuel,     3) with LoyalGuild
case object Gatekeepers       extends GuildEffect("Gatekeepers",        Fuel,     2)
case object ShippingInterest  extends GuildEffect("Shipping Interest",  Fuel,     2)
case object SpacingUnion      extends GuildEffect("Spacing Union",      Fuel,     2)
case object ArmsUnion         extends GuildEffect("Arms Union",         Weapon,   2)
case object PrisonWardens     extends GuildEffect("Prison Wardens",     Weapon,   2)
case object Skirmishers       extends GuildEffect("Skirmishers",        Weapon,   2)
case object CourtEnforcers    extends GuildEffect("Court Enforcers",    Weapon,   2)
case object LoyalMarines      extends GuildEffect("Loyal Marines",      Weapon,   3) with LoyalGuild
case object LatticeSpies      extends GuildEffect("Lattice Spies",      Psionic,  2)
case object Farseers          extends GuildEffect("Farseers",           Psionic,  2)
case object SecretOrder       extends GuildEffect("Secret Order",       Psionic,  2)
case object LoyalEmpaths      extends GuildEffect("Loyal Empaths",      Psionic,  3) with LoyalGuild
case object SilverTongues     extends GuildEffect("Silver Tongues",     Psionic,  2)
case object LoyalKeepers      extends GuildEffect("Loyal Keepers",      Relic,    3) with LoyalGuild
case object SwornGuardians    extends GuildEffect("Sworn Guardians",    Relic,    1)
case object ElderBroker       extends GuildEffect("Elder Broker",       Relic,    2)
case object RelicFence        extends GuildEffect("Relic Fence",        Relic,    2)
case object GalacticBards     extends GuildEffect("Galactic Bards",     Relic,    1)
case object MassUprisingBB    extends VoxEffect  ("Mass Uprising")
case object PopulistDemandsBB extends VoxEffect  ("Populist Demands")
case object OutrageSpreadsBB  extends VoxEffect  ("Outrage Spreads")
case object SongOfFreedomBB   extends VoxEffect  ("Song of Freedom")
case object GuildStruggleBB   extends VoxEffect  ("Guild Struggle")
case object CallToActionBB    extends VoxEffect  ("Call to Action")

object BaseCards {
    def base = $(
        GuildCard("bc01", LoyalEngineers   ),
        GuildCard("bc02", MiningInterest   ),
        GuildCard("bc03", MaterialCartel   ),
        GuildCard("bc04", AdminUnion       ),
        GuildCard("bc05", ConstructionUnion),
        GuildCard("bc06", FuelCartel       ),
        GuildCard("bc07", LoyalPilots      ),
        GuildCard("bc08", Gatekeepers      ),
        GuildCard("bc09", ShippingInterest ),
        GuildCard("bc10", SpacingUnion     ),
        GuildCard("bc11", ArmsUnion        ),
        GuildCard("bc12", PrisonWardens    ),
        GuildCard("bc13", Skirmishers      ),
        GuildCard("bc14", CourtEnforcers   ),
        GuildCard("bc15", LoyalMarines     ),
        GuildCard("bc16", LatticeSpies     ),
        GuildCard("bc17", Farseers         ),
        GuildCard("bc18", SecretOrder      ),
        GuildCard("bc19", LoyalEmpaths     ),
        GuildCard("bc20", SilverTongues    ),
        GuildCard("bc21", LoyalKeepers     ),
        GuildCard("bc22", SwornGuardians   ),
        GuildCard("bc23", ElderBroker      ),
        GuildCard("bc24", RelicFence       ),
        GuildCard("bc25", GalacticBards    ),
        VoxCard("bc26", MassUprisingBB     ),
        VoxCard("bc27", PopulistDemandsBB  ),
        VoxCard("bc28", OutrageSpreadsBB   ),
        VoxCard("bc29", SongOfFreedomBB    ),
        VoxCard("bc30", GuildStruggleBB    ),
        VoxCard("bc31", CallToActionBB     ),
    )
}

object BaseExpansion extends Expansion {
    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        case StartSetupAction =>
            if (game.starting.none)
                game.starting = board.starting

            CourtSetupAction

        case CheckCourtScrapAction(then) =>
            Then(then)

        case BaseFactionsSetupAction =>
            val f = factions.%(_.adjust).starting

            if (f.any)
                Then(BaseFactionSetupAction(f.get))
            else
                Then(StartChapterAction)

        case BaseFactionSetupAction(f) =>
            factions.zp(game.starting).toList.%<(_ == f).starting.foreach { case (f, (city, port, fleets)) =>
                f.reserve --> City.of(f) --> city
                f.reserve --> Ship.of(f) --> city
                f.reserve --> Ship.of(f) --> city
                f.reserve --> Ship.of(f) --> city
                f.log("placed", City.of(f), "and", Ship.sof(f), "in", city)

                f.reserve --> Starport.of(f) --> port
                f.reserve --> Ship.of(f) --> port
                f.reserve --> Ship.of(f) --> port
                f.reserve --> Ship.of(f) --> port
                f.log("placed", Starport.of(f), "and", Ship.sof(f), "in", port)

                fleets.foreach { fleet =>
                    f.reserve --> Ship.of(f) --> fleet
                    f.reserve --> Ship.of(f) --> fleet
                    f.log("placed", Ship.sof(f), "in", fleet)
                }

                f.gain(board.resource(city))
                f.gain(board.resource(port))

                f.adjust = false

                f.log("took", board.resource(city), "and", board.resource(port))

                f.recalculateSlots()
            }

            BaseFactionsSetupAction

        // HARM
        case TryHarmAction(f, e, s, reserved, then) if campaign.not =>
            Then(then)

        case HarmAction(f, e, s, then) if campaign.not =>
            Then(then)

//[[ PINKER
        // ON SECURE
        case GainCourtCardAction(f, v @ VoxCard(_, CallToActionBB), lane, main, then) =>
            game.deck.take(1) --> f.hand

            f.log("drew", 1.cards)

            DiscardVoxCardAction(f, v, then)

        case GainCourtCardAction(f, v @ VoxCard(_, PopulistDemandsBB), lane, main, then) =>
            (v : CourtCard) --> game.discourt

            val next = DiscardVoxCardAction(f, v, then)

            if (game.ambitionable.any)
                DeclareAmbitionMainAction(f, |(PopulistDemandsBB), game.ambitions, game.ambitionable.last, false, false, $(SkipAction(next)), next)
            else
                Ask(f).group("Declare Ambition".hl, "with", v).add(next.as("No Ambition Markers")).needOk

        case GainCourtCardAction(f, v @ VoxCard(_, MassUprisingBB), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            val nn = systems./(_.cluster).distinct
            val pp = 4.hi(f.pooled(Ship))

            if (pp > 0)
                Ask(f).group("Place", Ship.sof(f), "in a cluster")
                    .some(nn)(n => systems.%(_.cluster == n).combinations(pp)./(l => ShipsInSystemsAction(f, l, next).as(l.comma)))
                    .needOk
                    .cancel
            else
                NoAsk(f)(next)

        case GainCourtCardAction(f, v @ VoxCard(_, GuildStruggleBB), lane, main, then) =>
            val next = ShuffleCourtDiscardAction(DiscardVoxCardAction(f, v, then))

            StealGuildCardMainAction(f, $(next.as("Skip")), next)

        case GainCourtCardAction(f, v @ VoxCard(_, SongOfFreedomBB), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, ShuffleCourtDeckAction(then))

            val l = systems.%(f.rules)

            Ask(f).group(v, "frees a", "City".hl)
                .some(l)(s => factions./~(_.at(s).cities)./(u => FreeCityAction(f, s, u, next).as(u, "in", s)))
                .skip(next)

        case FreeCityAction(f, s, u, then) =>
            u --> u.faction.reserve

            u.faction.damaged :-= u

            f.log("freed", u, "in", s)

            u.faction.as[Faction].foreach { e =>
                e.recalculateSlots()
            }

            AdjustResourcesAction(FreeCitySeizeAskAction(f, then))

        case FreeCitySeizeAskAction(f, then) =>
            if (game.seized.none && factions.first != f)
                Ask(f).group(SongOfFreedom)
                    .add(FreeCitySeizeAction(f, then).as("Seize Initiative".hl))
                    .skip(then)
            else
                NoAsk(f)(then)

        case FreeCitySeizeAction(f, then) =>
            game.seized = |(f)

            f.log("seized the initative")

            if (f.objective.has(SowDivision))
                f.advance(1, $("seizing initative"))

            then

        case GainCourtCardAction(f, v @ VoxCard(_, OutrageSpreadsBB), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            Ask(f).group(v)
                .each(Resources.all)(r => OutrageSpreadsAction(f, r, next).as(ResourceRef(r, None)))
                .skip(next)

        case OutrageSpreadsAction(f, r, then) =>
            val l = factions.dropWhile(_ != f) ++ factions.takeWhile(_ != f)

            l.foldLeft(then)((q, e) => OutrageAction(e, r, None, q))

//]]

        // GAME OVER
        case CheckWinAction =>
            if (game.chapter >= 5 || factions./(_.power).max >= 39 - factions.num * 3) {
                val winner = factions.%(_.power == factions./(_.power).max).first

                Milestone(ArcsGameOverAction(hrf.HRF.version, game.seating, game.options, winner, factions./(f => f -> f.power).to(ListMap), game.chapter))
            }
            else
                Milestone(CartelCleanUpAction(CleanUpChapterAction(StartChapterAction)))

        case ArcsGameOverAction(_, _, _, winner, _, _) =>
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
