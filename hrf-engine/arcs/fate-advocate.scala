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


case object Advocate extends Fate("Advocate", "fate04", 1) {
    override val expansion = AdvocateExpansion
}


case object ProveYourselfToOverseers extends Objective("f04-01b", "Prove Yourself to the Overseers")

case object GuildInvestigators extends GuildEffect("Guild Investigators", Psionic, 999)
case object GuildOverseers extends GuildEffect("Guild Overseers", Relic, 999)
case object GuildStruggle extends VoxEffect("Guild Struggle")

case class RecoverMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class RecoverAction(self : Faction, cost : Cost, index : Int, then : ForcedAction) extends ForcedAction

case class GuildOverseersMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class GuildOverseersMoreAction(self : Faction, then : ForcedAction) extends ForcedAction
case class GuildOverseersAction(self : Faction, c : GuildCard, shuffle : Boolean, then : ForcedAction) extends ForcedAction

case class GiveAwayGuildCardAction(self : Faction, e : Faction, l : $[GuildCard], then : ForcedAction) extends ForcedAction


object AdvocateExpansion extends FateExpansion(Advocate) {
    val deck = $(
        GuildCard("f04-02", GuildInvestigators),
        GuildCard("f04-03", GuildOverseers),
        GuildCard("f04-05", MaterialLiaisons),
        GuildCard("f04-06", FuelLiaisons),
        GuildCard("f04-07", WeaponLiaisons),
        GuildCard("f04-08", RelicLiaisons),
        GuildCard("f04-09", PsionicLiaisons),
        VoxCard("f04-10", DiplomaticFiasco),
        VoxCard("f04-11", GuildStruggle),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // ADVOCATE I
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(ProveYourselfToOverseers)

            f.progress = 18

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(GuildInvestigators)) --> f.loyal

            f.log("took", f.loyal.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(GuildOverseers)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        case RecoverMainAction(f, x, then) =>
            Ask(f).group("Recover", CourtDiscard.$.of[GuildCard].last)
                .each(game.market) { m =>
                    RecoverAction(f, x, m.index, then).as(m.$)
                        .!(Influence(m.index).any, "agents")
                        .!(m.num > 1, "already attached")
                }
                .cancel
                .needOk

        case RecoverAction(f, x, n, then) =>
            f.pay(x)

            (CourtDiscard.$.of[GuildCard].last : CourtCard) --> Market(n)

            f.log("recovered", Market(n).last, "to", Market(n).first)

            if (f.objective.has(ProveYourselfToOverseers))
                if (f.hasGuild(GuildOverseers))
                    f.advance(2, $("recovering"))

            then

        case FateFailAction(f, `fate`, 1, then) =>
            f.loyal.%(_.as[GuildCard]./(_.effect).has(GuildOverseers)).foreach { c =>
                c --> game.court

                f.log("added", c, "to the court deck")
            }

            def guildSuit(e : Faction, r : Resource) = e.loyal./~(_.as[GuildCard]./(_.suit)).count(r)

            if (guildSuit(f, Material) > f.rivals./(guildSuit(_, Material)).max) {
                FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(MaterialLiaisons)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            if (guildSuit(f, Fuel) > f.rivals./(guildSuit(_, Fuel)).max) {
                FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(FuelLiaisons)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            if (guildSuit(f, Weapon) > f.rivals./(guildSuit(_, Weapon)).max) {
                FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(WeaponLiaisons)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            if (guildSuit(f, Relic) > f.rivals./(guildSuit(_, Relic)).max) {
                FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(RelicLiaisons)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            if (guildSuit(f, Psionic) > f.rivals./(guildSuit(_, Psionic)).max) {
                FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PsionicLiaisons)) --> game.court

                f.log("added", game.court.last, "to the court deck")
            }

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(GuildStruggle)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case FateDoneAction(f, `fate`, 1, then) =>
            f.loyal.%(_.as[GuildCard]./(_.effect).has(GuildOverseers)).foreach { c =>
                c --> game.court

                f.log("added", c, "to the court deck")
            }

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(MaterialLiaisons)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(FuelLiaisons)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(WeaponLiaisons)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(RelicLiaisons)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PsionicLiaisons)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(DiplomaticFiasco)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case GainCourtCardAction(f, c @ GuildCard(_, GuildOverseers), lane, main, then) =>
            (c : CourtCard) --> f.loyal

            f.recalculateSlots()

            GuildOverseersMainAction(f, then)

        case GuildOverseersMainAction(f, then) =>
            YYSelectObjectsAction(f, CourtDiscard.$)
                .withGroup(GuildOverseers)
                .withRule(_.is[GuildCard])
                .withThen(c => GuildOverseersAction(f, c.as[GuildCard].get, false, then).as("Take", c))("Take")
                .withExtras(GuildOverseersMoreAction(f, then).as("Search Court Deck")(" "))

        case GuildOverseersMoreAction(f, then) =>
            YYSelectObjectsAction(f, CourtDeck.$ ++ CourtDiscard.$)
                .withGroup(GuildOverseers)
                .withBreak({
                    case 0 => Empty
                    case _ => HorizontalBreak ~ "Discard" ~ HorizontalBreak
                })
                .withSplit($(CourtDeck.$.num))
                .withRule(_.is[GuildCard])
                .withThen(c => GuildOverseersAction(f, c.as[GuildCard].get, false, then).as("Take", c))("Take")
                .withExtra((CourtDeck.$ ++ CourtDiscard.$).of[GuildCard].none.$(then.as("Skip")) ++ $(HiddenOkAction))

        case GuildOverseersAction(f, c, shuffle, then) =>
            (c : CourtCard) --> f.loyal

            f.log("took", c, "with", GuildOverseers)

            f.recalculateSlots()

            if (shuffle)
                ShuffleCourtDeckAction(then)
            else
                then

        // ADVOCATE II
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, GuildStruggle), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            factions.foldLeft(next)((q, f) => f.loyal.of[GuildCard].%(_.keys < 999).some./(l => GiveAwayGuildCardAction(f, (factions ++ factions).dropWhile(_ != f).drop(1).first, l, q)).|(q))

        case GiveAwayGuildCardAction(f, e, l, then) =>
            YYSelectObjectsAction(f, l)
                .withGroup("Give", e, "card due to", GuildStruggle)
                .withThen(c => GiveGuildCardAction(f, e, c, then).as("Give", c, "to", e))("Give to", e)

        case GainCourtCardAction(f, v @ VoxCard(_, GuildStruggle), lane, main, then) =>
            var next : ForcedAction = ShuffleCourtDiscardAction(BuryVoxCardAction(f, v, then))

            StealGuildCardMainAction(f, $(next.as("Skip")), next)


        // ...
        case _ => UnknownContinue
    }
}
