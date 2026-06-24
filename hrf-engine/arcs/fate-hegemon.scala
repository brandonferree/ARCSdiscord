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


case object Hegemon extends Fate("Hegemon", "fate10", 2) {
    override val expansion = HegemonExpansion
}


case object Banner extends Piece


case object PlantBanners extends Objective("f10-01b", "Plant Banners")

case object PlantingBanners extends Ability("f10-02", "Planting Banners")
case object BannersOfHegemony extends Law("f10-03", "Banners of Hegemony")
case object SongOfTheBanner extends Lore("f10-04", "Song of the Banner")
case object CallSpirits extends Effect
case object CallBodies extends Effect
case object MindManagers extends GuildEffect("Mind Managers", Psionic, 999)
case object AgainstHegemony extends VoxEffect("Against Hegemony")


case object FormOneBodyOneMind extends Objective("f10-11b", "Form One Body, One Mind")

case object HappyHosts extends GuildEffect("Happy Hosts", Weapon, 999)


case class HegemonReplenishMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction
case class HegemonReplenishAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction

case class PlantBannerAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction

case class CallSpiritsMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class CallSpiritsAction(self : Faction, x : Cost, s : System, l : $[Resource], then : ForcedAction) extends ForcedAction

case class CallBodiesMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class CallBodiesAction(self : Faction, x : Cost, s : System, then : ForcedAction) extends ForcedAction

case class AgainstHegemonyMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class AgainstHegemonyAction(self : Faction, s : System, u : Figure, then : ForcedAction) extends ForcedAction

case class ManipulateMainAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction with Soft
case class ManipulateFactionAction(self : Faction, x : Cost, e : Faction, then : ForcedAction) extends ForcedAction
case class ManipulateAction(self : Faction, e : Faction, d : DeckCard, then : ForcedAction) extends ForcedAction
case class ManipulateBackAction(self : Faction, e : Faction, d : DeckCard, then : ForcedAction) extends ForcedAction

case class PacifyAction(self : Faction, l : $[Figure], x : Cost, then : ForcedAction) extends ForcedAction


object HegemonExpansion extends FateExpansion(Hegemon) {
    val deck = $(
        SongOfTheBanner,
        GuildCard("f10-06", MindManagers),
        GuildCard("f10-07", PrisonWardens),
        GuildCard("f10-08", PrisonWardens),
        VoxCard("f10-09", AgainstHegemony),
        VoxCard("f10-10", AgainstHegemony),
        GuildCard("f10-12", HappyHosts),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // HEGEMON II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(PlantBanners)

            f.progress = 14

            f.log("objective was set to", f.progress.hlb)

            f.abilities :+= PlantingBanners

            f.log("gained", f.abilities.last)

            game.laws :+= BannersOfHegemony

            f.log("set", game.laws.last)

            FateDeck(fate) --> SongOfTheBanner --> f.lores

            f.log("got", f.lores.last)

            val units = game.figures.register(FatePieces(fate), content = 1.to(8)./(Figure(f, Banner, _)))

            units --> f.reserve

            f.log("took", Banner.sof(f))

            SetupFlagshipMainAction(f, $(|(Starport), None, None, |(Starport), None, None), HegemonReplenishMainAction(f, 6, then))

        case HegemonReplenishMainAction(f, n, then) =>
            val d = (n - systems./(f.at(_).ships.num).sum)

            if (d > 0)
                systems.foreach { s =>
                    if (f.at(s).flagship.any) {
                        val l = f.reserve.$.ships.take(d)

                        l --> s

                        f.log("replenished fleet with", l.intersperse(Comma))
                    }
                }

            ScrapShipsMainAction(f, 4, then)

        case PlantBannerAction(f, s, then) =>
            f.reserve --> Banner --> s

            f.log("planeted", Banner.of(f), "in", s)

            then

        case CallSpiritsMainAction(f, x, then) =>
            Ask(f).group("Call Spirits".hlb)
                .some(systems.%(_.$.hasA(Banner)))(s =>
                    game.resources(s)./(r => game.availableNum(r).upTo(2).times(r))./(l => CallSpiritsAction(f, x, s, l, then).as(l, "from", s).!(l.none))
                )
                .cancel

        case CallSpiritsAction(f, x, s, l, then) =>
            f.pay(x)

            f.used :+= CallSpirits

            l.foreach(f.gain(_, $("calling spirits in", s)))

            AdjustResourcesAction(then)

        case CallBodiesMainAction(f, x, then) =>
            Ask(f).group("Call Bodies".hlb)
                .each(systems.%(_.$.hasA(Banner)))(s =>
                    CallBodiesAction(f, x, s, then).as(s).!(f.pool(Ship).not, "no ships")
                )
                .cancel

        case CallBodiesAction(f, x, s, then) =>
            f.pay(x)

            f.used :+= CallBodies

            val l = f.reserve.$.ships.take(2)

            l --> s

            f.log("called bodies", l.intersperse(Comma), "in", s)

            then

        case FateFailAction(f, `fate`, 2, then) =>
            val l = f.reserve.$.piece(Banner)

            if (l.any) {
                l --> Scrap

                f.log("scrapped all", Banner.sof(f), "from supply")
            }

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PrisonWardens)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(AgainstHegemony)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(MindManagers)) --> f.loyal

            f.log("took", f.loyal.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PrisonWardens)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        // HEGEMON II LEGACY
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, AgainstHegemony), then) =>
            log("Crisis", v)

            BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

        case GainCourtCardAction(f, v @ VoxCard(_, AgainstHegemony), lane, main, then) =>
            AgainstHegemonyMainAction(f, then)

        case AgainstHegemonyMainAction(f, then) =>
            Ask(f).group(AgainstHegemony)
                .some(systems)(s => s.$.banners./(u => AgainstHegemonyAction(f, s, u, then).as(u, game.showFigure(u), "in", s)))
                .done(then)

        case AgainstHegemonyAction(f, s, u, then) =>
            u --> f.trophies

            f.log("took", u, "in", s, "as trophy")

            if (u.damaged)
                u.faction.damaged :-= u

            AgainstHegemonyMainAction(f, then)

        case ManipulateMainAction(f, x, then) =>
            Ask(f).group("Manipulate")
                .each(f.rivals.%(_.hand.any).%(e => f.captives.$.ofc(e).any))(e => ManipulateFactionAction(f, x, e, then).as(e))
                .cancel

        case ManipulateFactionAction(f, x, e, then) =>
            f.pay(x)

            f.log("manipulated", e)

            YYSelectObjectsAction(f, e.hand)
                .withGroup(MindManagers, "look at", e, "cards")
                .withThen(d => ManipulateAction(f, e, d, then))(d => ("Take", d, "from", e))("Take a card from", e)
                .withExtras(then.as("Skip"))

        case ManipulateAction(f, e, d, then) =>
            e.hand --> d --> f.hand

            f.log("took a card from", e, "with", MindManagers)

            YYSelectObjectsAction(f, f.hand)
                .withGroup(MindManagers, "give back", e, "a card")
                .withThen(d => ManipulateBackAction(f, e, d, then))(d => ("Give", d, "to", e))("Give a card back to", e)
                .withExtras(NoHand)

        case ManipulateBackAction(f, e, d, then) =>
            f.hand --> d --> e.hand

            f.log("gave back a card to", e)

            then

        // HEGEMON III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(FormOneBodyOneMind)

            f.progress = 0

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(HappyHosts)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        case PacifyAction(f, l, x, then) =>
            f.pay(x)

            f.log("captured", l.comma)

            l.foreach { u =>
                u.faction.damaged :-= u
            }

            l --> f.captives

            then

        case CleanUpChapterAction(then)
        if game.declared.contains(Tyrant) && factions.%(_.hasGuild(HappyHosts)).exists { f =>
            val n = systems.%(f.at(_).banners.any).%(s => factions.but(f).exists(_.rulesAOE(s)).not).num
            f.captives.num > n && n > 0
        } =>
            val (f, n) = factions.%(_.hasGuild(HappyHosts))./~ { f =>
                val n = systems.%(f.at(_).banners.any).%(s => factions.but(f).exists(_.rulesAOE(s)).not).num
                if (f.captives.num > n && n > 0)
                    Some((f, n))
                else
                    None
            }.first

            implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, selected.??(2))

            XXSelectObjectsAction(f, f.captives)
                .withGroup("Keep captives with", HappyHosts)
                .withRule(_.num(n))
                .withThen(l => ReturnCaptivesAction(f, f.captives.$.diff(l), CleanUpChapterAction(then)))(l => ("Keep", l.intersperse(Comma)))

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            if (game.winners.get(Tyrant).?(_ != f).not) {
                f.grand += 1

                f.log("fulfilled a grand ambition denying", Tyrant)
            }

            if (systems./(_.$.banners.num).sum > game.chapter) {
                f.grand += 1

                f.log("fulfilled a grand ambition spreading", "Banners".hh)
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
