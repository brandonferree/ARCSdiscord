package hrf

import org.scalajs.dom
import scalajs.js

// DOM-facade compatibility shims for the vendored HRF browser UI.
//
// HRF's canvas / mini-map code (hrf-engine/grey.scala, grey-map.scala — both
// `package hrf.ui`) reaches for DOM members the modern scalajs-dom facade (2.x)
// no longer exposes on the expected types. We provide compatibility here WITHOUT
// editing the pristine vendored sources:
//
//   * Reads (`MouseEvent.offsetX/offsetY`) — an implicit extension; implicits
//     resolve fine for a plain selection. Because this is the `package object`
//     for `hrf.ui`, it is in scope for every file in that package automatically.
//
//   * Assignments (`parent.ontouchstart = f`, etc.) — Scala does NOT apply
//     implicit conversions to the left of `=`, so an implicit setter can't work.
//     Instead the build-time patch (see build.sbt `hrfWeb`) rewrites those sites
//     to `hrf.ui.touchy(parent).ontouchmove = f`, where `touchy` exposes a tiny
//     native facade carrying the handlers with the right event types. CSS-property
//     assignments (`style.touchAction`, `style.filter`) the patch rewrites to a
//     `js.Dynamic` assignment, which needs no shim.
package object ui {

    implicit class MouseEventOffsetOps(private val e : dom.MouseEvent) extends AnyVal {
        // PointerEvent <: MouseEvent, so this covers both.
        def offsetX : Double = e.asInstanceOf[js.Dynamic].offsetX.asInstanceOf[Double]
        def offsetY : Double = e.asInstanceOf[js.Dynamic].offsetY.asInstanceOf[Double]
    }

    /** Typed view of the touch/wheel handler slots that scalajs-dom 2.x dropped
      * from `html.Element`. Used by the build-time patch of grey*.scala. */
    @js.native
    trait TouchyElement extends js.Object {
        var ontouchstart : js.Function1[dom.TouchEvent, _] = js.native
        var ontouchmove  : js.Function1[dom.TouchEvent, _] = js.native
        var onwheel      : js.Function1[dom.WheelEvent, _] = js.native
    }

    def touchy(e : dom.EventTarget) : TouchyElement = e.asInstanceOf[TouchyElement]
}
