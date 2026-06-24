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


case object Believer extends Fate("Believer", "fate08", 1) {
    override val expansion = BelieverExpansion
}


case object SpreadTheGoodWord extends Objective("f08-01b", "Spread the Good Word")

case object FaithfulDisciples extends GuildEffect("Faithful Disciples", Relic, 999)
case object SpreadingTheFaith extends Ability("f08-03", "Spreading the Faith")
case object TheYoungLight extends GuildEffect("The Young Light", Psionic, 3)
case object TheProdigalOne extends GuildEffect("The Prodigal One", Psionic, 1)
case object TheProphet extends GuildEffect("The Prophet", Psionic, 999)
case object PlotToKidnap extends VoxEffect("Plot to Kidnap")


case object FaithfulCards extends Law("f08-13", "Faithful Cards")
case object PluralisticFaith extends Law("f08-14", "Pluralistic Faith")

case class BelieverCourtCard(card : ActionCard) extends CourtCard {
    val name = card.name
    val id = card.id
    override def elem = card.elem
}

case object BelieverScrap extends DeckCardLocation
case object BelieverDeck extends DeckCardLocation
case object BelieverCourtDeck extends CourtLocation
case object BelieverCourtSecured extends CourtLocation


case class TeachMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class TeachAction(self : Faction, cost : Cost, lane : Int, then : ForcedAction) extends ForcedAction

case class ReserveFaithfulMainAction(self : Faction, c : GuildCard, then : ForcedAction) extends ForcedAction with Soft
case class ReserveFaithfulAction(self : Faction, c : GuildCard, o : DeckCard, d : DeckCard, blind : Boolean, then : ForcedAction) extends ForcedAction

case class TakeCardFromDeckAction(self : Faction, d : DeckCard, then : ForcedAction) extends ForcedAction


object BelieverExpansion extends FateExpansion(Believer) {
    val deck = $(
        GuildCard("f08-02", FaithfulDisciples),
        GuildCard("f08-16", TheYoungLight),
        VoxCard("f08-17", PlotToKidnap),
        GuildCard("f08-18", TheProdigalOne),
        GuildCard("f08-19", SecretOrder),
    )

    val cards = $(
        ActionCard(Faithful, 9, 1),
        ActionCard(Faithful, 8, 1),
        ActionCard(Faithful, 7, 2),
        ActionCard(Faithful, 6, 2),
        ActionCard(Faithful, 5, 3),
        ActionCard(Faithful, 4, 3),
        ActionCard(Faithful, 3, 3),
        ActionCard(Faithful, 2, 4),
        ActionCard(Faithful, 1, 4),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // BELIEVER I
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(SpreadTheGoodWord)

            f.progress = game.factions.num @@ {
                case 2 => 10
                case 3 => 9
                case 4 => 8
            }

            f.log("objective was set to", f.progress.hlb)

            game.laws :+= FaithfulCards

            f.log("set", game.laws.last)

            game.laws :+= PluralisticFaith

            f.log("set", game.laws.last)

            f.abilities :+= SpreadingTheFaith

            f.log("gained", f.abilities.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(FaithfulDisciples)) --> f.loyal

            f.log("took", f.loyal.last)

            game.cards.register(BelieverScrap)
            game.cards.register(BelieverDeck, content = BelieverExpansion.cards)
            game.courtiers.register(BelieverCourtDeck, content = BelieverExpansion.cards./(BelieverCourtCard))
            game.courtiers.register(BelieverCourtSecured)

            Then(then)

        case TeachMainAction(f, x, then) =>
            Ask(f).group(SpreadingTheFaith, x)
                .each(game.market) { m =>
                    TeachAction(f, x, m.index, then).as(m.$)
                        .!(m.first.is[GuildCard].not, "not a guild card")
                        .!(m.first.as[GuildCard].get.suit.use(suit => systems.exists(s => game.resources(s).has(suit) && f.at(s).cities.any)).not, "no matching city")
                        .!(m.num > 1, "already attached")
                }
                .cancel
                .needOk

        case TeachAction(f, x, n, then) =>
            f.pay(x)

            BelieverCourtDeck.first --> Market(n)

            game.deck.%(_.suit != Event).starting.foreach { d =>
                d --> BelieverScrap

                f.log("scrapped", d)
            }

            f.log("taught", Market(n).first, "with", Market(n).last)

            then

        case GainCourtCardAction(f, c @ BelieverCourtCard(card), _, _, then) =>
            (c : CourtCard) --> BelieverCourtSecured

            (card : DeckCard) --> f.hand

            then

        case FateFailAction(f, `fate`, 1, then) =>
            f.abilities :-= SpreadingTheFaith

            f.log("lost", SpreadingTheFaith)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(TheProdigalOne)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(SecretOrder)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            BelieverCourtDeck.$.some.foreach { l =>
                l --> game.court

                f.log("added", l.intersperse(Comma), "to the court deck")
            }

            Then(then)

        case FateDoneAction(f, `fate`, 1, then) =>
            f.abilities :-= SpreadingTheFaith

            f.log("lost", SpreadingTheFaith)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(TheYoungLight)) --> f.loyal

            f.log("took", f.loyal.last)

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(PlotToKidnap)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            BelieverCourtDeck.$.some.foreach { l =>
                l --> game.court

                f.log("added", l.intersperse(Comma), "to the court deck")
            }

            Then(then)

        // BELIEVER II
        case ReserveFaithfulMainAction(f, c, then) =>
            YYSelectObjectsAction(f, f.hand)
                .withGroup(c, "discards a card")
                .withRule(_.strength > 0)
                .withThens(o =>
                    factions./~(e => e.played.%(_.suit == Faithful)./(d => ReserveFaithfulAction(f, c, o, d, false, then).as("Take", d))) ++
                    factions./~(e => e.blind .%(_.suit == Faithful)./(d => ReserveFaithfulAction(f, c, o, d, true , then).as("Take a card played by", e)))
                )
                .withExtras(CancelAction, NoHand)

        case ReserveFaithfulAction(f, c, o, d, blind, then) =>
            f.log("reserved", d, "with", c, "and discarded a card")

            f.hand --> o --> game.deck

            f.loyal --> c --> f.reclaimAfterRound

            if (blind)
                f.takingBlind :+= d
            else
                f.taking :+= d

            then

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, PlotToKidnap), then) =>
            val next : ForcedAction = CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

            log("Crisis", v)

            factions.foreach { f =>
                f.loyal.of[GuildCard].%(_.effect == TheYoungLight).foreach { c =>
                    (c : CourtCard) --> Market(lane)

                    f.log("attached", c, "to", v)
                }
            }

            factions.foreach { f =>
                f.hand.%(_.suit == Faithful).sortBy(_.strength).ending.foreach { d =>
                    d --> game.deck

                    f.log("discarded the highest", Faithful, "card")
                }
            }

            next

        case GainCourtCardAction(f, v @ VoxCard(_, PlotToKidnap), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            val h = game.deck.%(_.suit == Faithful).sortBy(_.strength).ending

            YYSelectObjectsAction(f, game.deck)
                .withGroup(f.elem ~ " takes")
                .withRule(h.has)
                .withThens(d => $(TakeCardFromDeckAction(f, d, next).as("Take", d.elem)))
                .withExtra(h.none.$(next.as("No", Faithful, "cards")))

        case TakeCardFromDeckAction(f, d, then) =>
            game.deck --> d --> f.hand

            f.log("took a", (d.suit == Faithful).?(Faithful), "card from the deck")

            then


        // ...
        case _ => UnknownContinue
    }
}
