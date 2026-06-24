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


case class GainResourceAction(self : Faction, x : Resource, then : ForcedAction) extends ForcedAction
case class FillSlotsMainAction(self : Faction, x : Resource, then : ForcedAction) extends ForcedAction
case class StealResourceMainAction(self : Faction, x : |[Resource], extra : $[UserAction], then : ForcedAction) extends ForcedAction with Soft

case class StealGuildCardMainAction(self : Faction, alt : $[UserAction], then : ForcedAction) extends ForcedAction with Soft
case class StealGuildCardAction(self : Faction, e : Faction, c : CourtCard, then : ForcedAction) extends ForcedAction

case class ShipsInSystemMainAction(self : Faction, l : $[System], then : ForcedAction) extends ForcedAction with Soft
case class ShipAtEachGateMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class ShipsInSystemsAction(self : Faction, l : $[System], then : ForcedAction) extends ForcedAction

case class PressgangMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class PressgangAction(self : Faction, u : Figure, r : Resource, cost : Cost, then : ForcedAction) extends ForcedAction

case class ExecuteMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class ExecuteAction(self : Faction, l : $[Figure], cost : Cost, then : ForcedAction) extends ForcedAction

case class AbductMainAction(self : Faction, l : $[Int], cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class AbductAction(self : Faction, n : Int, cost : Cost, then : ForcedAction) extends ForcedAction

case class FarseersMainAction(self : Faction, e : Faction, then : ForcedAction) extends ForcedAction // with Soft
case class FarseersAction(self : Faction, e : Faction, d : DeckCard, then : ForcedAction) extends ForcedAction
case class FarseersBackAction(self : Faction, e : Faction, d : DeckCard, then : ForcedAction) extends ForcedAction

case class FarseersRedrawMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class FarseersRedrawAction(self : Faction, l : $[DeckCard], then : ForcedAction) extends ForcedAction

case class FenceResourceAction(self : Faction, r : Resource, cost : Cost, then : ForcedAction) extends ForcedAction
case class GainResourcesAction(self : Faction, r : $[Resource], then : ForcedAction) extends ForcedAction

case class ManufactureMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction
case class SynthesizeMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction

case class TradeMainAction(self : Faction, cost : Cost, then : ForcedAction) extends ForcedAction with Soft
case class GiveBackResourceMainAction(self : Faction, cost : Cost, e : Faction, take : ResourceLike, then : ForcedAction) extends ForcedAction with Soft
case class TradeResourceAction(self : Faction, cost : Cost, e : Faction, give : ResourceLike, take : ResourceLike, then : ForcedAction) extends ForcedAction


object GuildsExpansion extends Expansion {
    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
//[[ GREENER
        // COURT
        case GainResourceAction(f, r, then) =>
            f.gain(r, $)

            then

        case FillSlotsMainAction(f, r, then) if soft =>
            val l = f.spendable.%(s => s.canHold(r) && s.canHoldMore)

            if (l.any && game.available(r)) {
                if (l.but(PirateHoardSlots).any)
                    Then(GainResourceAction(f, r, FillSlotsMainAction(f, r, then)))
                else
                    Then(AdjustResourcesAction(then))
            }
            else
            if (l.any && f.rivals.exists(_.hasStealable(f, r))) {
                StealResourceMainAction(f, |(r), $(), FillSlotsMainAction(f, r, then))
            }
            else
                Then(AdjustResourcesAction(then))

        case StealResourceMainAction(f, r, extra, then) =>
            Ask(f).group("Steal", r.|("Resource"))
                .some(f.rivals.%(_.stealable.resources.lefts.use(l => r./(r => l.%(_.is(r))).|(l)).any)) { e =>
                    e.stealable.resources.use(l => r./(r => l.%<(_.is(r))).|(l))./ { case (r, k) =>
                        StealResourceAction(f, e, r, k, then).as(r -> k, "from", e)
                            .!(e.hasGuild(SwornGuardians), SwornGuardians.name)
                            .!(game.declared.contains(Keeper) && e.hasLore(KeepersTrust) && r.as[ResourceToken]./(_.resource).?(f.hasCountableResource), KeepersTrust.name)
                    }
                }
                .needOk
                .add(extra)

        case StealGuildCardMainAction(f, alt, then) =>
            Ask(f).group("Steal".hl, "a", "Guild Card".hh)
                .some(f.rivals)(e => e.loyal.of[GuildCard]./ { c =>
                    StealGuildCardAction(f, e, c, then).as(c, "from", e)
                        .!(c.keys >= 999, "protected")
                        .!(e.loyal.but(c).exists(_.as[GuildCard].?(_.effect == SwornGuardians)), SwornGuardians.name)
                        .!(game.declared.contains(Keeper) && e.hasLore(KeepersSolidarity) && e.hasCountableResource(c.suit), KeepersSolidarity.name)
                        .!(c.suit == Weapon && e.hasGuild(HonorGuard) && e.hasCountableResource(Weapon), HonorGuard.name)
                })
                .add(alt)

        case ReserveCardMainAction(f, c, l, then) =>
            Ask(f).group("Take played card")
                .each(l)(d => ReserveCardAction(f, c, d, then).view(d)(_.img).!(factions.exists(_.taking.has(d))))
                .cancel

        case ReserveCardAction(f, c, d, then) =>
            f.log("reserved", d, "with", c)

            f.loyal --> c --> (d.suit == Faithful).?(f.reclaimAfterRound).|(f.discardAfterRound)

            f.taking :+= d

            then

        case ShipAtEachGateMainAction(f, then) =>
            val ss = systems.%(_.gate)./(s => game.broken.has(s.cluster).?(GateWraithExpansion.Passage).|(s))
            val n = ss.num.hi(f.pooled(Ship))

            Ask(f).group("Place", Ship.sof(f), "at gates")
                .each(ss.combinations(n).$)(l => ShipsInSystemsAction(f, l, then).as(l.comma))
                .needOk
                .cancel

        case ShipsInSystemsAction(f, ss, then) =>
            ss.distinct.foreach { s =>
                val l = f.reserve.$.ships.take(ss.count(s))

                l --> s

                f.log("placed", l.comma, "in", s)
            }

            then

        case ShipsInSystemMainAction(f, l, then) =>
            Ask(f).group("Place", 3.hi(f.pooled(Ship)).hlb, Ship.sof(f), "in")
                .each(l)(s => ShipsInSystemsAction(f, $(s, s, s), then).as(s))
                .needOk
                .cancel

        case PressgangMainAction(f, x, then) =>
            val done = (x == AlreadyPaid).?(AdjustResourcesAction(then).as("Done")).|(CancelAction)

            implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, selected.??(2))

            if (f.captives.any)
                YYSelectObjectsAction(f, f.captives)
                    .withGroup("Pressgang captives", x)
                    .withThens(u => Resources.all.%(game.available)./(r => PressgangAction(f, u, r, x, then).as(ResourceRef(r, None))))
                    .withExtras(done)
            else
                Ask(f).add(done)

        case PressgangAction(f, u, r, x, then) =>
            f.pay(x)

            u --> u.faction.reserve

            f.gain(r, $("releasing", u, x))

            PressgangMainAction(f, AlreadyPaid, then)

        case ExecuteMainAction(f, x, then) =>
            implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, selected.??(2))

            XXSelectObjectsAction(f, f.captives)
                .withGroup("Execute captives", x)
                .withThen(l => ExecuteAction(f, l, x, then))(l => ("Execute", l))
                .withExtras(CancelAction)

        case ExecuteAction(f, l, x, then) =>
            f.pay(x)

            f.captives --> l --> f.trophies

            f.log("executed", l, x)

            then

        case AbductMainAction(f, l, x, then) =>
            Ask(f).group("Abduct".hl, "Agents", x)
                .each(game.market)(m => AbductAction(f, m.index, x, then).as(m.$).!(l.has(m.index).not))
                .cancel

        case AbductAction(f, c, x, then) =>
            f.pay(x)

            val l = Influence(c).%(_.faction != f)

            l --> f.captives

            f.log("abducted", l.comma, x)

            then

        case FarseersMainAction(f, e, then) =>
            YYSelectObjectsAction(f, e.hand)
                .withGroup(Farseers, "look at", e, "cards")
                .withThen(d => FarseersAction(f, e, d, then))(d => ("Take", d, "from", e))("Take a card from", e)
                .withExtras(then.as("Skip"))

        case FarseersAction(f, e, d, then) =>
            e.hand --> d --> f.hand

            f.log("took a card from", e, "with", Farseers)

            YYSelectObjectsAction(f, f.hand)
                .withGroup(Farseers, "give back", e, "a card")
                .withThen(d => FarseersBackAction(f, e, d, then))(d => ("Give", d, "to", e))("Give a card back to", e)
                .withExtras(NoHand)

        case FarseersBackAction(f, e, d, then) =>
            f.hand --> d --> e.hand

            f.log("gave back a card to", e)

            then

        case FarseersRedrawMainAction(f, then) =>
            XXSelectObjectsAction(f, f.hand)
                .withGroup(Farseers, "discard cards to redraw")
                .withRule(_.upTo(game.deck.any.?(game.deck.num).|(0) - 1))
                .withThen(l => FarseersRedrawAction(f, l, then))(l => ("Discard", Farseers, "and", l.num.cards, "to draw", (l.num + 1).cards))
                .withExtras(NoHand, FarseersRedrawAction(f, $, then).as("Discard", Farseers, "only to draw", 1.cards), CancelAction)

        case FarseersRedrawAction(f, l, then) =>
            l.foreach { d =>
                if (f.hand.has(d))
                    d --> game.deck
            }

            val r = game.deck.take(l.num + 1)

            r --> f.hand

            f.log("discarded", l.num.cards, "and drew", r.num.cards)

            then

        case FenceResourceAction(f, r, x, then) =>
            f.pay(x)

            f.gain("fenced", r, $(x))

            AdjustResourcesAction(then)

        case GainResourcesAction(f, l, then) =>
            l.foreach { r => f.gain(r, $) }

            AdjustResourcesAction(then)

        // MANUFACTURE
        case ManufactureMainAction(f, x, then) =>
            f.pay(x)

            f.gain("manufactured", Material, $(x))

            AdjustResourcesAction(then)

        // SYNTHESIZE
        case SynthesizeMainAction(f, x, then) =>
            f.pay(x)

            f.gain("synthesized", Fuel, $(x))

            AdjustResourcesAction(then)

        // TRADE
        case TradeMainAction(f, x, then) =>
            val resources = f.spendable.resources.%<!(x.as[PayResource]./(_.resource).has)
            val rtypes = resources.lefts./(_.resource).distinct

            Ask(f).group("Trade".hl)
                .some(f.rivals.%(_.stealable.resources.any).%(e => rtypes.exists(r => e.hasCountableResource(r).not))) { e =>
                    e.stealable.resources.%<(r => systems.exists(s => e.at(s).cities.any && game.resources(s).has(r.resource) && f.rules(s)))./ { case (r, k) =>
                        GiveBackResourceMainAction(f, x, e, r, then).as(e, r -> k, "for", f, rtypes.%(r => e.hasCountableResource(r).not).intersperse("or"))
                    }
                }
                .cancel

        case GiveBackResourceMainAction(f, x, e, take, then) =>
            Ask(f).group("Give", e, "for", take)
                .each(f.spendable.resources)((r, k) => TradeResourceAction(f, x, e, r, take, then).as("Give", r -> k).!(e.hasCountableResource(r.resource)))
                .cancel

        case TradeResourceAction(f, x, e, give, take, then) =>
            f.pay(x)

            f.take(take)

            e.take(give)

            f.log("took in trade", take, "from", e, x)

            e.log("got back", give)

            AdjustResourcesAction(then)

//]]


        // ...
        case _ => UnknownContinue
    }
}
