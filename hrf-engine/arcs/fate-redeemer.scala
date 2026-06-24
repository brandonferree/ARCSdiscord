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


case object Redeemer extends Fate("Redeemer", "fate19", 3) {
    override val expansion = RedeemerExpansion
}


case object ShatteredRelics extends ResourceSlot


case object SubdueTheKeepers extends Objective("f19-01b", "Subdue the Keepers")

case object IreOfTheKeepers extends Lore("f19-02", "Ire of the Keepers")
case object RebukeOfTheKeepers extends Edict("f19-03", "Rebuke of the Keepers", "70")


case class ShatterRelicAction(self : Faction, x : ResourceToken, then : ForcedAction) extends ForcedAction


object RedeemerExpansion extends FateExpansion(Redeemer) {
    val deck = $(
        IreOfTheKeepers,
        GuildCard("f19-04", RelicFence),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // REDEEMER III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(SubdueTheKeepers)

            f.progress = game.factions.num @@ {
                case 2 => 16
                case 3 => 14
                case 4 => 12
            }

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate) --> IreOfTheKeepers --> f.lores

            f.log("got", f.lores.last)

            game.edicts :+= RebukeOfTheKeepers

            f.log("added", game.edicts.last, "edict")

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(RelicFence)) ++ game.court.$ --> game.court

            f.log("placed", game.court.first, "on top of the court deck")

            game.resources.register(ShatteredRelics)

            OutrageAction(f, Relic, |(IreOfTheKeepers), then)

        case ResolveEdictAction(priority, then) if priority == RebukeOfTheKeepers.priority =>
            val next = ResolveNextEdictAction(priority, then)

            if (game.declared.contains(Keeper)) {
                val f = factions.%(_.objective.has(SubdueTheKeepers)).only

                val n = game.declared(Keeper)./(_.high).sum

                if (n > 0) {
                    f.power -= n

                    f.log("lost", n.power, "from", RebukeOfTheKeepers)
                }

                next
            }
            else {
                val f = factions.%(f => f.countableResources(Relic) > f.rivals./(_.countableResources(Relic)).max).single

                if (f.any && game.ambitionable.any)
                    DeclareAmbitionMainAction(f.get, |(RebukeOfTheKeepers), $(Keeper), game.ambitionable.last, false, false, $(SkipAction(next)), next)
                else
                    next
            }

        case CheckWinAction if factions.exists(_.used.has(SubdueTheKeepers)) =>
            val f = factions.%(_.used.has(SubdueTheKeepers)).only

            Ask(f).group("Shatter", Relic)
                .some(f.countable.resources)((r, k) =>
                    r.is(Relic).$(ShatterRelicAction(f, r, CheckWinAction).as("Shatter", r -> k))
                )
                .done(ClearEffectAction(f, SubdueTheKeepers, CheckWinAction))

        case ShatterRelicAction(f, x, then) =>
            x --> ShatteredRelics

            f.log("shattered", x)

            f.advance(2, $("shattering"))

            then


        // ...
        case _ => UnknownContinue
    }
}
