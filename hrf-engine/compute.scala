package hrf.compute
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

object Compute {
    implicit def valueToJust[T](v : T) = Just(v)
}

trait Compute[+T] {
    def get(continue : (() => Unit) => Unit)(onResult : T => Unit) : Unit
    def map[U](f : T => U) : Compute[U] = new MapCompute[U, T](this)(f)
    def flatMap[U](f : T => Compute[U]) : Compute[U] = new FlatMapCompute[U, T](this)(f)
    def result : Option[T]
    def immediate : T = {
        get(c => c())(r => {})
        result.get
    }
}

class MapCompute[T, U](compute : Compute[U])(f : U => T) extends Compute[T] {
    var result : |[T] = None
    def get(continue : (() => Unit) => Unit)(onResult : T => Unit) = compute.get(continue)(_.use(f).use(v => { result = |(v) ; onResult(v) }))
}

class FlatMapCompute[T, U](compute : Compute[U])(f : U => Compute[T]) extends Compute[T] {
    var result : |[T] = None
    def get(continue : (() => Unit) => Unit)(onResult : T => Unit) = compute.get(continue)(v => f(v).get(continue)(v => { result = |(v) ; onResult(v) }))
}

trait Heavy[T] extends Compute[T] {
    var result : |[T] = None

    def work() : |[T]

    def get(continue : (() => Unit) => Unit)(onResult : T => Unit) {
        result match {
            case Some(r) => onResult(r)
            case None => work() match {
                case Some(r) =>
                    result = Some(r)
                    onResult(r)
                case None =>
                    continue(() => get(continue)(onResult))
            }
        }
    }
}

case class Just[T](value : T) extends Compute[T] {
    def result = |(value)
    def get(continue : (() => Unit) => Unit)(onResult : T => Unit) = onResult(value)
}
