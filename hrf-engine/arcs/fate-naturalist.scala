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


case object Naturalist extends Fate("Naturalist", "fate21", 3) {
    override val expansion = NaturalistExpansion
}


case object LiveInTheGarden extends Objective("f21-01b", "Live in the Garden")

case object Blightkin extends Ambition { val strength = 6 }
case object BlightSociety extends Lore("f21-02", "Blight Society")
case object SporeGuides extends GuildEffect("Spore Guides", Psionic, 999)
case object BlightkinAmbition extends Law("f21-04", "Blightkin Ambition")


case class SeedInitAction(self : Faction, x : Cost, then : ForcedAction) extends ForcedAction
case class SeedRolledAction(self : Faction, random : Int, then : ForcedAction) extends RandomAction[Int]
case class SeedMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction with Soft
case class SeedAdjustMainAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction with Soft
case class SeedAdjustAction(self : Faction, n : Int, lane : Int, then : ForcedAction) extends ForcedAction
case class SeedAction(self : Faction, s : System, then : ForcedAction) extends ForcedAction


object NaturalistExpansion extends FateExpansion(Naturalist) {
    val deck = $(
        BlightSociety,
        GuildCard("f21-03", SporeGuides),
    )

    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // NATURALIST III
        case FateInitAction(f, `fate`, 3, then) =>
            f.objective = |(LiveInTheGarden)

            f.progress = game.factions.num @@ {
                case 2 => 18
                case 3 => 16
                case 4 => 14
            }

            f.log("objective was set to", f.progress.hlb)

            game.laws :+= BlightkinAmbition

            f.log("set", game.laws.last)

            game.ambitions = game.ambitions./~ {
                case Empath => $(Empath, Blightkin)
                case a => $(a)
            }

            FateDeck(fate) --> BlightSociety --> f.lores

            f.log("got", f.lores.last)

            FateDeck(fate).%(_.as[GuildCard]./(_.effect).has(SporeGuides)) --> f.loyal

            f.log("took", f.loyal.last)

            Then(then)

        case SeedInitAction(f, x, then) =>
            f.pay(x)

            f.log("seeded", x)

            Random[Int]($(1, 2, 3, 4, 5, 6), SeedRolledAction(f, _, then))

        case SeedRolledAction(f, n, then) =>
            f.log("rolled", n.hlb)

            SeedMainAction(f, n, then)

        case SeedMainAction(f, n, then) =>
            Ask(f).group("Seed in")
                .each(systems.%(_.cluster == n))(s => SeedAction(f, s, then).as(s).!(Blights.at(s).any))
                .useIf(game.market.exists(m => Influence(m.index).$.ofc(f).any)) { _
                    .add(SeedAdjustMainAction(f, (n - 1 + 1) % 6 + 1, then).as("Adjust to cluster", ((n - 1 + 1) % 6 + 1).hlb))
                    .add(SeedAdjustMainAction(f, (n - 1 + 5) % 6 + 1, then).as("Adjust to cluster", ((n - 1 + 5) % 6 + 1).hlb))
                }
                .bailout(then.as("Done"))
                .needOk

        case SeedAdjustMainAction(f, n, then) =>
            Ask(f).group("Adjust seed cluster to", n.hl.styled(styles.cluster), "removing", Agent.of(f), "from")
                .each(game.market) { m =>
                    SeedAdjustAction(f, n, m.index, then).as(m.$.intersperse("|"))
                        .!(Influence(m.index).$.ofc(f).none)
                }
                .cancel

        case SeedAdjustAction(f, n, m, then) =>
            Influence(m).$.ofc(f).first --> f.reserve

            f.log("adjusted cluster to", n.hl.styled(styles.cluster), "removing", Agent.of(f), "from", Market(m).first)

            SeedMainAction(f, n, then)

        case SeedAction(f, s, then) =>
            val u = Blights.reserve.first

            u --> s

            Blights.damaged :+= u

            f.log("placed", u, "in", s)

            then


        // ...
        case _ => UnknownContinue
    }
}
