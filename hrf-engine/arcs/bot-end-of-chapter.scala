package arcs
//
//
//
//
import hrf.colmat._
import hrf.compute._
import hrf.logger._
//
//
//
//

import hrf.base._
import hrf.bot._


class BotEOC(faction : Faction, bot : Faction => EvalBot) extends EvalBot {
    def pwr(f : Faction)(implicit game : Game) = f.power * 100 + (f.power >= game.factions.num @@ {
        case 2 => 33
        case 3 => 30
        case 4 => 27
    }).??(10000)

    def vp(c : Continue)(f : Faction)(implicit game : Game) = c match {
        case GameOver(ww, _, _) => Some(ww.has(f).?(9999).|(-9999))
        case Milestone(_, CheckWinAction) if campaign.not => Some((pwr(f) - game.factions.but(f)./(pwr).max) + (game.chapter < 5).??(0
            + f.lores.num * 120
            + f.loyal.num * 60
            + f.spendable.num * 30
            + game.declared.contains(Tyrant).not.??(f.captives.num * 50)
            + game.declared.contains(Warlord).not.??(f.trophies.num * 40)
            + systems./(_.$.ofc(f).use(l => l.ships.num * 10 + l.starports.num * 15 + l.cities.num * 20)).sum
            - f.outraged.num * 160
            - f.damaged.num * 2
        ))
        case _ => None
    }

    def eval(actions : $[UserAction])(implicit game : Game) : Compute[$[ActionEval]] = {
        if (actions.num == 1)
            return $(ActionEval(actions.only, $))

        val unwraps = actions./(_.unwrap)

        if (unwraps.notOf[ReorderResourcesAction].none) {
            // println("Auto Reorder")

            return bot(faction).eval(actions)
        }

        if (unwraps.notOf[BattleDiceAction].none) {
            // println("Auto Dice")

            return bot(faction).eval(actions)
        }

        val gc = game.cloned().cleanFor(faction)

        // println("Chapter " + game.chapter + " Round " + game.round)

        val bots = game.factions./(e => e -> bot(e)).toMap

        bots(faction).eval(actions).flatMap { l =>
            val prime = l.sortWith(EvalBot.compare).take(24)./(_.action)
            // val runs = prime./~((10 + game.round * game.factions.num * 0).times) ++ actions.diff(prime).shuffle.take(0)./~(2.times)
            val runs = prime./~(1.times)

            new Heavy[$[ActionEval]] {
                var results : $[Int] = $
                var reruns : $[UserAction] = $
                var reresults : $[Int] = $

                def work() : |[$[ActionEval]] = {
                    if (results.num < runs.num) {
                        val action = runs(results.num)

                        implicit val game = gc.cloned()

                        def execute(a : Action) : Continue = game.performContinue(None, a, false).continue

                        def process(c : Continue) : Continue = c match {
                            case Log(_, _, c) => c
                            case Then(a) => execute(a)
                            case Milestone(_, a) => execute(a)
                            case Ask(o, actions) => execute({
                                try {
                                    bots(o.as[Faction].get).ask(actions, 0.0).immediate
                                }
                                catch {
                                    case e =>
                                        println()
                                        println()
                                        println()
                                        println(game.actions./(_.unwrap)./(Serialize.write).mkString("\n"))
                                        println()
                                        println()
                                        println()
                                        throw e
                                }
                            })
                            case MultiAsk(l, _) => process(l.first)
                            case DelayedContinue(_, c) => c
                            case Roll3(d1, d2, d3, roll, _) => execute(roll(
                                d1.of[BattleDie].distinct.single./~(_.ownApprox.take(d1.num)),
                                d2.of[BattleDie].distinct.single./~(_.ownApprox.take(d2.num)),
                                d3.of[BattleDie].distinct.single./~(_.ownApprox.take(d3.num)),
                            ))
                            case Shuffle(l, f, _) => execute(f(l.drop(l.num / 2) ++ l.take(l.num / 2)))
                        }

                        var c = execute(action)

                        while (vp(c)(faction).none) {
                            c = process(c)
                        }

                        results :+= vp(c)(faction).get

                        None
                    }
                    else
                    if (reruns.none) {
                        var best = results.max

                        if (results.count(best) == 1)
                            best = results.but(best).maxOr(0)

                        var ll = runs.zp(results).toList.%>(_ >= best).lefts

                        if (ll.num == 1)
                            Some(
                                runs.zp(results).groupMap(_._1)(_._2).$./{ case (a, l) => ActionEval(a, $(
                                    Evaluation(l.sum, "vp diff at the end of turn fixed")
                                ))}
                            )
                        else {
                            reruns = ll./~((24*(1 + game.round)/ll.num).times)
                            None
                        }
                    }
                    else
                    if (reresults.num < reruns.num) {
                        val action = reruns(reresults.num)

                        implicit val game = gc.cloned()

                        def execute(a : Action) : Continue = game.performContinue(None, a, false).continue

                        def process(c : Continue) : Continue = c match {
                                case Log(_, _, c) => c
                            case Then(a) => execute(a)
                            case Milestone(_, a) => execute(a)
                            case Ask(o, actions) => execute({
                                try {
                                    val f = o.as[Faction].get

                                    if (f == faction)
                                        bots(f).ask(actions, 0.00).immediate
                                    else
                                        bots(f).ask(actions, 0.05).immediate
                                }
                                catch {
                                    case e =>
                                        println()
                                        println()
                                        println()
                                        println(game.actions./(_.unwrap)./(Serialize.write).mkString("\n"))
                                        println()
                                        println()
                                        println()
                                        throw e
                                }
                            })
                            case MultiAsk(l, _) => process(l.shuffle.first)
                            case DelayedContinue(_, c) => c
                            case Roll3(d1, d2, d3, roll, _) => execute(roll(d1./(_.roll()), d2./(_.roll()), d3./(_.roll())))
                            case Shuffle(l, f, _) => execute(f(l.shuffle))
                        }

                        var c = execute(action)

                        while (vp(c)(faction).none) {
                            c = process(c)
                        }

                        reresults :+= vp(c)(faction).get

                        None
                    }
                    else {
                        Some(
                            reruns.zp(reresults).groupMap(_._1)(_._2).$./{ case (a, l) => ActionEval(a, $(
                                Evaluation((l.sum * 100) /~/ l.num, "vp diff at the end of turn variable"),
                                Evaluation(results(runs.indexOf(a)), "vp diff at the end of turn fixed")
                            ))} ++
                            runs.zp(results).groupMap(_._1)(_._2).$.%<!(reruns.has)./{ case (a, l) => ActionEval(a, $(
                                Evaluation(-1000000, "vp diff at the end of turn variable"),
                                Evaluation(l.sum, "vp diff at the end of turn fixed")
                            ))}
                        )
                    }
                }
            }
        }
    }

}
