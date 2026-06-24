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

class BotOld(f : Faction) extends EvalBot {
    def eval(actions : $[UserAction])(implicit game : Game) : Compute[$[ActionEval]] = {
        val ev = new GameEvaluationNew(f, false)
        actions./{ a => ActionEval(a, ev.eval(a)) }
    }
}
