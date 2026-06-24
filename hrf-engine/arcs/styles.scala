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

package object elem {
    import hrf.elem._

    object dt {
        val Arrow = Span(Text(0x2192.toChar.toString))
    }

    object styles extends BaseStyleMapping("arcs") {
        import rules._

        Red --> color("#cf002c")
        Yellow --> color("#d09f31")
        // Green --> color("#087033")
        Blue --> color("#0b4e98")
        White --> color("#cccccc")

        Free --> color("#908977")
        Empire --> color("#d942ff")
        Blights --> color("#2dc93b")

        Material --> color("#9d4d8a")
        Fuel --> color("#886b29")
        Weapon --> color("#c05728")
        Relic --> color("#98d1dc")
        Psionic --> color("#e36386")

        Administration --> $(color("#898a82"), font.weight("bold"))
        Aggression --> $(color("#a82024"), font.weight("bold"))
        Construction --> $(color("#d65329"), font.weight("bold"))
        Mobilization --> $(color("#2e899b"), font.weight("bold"))

        Faithful --> $(color("#892e56"), font.weight("bold"))
        Zeal --> $(color("#c2242b"), font.weight("bold"))
        Wisdom --> $(color("#3d438a"), font.weight("bold"))

        object title extends CustomStyle(font.family("FMBolyarPro-900"), font.size("84%"))
        object titleW extends CustomStyle(font.family("FMBolyarPro-900"), font.size("84%"), color("#d3d3d3"))
        object cluster extends CustomStyle(font.family("FMBolyarPro-900"), line.height("2ex"))


        object token extends CustomStyle(width("2.8ex"), vertical.align("middle"), margin("0.2ex"))
        object inlineToken extends CustomStyle(width("2.8ex"), vertical.align("middle"), margin.left("0.2ex"), margin.right("0.2ex"), margin.top("-0.1ex"), margin.bottom("-0.1ex"))
        object inlineTokenDarken extends CustomStyle(width("2.8ex"), vertical.align("middle"), margin.left("0.2ex"), margin.right("0.2ex"), margin.top("-0.1ex"), margin.bottom("-0.1ex"), filter("brightness(0.5)"))
        object building extends CustomStyle(height("3ex"), vertical.align("middle"), margin.left("-0.1ex"), margin.right("-0.1ex"), margin.top("-0.1ex"), margin.bottom("0.2ex"))
        object ship extends CustomStyle(height("2.4ex"), vertical.align("middle"), margin.left("-0.1ex"), margin.right("-0.1ex"), margin.top("-0.1ex"), margin.bottom("0.2ex"))
        object plaque extends CustomStyle(height("4.2ex"), vertical.align("middle"), margin("-0.1ex"))
        object plaqueContainer extends CustomStyle(margin("0.1ex"), white.space("nowrap"))

        object token3x extends CustomStyle(width("8.1ex"), vertical.align("middle"), margin("0.0ex"))
        object ship3x extends CustomStyle(height("7.2ex"), vertical.align("middle"), margin.left("-0.3ex"), margin.right("-0.3ex"), margin.top("-0.3ex"), margin.bottom("0.6ex"))

        object qship extends CustomStyle(height("5.6ex"), vertical.align("middle"), margin("0.0ex"))
        object qflagship extends CustomStyle(height("7.6ex"), vertical.align("middle"), margin("0.0ex"))
        object qbuilding extends CustomStyle(height("6.9ex"), vertical.align("middle"), margin.top("-0.5ex"), margin.bottom("-0.8ex"))

        object circle extends CustomStyle(border.radius("5ex"))

        object condensed extends CustomStyle(letter.spacing("-0.125ch"))

        object power extends CustomStyle(color("#e7ce4d"))
        object hit extends CustomStyle(color("#dc143c"))

        object fund extends CustomStyle(height("2.6ex"), vertical.align("middle"), margin("0.2ex"))
        object figureLine extends CustomStyle(margin.left("1ex"), margin.right("1ex"))

        object card extends CustomStyle(display("block"), width("14.88ex"), height("20.78ex"))
        object leaderCard extends CustomStyle(display("block"), width("14.88ex"), height("25.50ex"))
        object courtCard extends CustomStyle(display("block"), width("20.00ex"), height("27.93ex"))
        object fateCard extends CustomStyle(display("block"), width("22.32ex"), height("37.75ex"))
        object setupCard extends CustomStyle(display("block"), width("50ex"))
        object card0 extends CustomStyle(padding("5px"), padding("0.0vmin"), margin("0px"), margin("0.0vmin"), border.color("transparent"), border.style("solid"), border.width("0.3vmin"))
        object cardX extends CustomStyle(padding("5px"), padding("0.5vmin"), margin("8px"), margin("0.8vmin"), border.color("transparent"), border.style("solid"), border.width("0.3vmin"))
        object cardI extends CustomStyle(margin("1.2ex"), outline.width("0.8ex"))

        object inline extends CustomStyle(display("inline-block"))
        object nomargin extends CustomStyle(margin("0"))
        object selected extends CustomStyle(filter("brightness(1.1) saturate(1.1)"), outline.color("#ffffff"), outline.style("solid"), outline.width("0.3vmin"))

        object smallname extends CustomStyle(font.size("90%"), font.weight("bold"))

        object artwork extends CustomStyle(max.height("100%"), max.width("100%"), margin("auto"))
        object seeThroughInner extends CustomStyle(background.color("#222222e0"))

        object status extends CustomStyle(
            border.width("4px"),
            border.width("0.4vmin"),
            text.align("center"),
            overflow.x("hidden"),
            overflow.y("auto"),
            text.overflow("ellipsis")
        )

        object fstatus extends CustomStyle(font.size("115%"))

        object statusUpper extends CustomStyle(height("100%"), overflow.x("hidden"), overflow.y("auto"))
        object play extends CustomStyle(margin.top("-4.2ex"))
        object initative extends CustomStyle(font.size("160%"), line.height("0"), vertical.align("sub"), color("#ffffff"))
        object bonus extends CustomStyle(/*font.size("110%"), */ line.height("0"), vertical.align("middle"), color("#e7ce4d"))
        object hand extends CustomStyle(white.space("nowrap"))
        object cardName extends CustomStyle(margin.top("-0.6ex"), margin.bottom("-0.9ex"), white.space("nowrap"))
        object firstRegent extends CustomStyle(color("#7e2495"), margin.bottom("-3.4ex"), margin.top("1ex"))
        object flagship extends CustomStyle(outline.color("#ffffff"), outline.style("dotted"), outline.width("1px"), margin.left("auto"), margin.right("auto"), margin.top("0.2ex"), margin.bottom("0.4ex"), padding.left("0.35ex"), padding.right("0.35ex"), padding.top("0.2ex"), background.color("#000000"), width("fit-content"), font.size("84%"))

        object dashedRect extends CustomStyle(outline.style("dashed"), outline.width("0.2ex"), margin.left("1.5ex"), margin.right("1.5ex"), margin.top("0.5ex"), margin.bottom("0.5ex"))
        object imperialTrust extends CustomStyle(outline.color("#bc09eb"), background.color("#000000"))
        object merchantLeague extends CustomStyle(outline.color("#666666"))
        object materialCartel extends CustomStyle(outline.color("#9d4d8a"))
        object fuelCartel extends CustomStyle(outline.color("#886b29"))
        object weaponCartel extends CustomStyle(outline.color("#c05728"))
        object relicCartel extends CustomStyle(outline.color("#98d1dc"))
        object psionicCartel extends CustomStyle(outline.color("#e36386"))

        object quasi extends CustomStyle(background.color("transparent"), padding("0.2ch"), margin("0.1ch"), outline.style("none"), border.style("solid"), border.width("0.2ch"), border.color("transparent"))
        object selfigure1 extends CustomStyle(border.color("#dc143c"), border.style("dashed"))
        object selfigure2 extends CustomStyle(border.color("#dc143c"), border.style("solid"))
        object unquasi extends CustomStyle(filter("brightness(0.9)"))

        object keyLine extends CustomStyle(margin.top("-0.8ex"), margin.bottom("-0.6ex"), white.space("nowrap"))
        object outrageLine extends CustomStyle(margin.top("0.1ex"), margin.bottom("-0.6ex"))
        object tokenTop extends CustomStyle(width("2.8ex"), vertical.align("top"), margin("0.2ex"))
        object tokenTopPlus extends CustomStyle(width("2.8ex"), vertical.align("top"), margin.left("0.2ex"), margin.right("0.2ex"), margin.top("0.6ex"), margin.bottom("0.2ex"))
        object tokenObj extends CustomStyle(width("2.8ex"), vertical.align("top"))

        object infoStatus extends CustomStyle(line.height("100%"))

        object notDoneYet extends CustomStyle(color("darkred"), text.decoration.line("line-through"), text.decoration.style("wavy"))

        object outlined extends CustomStyle(border.width("2px"), border.width("0.2ex"), border.style("solid"), background.color("#222222"), margin("0.1ex"), display("inline-block"), text.indent("0"))

        object used extends CustomStyle(color("darkred"), text.decoration.line("line-through"), text.decoration.style("wavy"))

        object intermission extends CustomStyle(background("linear-gradient(to bottom, #d942ff 0%, #d942ff 50%, #2dc93b 70%, #2dc93b 100%)"), background.clip("text"), color("transparent"))
    }

    implicit class ElemString(val s : String) extends AnyVal {
    }

    implicit class ElemElem(val elem : Elem) extends AnyVal {
        def larger = elem.styled(xstyles.larger125)
    }

    implicit class ElemInt(val n : Int) extends AnyVal {
        def power = 0x27C5.toChar.toString.hh ~ n.styled(xstyles.bold).styled((n >= 0).?(styles.power).|(styles.hit)) ~ 0x27C6.toChar.toString.hh
        def cards = (n != 1).?(n.hl ~ " cards").|("a card")
        def hit = (n != 1).?(n.hlb ~ " Hits").|(1.hlb ~ " Hit").styled(styles.hit)
    }

    object borders extends BaseStyleMapping("arcs-border") {
        import rules._

        Red --> outline.color("#a60024")
        Yellow --> outline.color("#a88228")
        Blue --> outline.color("#09407a")
        White --> outline.color("#a3a3a3")
    }
}
