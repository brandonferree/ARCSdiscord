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


case object Partisan extends Fate("Partisan", "fate06", 1) {
    override val expansion = PartisanExpansion
}


case object SowDivision extends Objective("f06-01b", "Sow Division")

case object Informants extends GuildEffect("Informants", Psionic, 999)
case object PartisanSeizing extends Ability("f06-03", "Partisan Seizing")
case object OutrageSpreads extends VoxEffect("Outrage Spreads")


case class SpyMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class SpyAction(self : Faction, cost : Cost, e : Faction, d : DeckCard, then : ForcedAction) extends ForcedAction
case class SpyBackAction(self : Faction, e : Faction, d : DeckCard, then : ForcedAction) extends ForcedAction

case class PartisanSeizeAction(self : Faction, r : Resource, then : ForcedAction) extends ForcedAction


object PartisanExpansion extends FateExpansion(Partisan) {
    val deck = $(
        GuildCard("f06-02", Informants),
        GuildCard("f06-05", LoyalEngineers),
        GuildCard("f06-06", LoyalPilots),
        GuildCard("f06-07", LoyalMarines),
        GuildCard("f06-08", LoyalEmpaths),
        GuildCard("f06-09", LoyalKeepers),
        GuildCard("f06-10", GalacticBards),
        GuildCard("f06-11", SpacingUnion),
        GuildCard("f06-12", ArmsUnion),
        VoxCard("f06-13", OutrageSpreads),
        VoxCard("f06-14", OutrageSpreads),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // PARTISAN I
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(SowDivision)

            f.progress = game.factions.num @@ {
                case 2 => 12
                case 3 => 11
                case 4 => 10
            }

            f.log("objective was set to", f.progress.hlb)

            f.abilities :+= PartisanSeizing

            f.log("gained", f.abilities.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(Informants)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        case PartisanSeizeAction(f, r, then) =>
            game.seized = |(f)

            f.log("seized initiative with", r)

            if (f.objective.has(SowDivision))
                f.advance(1, $("seizing initative"))

            OutrageAction(f, r, None, then)

        case SpyMainAction(f, x, then) =>
            YYSelectObjectsAction(f, f.hand)
                .withGroup(Informants, "give highest card")
                .withRule(d => d.strength > 0 && f.hand.but(d).exists(_.strength > d.strength).not)
                .withThens(d => f.rivals./(e => SpyAction(f, x, e, d, then).as("Give", d, "to", e)))
                .withExtras(CancelAction)

        case SpyAction(f, x, e, d, then) =>
            f.pay(x)

            f.used :+= Informants

            d --> e.hand

            f.log("spied on", e, "and gave the highest card")

            YYSelectObjectsAction(e, e.hand)
                .withGroup(Informants, "give back highest card")
                .withRule(d => d.strength > 0 && e.hand.but(d).exists(_.strength > d.strength).not)
                .withThen(d => SpyBackAction(e, f, d, then).as("Give", d, "to", f))("~~~")

        case SpyBackAction(f, e, d, then) =>
            d --> e.hand

            f.log("gave back the highest card")

            then

        case FateFailAction(f, `fate`, 1, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(GalacticBards)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(SpacingUnion)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(ArmsUnion)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(OutrageSpreads)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            f.abilities :-= PartisanSeizing

            f.log("lost", PartisanSeizing)

            if (f.outraged.any) {
                f.outraged = $

                f.log("cleared all", "Outrage".hh)
            }

            Then(then)

        case FateSetupAction(f, then) if f.abilities.has(PartisanSeizing) && f.fates.has(Partisan).not =>
            f.abilities :-= PartisanSeizing

            f.log("lost", PartisanSeizing)

            if (f.outraged.any) {
                f.outraged = $

                f.log("cleared all", "Outrage".hh)
            }

            FateSetupAction(f, then)

        case FateDoneAction(f, `fate`, 1, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(LoyalEngineers)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(LoyalPilots)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(LoyalMarines)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(LoyalEmpaths)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(LoyalKeepers)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(GalacticBards)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        // PARTISAN II
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, OutrageSpreads), then) =>
            val next : ForcedAction = DiscardCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            game.resources(System(cluster, symbol)).foldLeft(next)((q, r) => OutrageSpreadsAction(factions.first, r, q))

        case GainCourtCardAction(f, v @ VoxCard(_, OutrageSpreads), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            Ask(f).group(v)
                .each(Resources.all)(r => OutrageSpreadsAction(f, r, next).as(ResourceRef(r, None)))
                .skip(next)

        case OutrageSpreadsAction(f, r, then) =>
            val l = factions.dropWhile(_ != f) ++ factions.takeWhile(_ != f)

            l.foldLeft(then)((q, e) => OutrageAction(e, r, None, q))


        // ...
        case _ => UnknownContinue
    }
}
