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


case object Admiral extends Fate("Admiral", "fate07", 1) {
    override val expansion = AdmiralExpansion
}


case object ProveYourself extends Objective("f07-01b", "Prove Yourself")

case object UseImperialFoundries extends Edict("f07-03", "Use Imperial Foundries", "55")
case object ImperialOfficers extends GuildEffect("Imperial Officers", Weapon, 999)
case object HonorGuard extends GuildEffect("Honor Guard", Weapon, 999)
case object RogueAdmirals extends GuildEffect("Rogue Admirals", Weapon, 2)
case object ImperialDefectors extends VoxEffect("Imperial Defectors")


case class UseImperialFoundriesMainAction(self : Faction, l : $[System], then : ForcedAction) extends ForcedAction with Soft
case class UseImperialFoundriesAction(self : Faction, u : Figure, s : System, l : $[System], then : ForcedAction) extends ForcedAction

case class ImperialDefectorsMainAction(cluster : Int, then : ForcedAction) extends ForcedAction with Soft
case class ImperialDefectorsAction(self : Faction, e : Faction, l : $[Figure], then : ForcedAction) extends ForcedAction

case class BuryOrDiscardVoxCardAction(self : Faction, v : VoxCard, then : ForcedAction) extends ForcedAction with Soft

case class ImperialShipsInSystemMainAction(self : Faction, l : $[System], then : ForcedAction) extends ForcedAction with Soft
case class ImperialShipsInSystemsAction(self : Faction, l : $[System], then : ForcedAction) extends ForcedAction


object AdmiralExpansion extends FateExpansion(Admiral) {
    val deck = $(
        GuildCard("f07-02", ImperialOfficers),
        GuildCard("f07-05", HonorGuard),
        GuildCard("f07-06", RogueAdmirals),
        GuildCard("f07-07", RogueAdmirals),
        GuildCard("f07-08", CourtEnforcers),
        GuildCard("f07-09", CourtEnforcers),
        VoxCard("f07-10", ImperialDefectors),
        VoxCard("f07-11", ImperialDefectors),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // ADMIRAL I
        case FateInitAction(f, `fate`, 1, then) =>
            f.objective = |(ProveYourself)

            f.progress = 13

            f.log("objective was set to", f.progress.hlb)

            if (factions.%(_.fates.has(Steward)).none) {
                f.primus = true
                f.recalculateSlots()

                f.log("became", "the First Regent".styled(Empire))
            }

            game.edicts :+= UseImperialFoundries

            f.log("added", game.edicts.last, "edict")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(ImperialOfficers)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        case ResolveEdictAction(priority, then) if priority == UseImperialFoundries.priority =>
            val next = ResolveNextEdictAction(priority, then)

            val officer = factions.%(_.hasGuild(ImperialOfficers)).single

            if (officer.any) {
                val f = officer.get
                val l = systems./~(s => f.at(s).starports./(_ => s)) ++ f.flagship./~(s => Flagship.scheme(f).%(_.$.starports.any).num.times(s.system))

                f.log("could", UseImperialFoundries)

                UseImperialFoundriesMainAction(f, l, next)
            }
            else
                next

        case UseImperialFoundriesMainAction(f, ss, then) =>
            Ask(f).group(UseImperialFoundries)
                .some(ss.distinct) { s =>
                    val l = f.at(s).ships
                    l./(u => UseImperialFoundriesAction(f, u, s, ss :- s, then).as(u, game.showFigure(u))(s).!(Empire.reserve.$.ships.none))
                }
                .done(then)
                .needOk

        case UseImperialFoundriesAction(f, u, s, ss, then) =>
            val damaged = u.faction.damaged.has(u)

            val n = Empire.reserve.$.ships.first

            n --> s

            u --> u.faction.reserve

            if (damaged)
                n.faction.damaged :+= n

            f.log("replaced", u, "with", n, "in", s)

            if (damaged)
                u.faction.damaged :-= u

            factions.%(_.objective.has(ProveYourself)).foreach { f =>
                if (game.declared.keys.exists(a => game.declared(a).any && f.ambitionValue(a) > factions.but(f)./(_.ambitionValue(a)).max))
                    f.advance(1, $("for imperial ship placed"))
            }

            UseImperialFoundriesMainAction(f, ss, then)

        case FateFailAction(f, `fate`, 1, then) =>
            game.edicts :-= UseImperialFoundries

            f.log("removed", UseImperialFoundries, "edict")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(CourtEnforcers)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(ImperialDefectors)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        case FateDoneAction(f, `fate`, 1, then) =>
            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(HonorGuard)) --> f.loyal

            f.log("took", f.loyal.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(RogueAdmirals)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(CourtEnforcers)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        // ADMIRAL I LEGACY
        case GainCourtCardAction(f, c @ GuildCard(_, RogueAdmirals), _, _, then) if f.regent.not =>
            (c : CourtCard) --> game.court

            f.log("buried", c, "as", "Outlaw".hh)

            then

        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, ImperialDefectors), then) =>
            val next : ForcedAction = BuryCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            ImperialDefectorsMainAction(cluster, next)

        case GainCourtCardAction(f, v @ VoxCard(_, ImperialDefectors), lane, main, then) =>
            val e = factions.dropWhile(_.regent).starting

            if (e.none)
                Ask(f).group(ImperialDefectors, "can't defect")
                    .done(BuryOrDiscardVoxCardAction(f, v, then))
                    .needOk
            else
                Ask(f).group(ImperialDefectors, "defect to", e, "in")
                    .each(1.to(6).$)(i => ImperialDefectorsMainAction(i, BuryVoxCardAction(f, v, then)).as(systems.%(_.cluster == i).intersperse(Comma)))
                    .skip(BuryOrDiscardVoxCardAction(f, v, then))

        case BuryOrDiscardVoxCardAction(f, v, then) =>
            Ask(f).group(v)
                .add(BuryVoxCardAction(f, v, then).as("Bury", v))
                .add(DiscardVoxCardAction(f, v, then).as("Discard", v))

        case ImperialDefectorsMainAction(n, then) =>
            factions.dropWhile(_.regent).starting./ { e =>
                val f = game.current.|(e)

                val l = systems.%(_.cluster == n)./~(s => Empire.at(s).ships./(_ -> s))

                if (e.pool(Ship).not)
                    Then(then)
                else
                if (l.num < e.pooled(Ship))
                    Then(ImperialDefectorsAction(game.current.|(f), e, l.lefts, then))
                else
                    Ask(game.current.|(f)).group(ImperialDefectors, "defect to", e)
                        .each(l)((u, s) => ImperialDefectorsAction(game.current.|(f), e, $(u), ImperialDefectorsMainAction(n, then)).as(u, game.showFigure(u), "in", s))
            }.|(Then(then))

        case ImperialDefectorsAction(f, e, l, then) =>
            l.foreach { u =>
                val s = u.system
                val damaged = u.damaged

                u --> Empire.reserve

                val n = e.reserve.$.ships.first

                n --> s

                if (damaged) {
                    Empire.damaged :-= u
                    e.damaged :+= n
                }

                f.log("replaced", Ship.of(Empire, damaged), "with", Ship.of(e, damaged), "in", s)
            }

            then

        case ImperialShipsInSystemMainAction(f, l, then) =>
            Ask(f).group("Place", 3.hi(Empire.pooled(Ship)).hlb, Ship.sof(f), "in")
                .each(l)(s => ImperialShipsInSystemsAction(f, $(s, s, s), then).as(s))
                .needOk
                .cancel

        case ImperialShipsInSystemsAction(f, ss, then) =>
            ss.distinct.foreach { s =>
                val l = Empire.reserve.$.ships.take(ss.count(s))

                l --> s

                f.log("placed", l.comma, "in", s)
            }

            then


        // ...
        case _ => UnknownContinue
    }
}
