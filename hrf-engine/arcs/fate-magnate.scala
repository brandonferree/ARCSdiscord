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


case object Magnate extends Fate("Magnate", "fate03", 1) {
    override val expansion = MagnateExpansion
}


case object MerchantLeagueSlots extends ResourceSlot with Elementary {
    override val tradeable = true
    override val stealable = true
    def elem = "Merchant League".styled(styles.titleW)
}


case object CloseDeals extends Objective("f03-01b", "Close Deals with Rivals")

case object MerchantLeague extends GuildEffect("Merchant League", Fuel, 999) with FateCrisis
// case object MaterialCartel extends GuildEffect("Material Cartel", Material, 2)
// case object FuelCartel extends GuildEffect("Fuel Cartel", Fuel, 2)
case object WeaponCartel extends GuildEffect("Weapon Cartel", Weapon, 2)
case object RelicCartel extends GuildEffect("Relic Cartel", Relic, 2)
case object PsionicCartel extends GuildEffect("Psionic Cartel", Psionic, 2)


case class MerchantLeagueMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class MerchantLeagueAction(self : Faction, l : $[Resource], then : ForcedAction) extends ForcedAction

case class ExportMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft
case class ExportAction(self : Faction, l : $[ResourceToken], then : ForcedAction) extends ForcedAction


object MagnateExpansion extends FateExpansion(Magnate) {
    val deck = $(
        GuildCard("f03-02", MerchantLeague),
        GuildCard("f03-04", ElderBroker),
        GuildCard("f03-05", RelicFence),
        GuildCard("f03-06", PrisonWardens),
        GuildCard("f03-07", MaterialCartel),
        GuildCard("f03-08", FuelCartel),
        GuildCard("f03-09", WeaponCartel),
        GuildCard("f03-10", PsionicCartel),
        GuildCard("f03-11", RelicCartel),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // MAGNATE
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(CloseDeals)

            f.progress = 10

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(MerchantLeague)) --> f.loyal

            f.log("took", f.loyal.last)

            game.resources.register(MerchantLeagueSlots)

            Then(then)

        case MerchantLeagueMainAction(f, then) =>
            Ask(f).group(MerchantLeague, "gains resources")
                .some(Resources.all./~(a => Resources.all./(a :: _)) ++ Resources.all./(_ :: Nil))(l =>
                    l.distinct.forall(r => Supply(r).num >= l.count(r)).?(
                        MerchantLeagueAction(f, l, then).as(l./(r => ResourceRef(r, None)))
                    )
                )
                .cancel
                .needOk

        case MerchantLeagueAction(f, l, then) =>
            l.foreach { r =>
                Supply(r).take(1) --> MerchantLeagueSlots
            }

            f.log("gained", l./(r => ResourceRef(r, None)), "for", MerchantLeague)

            then

        case ExportMainAction(f, then) =>
            Ask(f).group("Export".hl)
                .each(f.spendable.resources.combinations(3).$)(l => ExportAction(f, l.lefts, then).as(l.intersperse(Comma)))
                .cancel

        case ExportAction(f, l, then) =>
            l.foreach { r =>
                r --> Supply(r.resource)
            }

            log(DottedLine)

            f.log("exported", l.intersperse(Comma))

            if (f.objective.has(CloseDeals))
                f.advance(2, $("exporting"))

            log(DottedLine)

            NegotiateCleanUpAction(then)

        case FateCrisisAction(f, MerchantLeague, cluster, symbol, then) =>
            val l = MerchantLeagueSlots.$

            l.foreach {
                case r : ResourceToken => r --> r.supply
            }

            if (l.any)
                f.log("discarded", l.intersperse(Comma), "from", MerchantLeague)

            then

        case FateFailAction(f, `fate`, 1, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(MaterialCartel)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(FuelCartel)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(WeaponCartel)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(RelicCartel)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PsionicCartel)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            Then(then)


        case FateDoneAction(f, `fate`, 1, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(ElderBroker)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(RelicFence)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PrisonWardens)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            CourtDiscard.%(_.as[GuildCard]./(_.effect).has(MiningInterest)).foreach { c =>
                c --> game.court

                f.log("returned", c, "to the court deck")
            }

            CourtDiscard.%(_.as[GuildCard]./(_.effect).has(ShippingInterest)).foreach { c =>
                c --> game.court

                f.log("returned", c, "to the court deck")
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
