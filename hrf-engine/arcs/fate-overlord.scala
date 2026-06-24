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


case object Overlord extends Fate("Overlord", "fate17", 3) {
    override val expansion = OverlordExpansion
}


case object RuleByFearAlone extends Objective("f17-01b", "Rule by Fear Alone")

case object ReachRejectsOverlord extends Edict("f17-03", "Reach Rejects Overlord", "65")


object OverlordExpansion extends FateExpansion(Overlord) {
    val deck = $(
        GuildCard("f17-02", Sycophants),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // OVERLORD III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(RuleByFearAlone)

            f.progress = 20

            f.log("objective was set to", f.progress.hlb)

            game.edicts :+= ReachRejectsOverlord

            f.log("added", game.edicts.last, "edict")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(Sycophants)) --> f.loyal

            f.log("took", f.loyal.last)

            if (f.outraged.none)
                Ask(f).group("Outrage a resource")
                    .each(Resources.all)(r => OutrageAction(f, r, None, then).as(r))
                    .skip(then)
            else
                Then(then)

        case ResolveEdictAction(priority, then) if priority == ReachRejectsOverlord.priority =>
            val next = ResolveNextEdictAction(priority, then)

            val f = factions.%(_.objective.has(RuleByFearAlone)).only

            val n = f.outraged.num @@ {
                case 0 => 0
                case 1 => 1
                case 2 => 3
                case 3 => 6
                case 4 => 8
                case 5 => 10
            }

            if (n > 0) {
                f.power -= n

                f.log("lost", n.power, "from", ReachRejectsOverlord)
            }

            next


        // ...
        case _ => UnknownContinue
    }
}
