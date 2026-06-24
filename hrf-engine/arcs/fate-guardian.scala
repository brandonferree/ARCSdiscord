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


case object Guardian extends Fate("Guardian", "fate20", 3) {
    override val expansion = GuardianExpansion
}


case object GreenVaultSlots extends ResourceSlot with Elementary {
    override def canHold(t : ResourceLike)(implicit game : Game) = t.is(Fuel) || t.is(Material)
    def elem = GreenVault.elem
}


case object GiveBackToTheLand extends Objective("f20-01b", "Give Back to the Land")

case object Edenguard extends Ambition { val strength = 2 }

case object GreenVault extends Lore("f20-02", "Green Vault")
case object IreOfTheTycoons extends Lore("f20-03", "Ire of the Tycoons")
case object EdenguardAmbition extends Law("f20-04", "EdenguardAmbition")


case class GreenVaultMainAction(self : Faction, then : ForcedAction) extends ForcedAction
case class GreenVaultAction(self : Faction, l : $[ResourceLike], then : ForcedAction) extends ForcedAction


object GuardianExpansion extends FateExpansion(Guardian) {
    val deck = $(
        GreenVault,
        IreOfTheTycoons,
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // GUARDIAN III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(GiveBackToTheLand)

            f.progress = 15

            f.log("objective was set to", f.progress.hlb)

            FateDeck(fate) --> GreenVault --> f.lores

            f.log("got", f.lores.last)

            game.resources.register(GreenVaultSlots)

            FateDeck(fate) --> IreOfTheTycoons --> f.lores

            f.log("got", f.lores.last)

            game.laws :+= EdenguardAmbition

            f.log("set", game.laws.last)

            game.ambitions = game.ambitions./~ {
                case Tycoon => $(Tycoon, Edenguard)
                case a => $(a)
            }

            OutrageAction(f, Material, |(IreOfTheTycoons), OutrageAction(f, Fuel, |(IreOfTheTycoons), then))

        case AdjustResourcesAction(then) if factions.%(_.taken.exists(r => r.is(Material) || r.is(Fuel))).%(_.hasLore(GreenVault)).any =>
            GreenVaultMainAction(factions.%(_.taken.exists(r => r.is(Material) || r.is(Fuel))).%(_.hasLore(GreenVault)).first, then)

        case GreenVaultMainAction(f, then) =>
            val lm = f.taken.%(_.is(Material))
            val lf = f.taken.%(_.is(Fuel))

            Ask(f).group(GreenVault, "take")
                .each(0.to(lm.num)./~(i => 0.to(lf.num)./~(j => (i + j > 0).?(lm.take(i) ++ lf.take(j)))).reverse)(l => GreenVaultAction(f, l, then).as(l./(_.token)))
                .skip(GreenVaultAction(f, $, then))

        case GreenVaultAction(f, l, then) =>
            if (l.any) {
                l --> GreenVaultSlots

                f.log("placed", l.intersperse(Comma), "in", GreenVault)
            }

            f.taken = f.taken.%!(_.is(Material)).%!(_.is(Fuel))

            AdjustResourcesAction(then)


        // ...
        case _ => UnknownContinue
    }
}
