package hrf.options
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

import hrf.base._
import hrf.elem._


object OptionsState {
    def apply[O <: BaseOption](all : $[O], mandatory : $[O], selected : $[O], dimmed : $[O]) : OptionsState[O] = new OptionsState[O](all, all.intersect(mandatory), all.intersect(selected), dimmed.intersect(all))
}

case class OptionsState[O <: BaseOption] private (all : $[O], mandatory : $[O], selected : $[O], dimmed : $[O]) {
    // ---
    // ---
    // ---
    // +++("all", all)
    // +++("selected", selected)
    // +++("dimmed", dimmed)
    // ---
    // ---
    // ---
    val actual = mandatory ++ selected

    def filtered(options : $[O]): $[O] = {
        var remaining = all.intersect(options)
        var resulting = mandatory ++ remaining

        var found = true
        while (found) {
            val x = remaining.%(o => o.required(all).forall(_.diff(resulting).any))
            found = x.any
            remaining = remaining.diff(x)
            resulting = mandatory ++ remaining
        }

        found = true
        while (found) {
            val x = remaining.%(o => o.blocked(all).exists(_.diff(resulting).none))
            found = x.any
            remaining = remaining.diff(x)
            resulting = mandatory ++ remaining
        }

        found = true
        while (found) {
            val x = remaining.%(o => o.forcedOn(all).diff(resulting).any)
            found = x.any
            remaining = remaining.diff(x)
            resulting = mandatory ++ remaining
        }

        found = true
        while (found) {
            val x = remaining.%(o => o.forcedOff(all).intersect(resulting).any)
            found = x.any
            remaining = remaining.diff(x)
            resulting = mandatory ++ remaining
        }

        if (remaining != options)
            filtered(remaining)
        else
            options
    }

    def enabled(o : O) : Boolean = {
        if (mandatory.has(o))
            false
        else
        if (selected.has(o))
            o.toggle
        else
            o.required(all).exists(_.diff(actual).none) && o.blocked(all).forall(_.diff(actual).any)
    }

    def click(o : O) : OptionsState[O] = {
        if (selected.has(o)) {
            if (o.toggle.not)
                return this

            copy(selected = selected.but(o)).checkDimmed()
        }
        else {
            val on = all.intersect(o.forcedOn(all))
            val off = o.forcedOff(all)
            val clear = o.clear(all)

            copy(selected = $(o) ++ on ++ selected.diff(off).diff(clear), dimmed = (selected.intersect(off) ++ dimmed.but(o).diff(on)).diff(clear)).checkDimmed()
        }
    }

    def checkDimmed() : OptionsState[O] = {
        // warn("checkDimmed")
        // +++("selected", selected)
        var remaining = filtered(selected)
        // +++("remaining", remaining)
        var candidates = selected.diff(remaining) ++ dimmed
        // +++("candidates", candidates)

        var found = true

        while (found) {
            found = false

            candidates.foreach { c =>
                if (!found)
                    if (filtered(remaining :+ c).num > remaining.num) {
                        remaining :+= c
                        candidates :-= c
                        found = true
                        // +++("candidates -> remaining", c)
                    }
            }
        }

        val retired = selected.diff(remaining)
        // +++("retired", retired)

        OptionsState(all, mandatory, remaining, retired ++ dimmed.diff(remaining))
    }

}

trait BaseOption extends Record {
    val group : Elem
    def valueOn : Elem
    def valueOff : Elem = Text(valueOn.text)
    def decorate(elem : Elem) = elem
    val explain : $[Elem] = $
    val grow : Style = xlo.grow4
    val links : $[(String, String)] = $

    val toggle : Boolean = false

    def required(all : $[BaseOption]) : $[$[BaseOption]] = $($())
    def blocked(all : $[BaseOption]) : $[$[BaseOption]] = $()
    def forcedOn(all : $[BaseOption]) : $[BaseOption] = $()
    def forcedOff(all : $[BaseOption]) : $[BaseOption] = $()
    def clear(all : $[BaseOption]) : $[BaseOption] = $()
}

trait OneOfGroup { self : BaseOption =>
    override def forcedOff(all : $[BaseOption]) = all.of[OneOfGroup].but(this).%(_.group == this.group)
}

trait ToggleOption { self : BaseOption =>
    override val toggle = true
}

trait ImportantOption extends BaseOption


object BaseOption {
    def optimizeOld(l : $[BaseOption]) = {
        var r = l

        var f = true
        while (f) {
            val x = r
            f = x.any
            r = r.diff(x) ++ x./~{
                case o : OneOfGroup => l.diff(x).collect { case q : OneOfGroup if q.group == o.group => q }.lastOption
                case _ => None
            }
        }

        r = r./~{
            case o : OneOfGroup => r.collect { case q : OneOfGroup if q.group == o.group => q }.lastOption.%(_ == o)
            case o => Some(o)
        }

        r = r.reverse.distinct.reverse

        r
    }
}
