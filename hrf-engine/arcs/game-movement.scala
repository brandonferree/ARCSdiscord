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


case class MayMoveAction(self : Faction, effect : |[Effect], then : ForcedAction) extends ForcedAction with ThenDesc { def desc = "(then may " ~ "Move".hh ~ ")" }

case class MoveMainAction(self : Faction, cost : Cost, effect : |[Effect], skip : Boolean, cancel : Boolean, then : ForcedAction) extends ForcedAction with Soft
case class MoveFromAction(self : Faction, s : System, l : $[Figure], cascade : Boolean, x : Cost, effect : |[Effect], alt : UserAction, then : ForcedAction) extends ForcedAction with Soft
case class MoveToAction(self : Faction, s : System, d : System, l : $[Figure], cascade : Boolean, x : Cost, effect : |[Effect], then : ForcedAction) extends ForcedAction with Soft
case class MoveListAction(self : Faction, s : System, d : System, l : $[Figure], cascade : Boolean, x : Cost, effect : |[Effect], then : ForcedAction) extends ForcedAction


object MovementExpansion extends Expansion {
    def movable(f : Faction, s : System, officers : Boolean, blight : Boolean)(implicit game : Game) : $[Figure] = {
        val l = f.at(s)
        val ships = l.shiplikes
        val witnesses = l.piece(Witness).some./~(w => f.rules(s).??(w))
        val blights = blight.??(Blights.at(s).some./~(l => f.rules(s).?(l)).|($))
        val empire = f.regent.??((ships.any || officers).??(Empire.at(s).ships))
        val chosen = f.abilities.has(JudgesChosen).?(f.rivals.%(_.fates.has(Judge)).only)./~(_.at(s).shiplikes)
        ships ++ empire ++ chosen ++ witnesses ++ blights
    }

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // MOVE
        case MayMoveAction(f, effect, then) =>
            MoveMainAction(f, NoCost, effect, true, false, then)

        case MoveMainAction(f, x, effect, skip, cancel, then) =>
            val regent = campaign && f.regent
            val officers = regent && f.officers
            val blight = campaign && (f.hasLore(BlightFury) || f.hasLore(BlightSociety))

            var pp = systems.%(f.at(_).hasA(Starport))
            var ss = systems./(s => s -> movable(f, s, officers, blight)).%>(_.any)

            if (f.hasLore(EmpathsBond) && game.declared.contains(Empath))
                pp ++= systems.%(s => game.colors.but(f).exists(_.at(s).starports.any))
            else
            if (f.hasLore(CatapultOverdrive) || f.hasLore(CatapultOverdriveLL))
                pp ++= systems.%(s => game.colors.but(f).exists(_.at(s).starports.any)).%(f.rules)
            else
            if (campaign)
                pp ++= systems.%(s => Free.at(s).hasA(Starport)).%(f.rules)

            if (f.abilities.has(BreakingGates))
                pp = pp.%(_.cluster.in(game.broken))

            if (f.hasTrait(Ancient))
                pp = systems.%(_.gate)

            Ask(f).group("Move".hh, x, effect./("with" -> _), "from")
                .each(ss)((s, l) => MoveFromAction(f, s, l, pp.has(s), x, effect, CancelAction, then).as(pp.has(s).?(Image("starport-empty", styles.token)), s, pp.has(s).?(Image("starport-empty", styles.token))))
                .skipIf(skip)(then)
                .cancelIf(cancel)
                .needOk

        case MoveFromAction(f, s, l, cascade, x, effect, alt, then) =>
            var destinations = systems.intersect(game.connected(s))

            if (f.hasLore(SongOfTheBanner) && s.$.hasA(Banner))
                destinations ++= systems.but(s).%(_.$.hasA(Banner)).diff(destinations)

            if (effect.has(PassageStorms))
                destinations = systems.%!(_.gate)

            Ask(f).group("Move".hh, effect./("with" -> _), "from", s, "to")
                .each(destinations)(d => {
                    val cat = (cascade
                        && (d.gate || f.hasLore(CatapultOverdriveLL))
                        && f.rivals.exists(e => e.rules(d) && campaign.??(e.regent && f.regent && l.ofc(Empire).any).not).not
                        && campaign.??(Blights.at(d).any && f.hasGuild(SporeShips).not).not
                        && campaign.??(f.regent.not && Empire.rules(d)).not
                        && campaign.??(l.forall(_.piece == Witness)).not
                        && campaign.??(l.forall(_.piece == Blight)).not
                    ) || campaign.??((cascade && f.hasLore(CatapultOverdrive) && d == GateWraithExpansion.Passage))

                    MoveToAction(f, s, d, l, cat, x, effect, then).as(d, cat.?("and further"))
                })
                .add(alt)

        case MoveToAction(f, s, d, l, cascade, x, effect, then) =>
            val combinations = l.groupBy(u => (u.faction, u.piece)).$.sortBy(_._1._2 @@ {
                case Flagship => 1
                case Ship => 2
                case Witness => 3
                case Blight => 4
            })./{ case ((c, p), l) =>
                val n = f.hasTrait(Disorganized).?(l.num.hi(2)).|(l.num)
                val (damaged, fresh) = l.partition(c.damaged.has)
                val combinations = 0.to(n).reverse./~(k => max(0, k - fresh.num).to(min(k, damaged.num))./(i => fresh.take(k - i) ++ damaged.take(i)))
                combinations
            }.reduceLeft((aa, bb) => aa./~(a => bb./(b => a ++ b))).%(_.any).%(l => l.ofc(Empire).num <= l.ofc(f).ships.num || f.officers)

            Ask(f).group("Move".hh, effect./("with" -> _), "from", s, "to", d)
                .each(combinations)(l => {
                    val cat = (cascade
                        && campaign.??(f.rivals.exists(e => e.rules(d) && (e.regent && f.regent && l.ofc(Empire).any).not)).not
                        && campaign.??(l.exists(_.piece == Blight)).not
                    )

                    MoveListAction(f, s, d, l, cat, x, effect, then).as(l./(u => game.showFigure(u)))
                })
                .cancel

        case MoveListAction(f, s, d, l, cascade, x, effect, then) =>
            f.pay(x)

            f.log("moved", l.comma, "from", s, "to", d, x, effect./("with" -> _))

            if (d.gate)
                f.rivals.%(_.hasLore(GatePorts))./ { e =>
                    if (e.at(d).starports.fresh.any && e.rules(d) && l.shiplikes.ofc(f).any) {
                        if (f.pool(Agent)) {
                            f.reserve --> Agent --> e.captives

                            e.log("captured", Agent.of(f), "with", GatePorts)
                        }
                        else
                            f.log("had no", Agent.sof(f))
                    }
                }

            l --> d

            val next = if (s.cluster == 7 && effect.has(PassageStorms).not)
                ReRollPassageExitAction(then)
            else
                then

            val cont = cascade && game.countedMoves < 12

            if (cont)
                game.countedMoves += 1
            else
                game.countedMoves = 0

            if (cont)
                MoveFromAction(f, d, l, true, NoCost, effect, DoneAction(then), next)
            else
            if (f.hasLore(SprinterDrives) && f.used.has(SprinterDrives).not && l.shiplikes.fresh.any)
                UseEffectAction(f, SprinterDrives, MoveFromAction(f, d, l.shiplikes.fresh, false, NoCost, |(SprinterDrives), DoneAction(ClearEffectAction(f, SprinterDrives, next)), ClearEffectAction(f, SprinterDrives, next)))
            else
                next


        // ...
        case _ => UnknownContinue
    }
}
