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


case object Pacifist extends Fate("Pacifist", "fate14", 2) {
    override val expansion = PacifistExpansion
}


case object WellOfEmpathySlots extends ResourceSlot with Elementary {
    override def capacity(f : Faction)(implicit game : Game) = f.adjustable.content.count(_.is(Psionic))
    override def canHold(t : ResourceLike)(implicit game : Game) = t.is(Psionic)
    override val raidable = |(2)
    def elem = WellOfEmpathy.elem
}

case object Witness extends Piece with Effect with Elementary {
    def elem = "Witness".hh
}

case object WitnessPsionicSupply extends ResourceSlot


case object CommitToPacifism extends Objective("f14-01b", "Commit to Pacifism")

case object Witnesses extends Ability("f14-02", "Witnesses")
case object WellOfEmpathy extends Lore("f14-03", "Well of Empathy")
case object ItinerantEmpaths extends VoxEffect("Itinerant Empaths")


case object DemonstrateNonviolence extends Objective("f14-08b", "Demonstrate Nonviolence")

case object EmpathyForAll extends Law("f14-09", "Empathy for All")
case object ReconcileWithTheOutraged extends Law("f14-11", "Reconcile with the Outraged")
case object RebukeOfTheEmpaths extends Edict("f14-12", "Rebuke of the Empaths", "60")


case class WitnessAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction

case class ItinerantEmpathsAction(self : Faction, then : ForcedAction) extends ForcedAction


object PacifistExpansion extends FateExpansion(Pacifist) {
    val deck = $(
        WellOfEmpathy,
        GuildCard("f14-05", PsionicLiaisons),
        VoxCard("f14-06", ItinerantEmpaths),
        VoxCard("f14-07", ItinerantEmpaths),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // PACIFIST II
        case FateInitAction(f, `fate`, 2, then) =>
            f.objective = |(CommitToPacifism)

            f.progress = game.factions.num @@ {
                case 2 => 16
                case 3 => 14
                case 4 => 12
            }

            f.log("objective was set to", f.progress.hlb)

            f.abilities :+= Witnesses

            f.log("gained", f.abilities.last)

            FateDeck(fate) --> WellOfEmpathy --> f.lores

            f.log("got", f.lores.last)

            game.resources.register(WellOfEmpathySlots)

            f.recalculateSlots()

            val units = game.figures.register(FatePieces(fate), content = 1.to(4)./(Figure(f, Witness, _)))

            systems.%!(_.gate).sortBy(s => game.resources(s).has(Weapon).??(99) + s.$.shiplikes.num).reverse.take(4).foreach { s =>
                units.first --> s

                log(Witness.of(f), "was placed in", s)
            }

            val resources = game.resources.register(WitnessPsionicSupply, content = 6.to(9)./(i => ResourceToken(Psionic, i)))

            ClearOutrageAction(f, f.outraged, ReplenishShipsMainAction(f, 8, then))

        case WitnessAction(f, s, then) =>
            f.at(s).piece(Witness).first --> FatePieces(fate)

            WitnessPsionicSupply.first --> Psionic.supply

            f.gain(Psionic, $("from", Witness.of(f), "in", s))

            val next =
                if (game.ambitionable.any)
                    DeclareAmbitionMainAction(f, |(Witness), $(Empath), game.ambitionable.last, false, false, $(SkipAction(then)), then)
                else
                    then

            AdjustResourcesAction(next)

        case FateFailAction(f, `fate`, 2, then) =>
            f.abilities :-= Witnesses

            f.log("lost", Witnesses)

            systems.foreach { s =>
                f.at(s).piece(Witness) --> FatePieces(fate)
            }

            WitnessPsionicSupply.$.some / { l =>
                f.log("scrapped", "Witnesses".styled(f))
            }

            Then(then)

        case FateDoneAction(f, `fate`, 2, then) =>
            f.abilities :-= Witnesses

            f.log("lost", Witnesses)

            systems.foreach { s =>
                f.at(s).piece(Witness) --> FatePieces(fate)
            }

            WitnessPsionicSupply.$.some / { l =>
                l --> Supply(Psionic)

                f.log("added", "Witnesses".styled(f), "to", Psionic, "supply")
            }

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(PsionicLiaisons)) --> game.court

            f.log("added", game.court.last, "to the court deck")

            FateDeck(fate).%(_.as[VoxCard]./(_.effect).has(ItinerantEmpaths)) --> game.court

            f.log("added", game.court.last, "to the court deck")
            f.log("added", game.court.last, "to the court deck")

            Then(then)

        // PACIFIST II LEGACY
        case CourtCrisesPerformAction(cluster, symbol, lane, skip, main, v @ VoxCard(_, ItinerantEmpaths), then) =>
            val next : ForcedAction = DiscardCrisisVoxCardAction(v, lane, CourtCrisesContinueAction(cluster, symbol, lane, skip + main.??(1), then))

            log("Crisis", v)

            val f = factions.%(f => f.power <= f.rivals./(_.power).min).first

            ItinerantEmpathsAction(f, next)

        case GainCourtCardAction(f, v @ VoxCard(_, ItinerantEmpaths), lane, main, then) =>
            val next = DiscardVoxCardAction(f, v, then)

            ItinerantEmpathsAction(f, next)

        case ItinerantEmpathsAction(f, then) =>
            var n = 0

            while (f.spendable.%(s => s.canHold(Psionic) && s.canHoldMore).any && game.available(Psionic)) {
                f.gain(Psionic)

                n += 1
            }

            if (n > 0)
                f.log("gained", n.times(Psionic.token))

            AdjustResourcesAction(then)

        // PACIFIST III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(DemonstrateNonviolence)

            f.progress = 0

            game.laws :+= EmpathyForAll

            f.log("set", game.laws.last)

            game.laws :+= ReconcileWithTheOutraged

            f.log("set", game.laws.last)

            game.edicts :+= RebukeOfTheEmpaths

            f.log("added", game.edicts.last, "edict")

            Then(then)

        case ResolveEdictAction(priority, then) if priority == RebukeOfTheEmpaths.priority =>
            val next = ResolveNextEdictAction(priority, then)

            if (game.declared.contains(Empath)) {
                factions.foreach { f =>
                    val l = f.outraged./~(r => f.spendable.resources.lefts.%(_.is(r)))

                    if (l.any) {
                        l.foreach { r => r --> r.supply }

                        f.log("discarded", l)
                    }
                }

                factions.foreach { f =>
                    if (f.outraged.has(Psionic).not)
                        f.gain(Psionic, $)
                }
            }

            AdjustResourcesAction(next)

        case HarmAction(f, e, s, then) if game.laws.has(EmpathyForAll) && s.gate.not =>
            Then(game.resources(s).%(f.hasCountableResource).foldLeft(then)((q, r) => OutrageAction(f, r, |(EmpathyForAll), q)))

        case CheckGrandAmbitionsAction(f, then) if f.fates.has(fate) =>
            if (f.trophies.none && f.captives.none) {
                f.grand += 1

                f.log("fulfilled a grand ambition having no", "trophies".hh, "or", "captives".hh)
            }

            if (f.rivals./(_.countableResources(Psionic)).sum > f.countableResources(Psionic)) {
                f.grand += 1

                f.log("fulfilled a grand ambition giving out", Psionic)
            }

            Then(then)


        // ...
        case _ => UnknownContinue
    }
}
