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

import scala.collection.parallel.CollectionConverters._

trait BaseHost extends hrf.host.BaseHost {
    val gaming = arcs.gaming
    val path = "arcs"

    def factionName(f : F) = f.name

    def serializer = arcs.Serialize
    def start = StartAction(version)
    def times = 1


    type W = Faction

    def factions = $(Red, White, Blue, Yellow)
    def subjects = factions

    def nameWinner(w : Faction) = w.name

    def winners(a : Action)(implicit g : G) = a @@ {
        case GameOverWonAction(_, f) => $(f)
    }

    def winnersFromFaction(f : F)(implicit g : G) = $(f)

    def askBot(g : G, f : F, actions: $[UserAction]) =
        if (f == Red)
            new BotEOC(f, e => new BotNew(e, false)).ask(actions, 0)(g)
        else
            new BotNew(f, true).ask(actions, 0.01)(g)

    def batch = {
        val allComb = factions.combinations(4).$
        val repeat = 1.to(20).map(_ => factions)

        def allSeatings(factions : $[Faction]) = factions.permutations.$
        def randomSeating(factions : $[Faction]) = allSeatings(factions).shuffle.head

        val base = allSeatings(factions)./(l => () => new G(l, $(
            RandomPlayerOrder,
        )))

        base
    }
}

trait CampaignHost extends hrf.host.BaseHost {
    val gaming = arcs.gaming
    val path = "arcs"

    def factionName(f : F) = f.name

    def serializer = arcs.Serialize
    def start = StartAction(version)
    def times = 10


    type W = Fate

    def factions = $(Red, White, Blue, Yellow)
    def subjects = Fates.act1 // ++ Fates.act2 ++ Fates.act3

    def nameWinner(w : Fate) = w.name

    def winners(a : Action)(implicit g : G) = a @@ {
        case GameOverWonAction(_, f) => f.past
    }

    def winnersFromFaction(f : F)(implicit g : G) = f.past

    def askBot(g : G, f : F, actions: $[UserAction]) = new BotNew(f, true).ask(actions, 0)(g)

    def batch = {
        val allComb = factions.combinations(4).$
        val repeat = 1.to(20).map(_ => factions)

        def allSeatings(factions : $[Faction]) = factions.permutations.$
        def randomSeating(factions : $[Faction]) = allSeatings(factions).shuffle.head

        val base = allSeatings(factions)./(l => () => new G(l, $(
            // NoFate,
            HostTest,
            Act1Only,
            RandomPlayerOrder,
        )))

        base
    }

}

object Host extends CampaignHost {
}
