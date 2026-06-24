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


case object Steward extends Fate("Steward", "fate01", 1) {
    override val expansion = StewardExpansion
}


case object ConsolidateImperialPower extends Objective("f01-01b", "Consolidate Imperial Power")

case object ImperialAuthority extends Lore("f01-02", "Imperial Authority")
case object Dealmakers extends GuildEffect("Dealmakers", Psionic, 999)

case object TaxCollectors extends GuildEffect("Tax Collectors", Relic, 3)
case object HunterSquads extends GuildEffect("Hunter Squads", Weapon, 3)
case object ImperialProtectors extends Law("f01-08", "Imperial Protectors")
case object EmpireFalls extends VoxEffect("Empire Falls")
case object LesserRegent extends GuildEffect("Lesser Regent", Psionic, 2)

case class BargainInfluence(n : Int) extends Effect with Elementary {
    def elem : Elem = "Bargain".hl ~ " (" ~ n.hlb ~ " times)"
}
case object BargainSecure extends Effect with Elementary {
    def elem : Elem = "Bargain".hl
}


case object ExpandTheEmpire extends Objective("f01-12b", "Expand the Empire")

case object ImperialQuorum extends Edict("f01-13", "Imperial Quorum", "02")


case class BargainMainAction(self : Faction, cost : Cost, effect : |[Effect], skip : Boolean, cancel : Boolean, then : ForcedAction) extends ForcedAction with Soft
case class BargainAction(self : Faction, cost : Cost, effect : |[Effect], index : Int, e : Faction, then : ForcedAction) extends ForcedAction

case class LesserRegentMainAction(self : Faction, then : ForcedAction) extends ForcedAction with Soft


object StewardExpansion extends FateExpansion(Steward) {
    val deck = $(
        ImperialAuthority,
        GuildCard("f01-03", Dealmakers),
        VoxCard("f01-05", CouncilIntrigue),
        GuildCard("f01-06", TaxCollectors),
        GuildCard("f01-07", HunterSquads),
        VoxCard("f01-09", EmpireFalls),
        GuildCard("f01-10", LesserRegent),
        GuildCard("f01-11", LesserRegent),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // STEWARD I
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(ConsolidateImperialPower)

            f.progress = game.factions.num @@ {
                case 2 => 20
                case 3 => 17
                case 4 => 14
            }

            f.log("objective was set to", f.progress.hlb)

            f.primus = true
            f.recalculateSlots()

            f.log("became the", "First Regent".styled(Empire))

            FateDeck(fate) --> ImperialAuthority --> f.lores

            f.log("got", f.lores.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(Dealmakers)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        case BargainMainAction(f, x, effect, skip, cancel, then) =>
            Ask(f).group("Bargain".hl, effect./("with" -> _), x)
                .some(game.market) { m =>
                    val l = Influence(m.index).$

                    f.rivals.%(e => l.ofc(e).num > e.rivals./(l.ofc(_).num).max)./ { e =>
                        BargainAction(f, x, effect, m.index, e, then).as(m.$.intersperse(Comma))
                            .!(game.feudal.get(m.index).?(i => e.isLordIn(i).not && e.isVassalIn(i).not), "feudal court")
                    }.some.| {
                        $(Info(m.$.intersperse(Comma)))
                    }
                }
                .skipIf(skip)(then)
                .cancelIf(cancel)
                .needOk

        case BargainAction(f, x, effect, row, e, then) =>
            f.pay(x)

            val l = Market(row).$

            e.log("secured", l.first, x, effect./("with" -> _))

            l.drop(1).foreach { c =>
                e.log("also secured", c)
            }

            val next = Influence(row).$.ofc(e).indexed.foldLeft(MaySecureAction(f, |(BargainSecure), then) : ForcedAction)((q, _, i) => MayInfluenceAction(f, |(BargainInfluence(i + 1)), q))

            l.foldLeft(ReturnAgentsCourtCardAction(row, ReplenishMarketAction(next)) : ForcedAction)((q, c) => GainCourtCardAction(e, c, row, c == l.first, q))

        case FateFailAction(f, `fate`, 1, then) =>
            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(EmpireFalls)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(LesserRegent)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            f.loyal.%(_.as[GuildCard]./(_.effect).has(Dealmakers)).foreach { c =>
                c --> game.court

                f.log("added", c, "to the court deck")
            }

            Then(then)

        case FateDoneAction(f, `fate`, 1, then) =>
            game.laws :+= ImperialProtectors

            f.log("set", game.laws.last)

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(CouncilIntrigue)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(TaxCollectors)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(HunterSquads)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            Then(then)

        // STEWARD I LEGACY
        case GainCourtCardAction(f, c @ GuildCard(_, TaxCollectors), _, _, then) if f.regent.not =>
            (c : CourtCard) --> game.court

            f.log("buried", c, "as", "Outlaw".hh)

            then

        case LesserRegentMainAction(f, then) =>
            DeclareAmbitionMainAction(f, |(LesserRegent), game.ambitions, game.ambitionable.last, false, false, $(CancelAction), then)

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, EmpireFalls), then) =>
            log("Crisis", v)

            if (symbol == Arrow) {
                val next : ForcedAction = DiscardCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

                systems.foreach { s =>
                    val damage = Empire.at(s).ships.num

                    if (damage > 0) {
                        val empire = Empire.at(s).use(l => l.fresh ++ l)

                        if (empire.any) {
                            val incoming = empire.take(damage)

                            var result = Empire.damaged ++ incoming

                            val destroyed = result.diff(result.distinct)
                            val damaged = incoming.diff(destroyed).diff(destroyed)

                            result = result.diff(destroyed).diff(destroyed)

                            destroyed --> Empire.reserve

                            if (destroyed.any)
                                log(v, "destroyed", destroyed, "in", s)

                            if (damaged.any)
                                log(v, "damaged", damaged, "in", s)

                            Empire.damaged = result
                        }
                    }
                }

                val l = factions.%(_.regent).sortBy(_.primus).reverse

                l.foldLeft(next)((q, f) => BecomeOutlawAction(f, q))
            }
            else
                CourtCrisesContinueAction(cluster, symbol, lane, skip + 1, then)

        case GainCourtCardAction(f, v @ VoxCard(_, EmpireFalls), lane, main, then) =>
            val next = BuryVoxCardAction(f, v, then)

            if (main)
                Then(SpreadAgentsAction(f, lane, next))
            else
                Then(next)

        // STEWARD II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(ExpandTheEmpire)

            f.progress = 22

            f.log("objective was set to", f.progress.hlb)

            game.edicts :+= ImperialQuorum

            f.log("added", game.edicts.last, "edict")

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
