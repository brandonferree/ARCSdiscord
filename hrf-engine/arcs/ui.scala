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

import org.scalajs.dom

import hrf.canvas._

import hrf.web._
import hrf.ui._

import hrf.elem._
import hrf.html._

import arcs.elem._

import hrf.ui.again._
import hrf.ui.sprites._

import scalajs.js.timers.setTimeout


object UI extends BaseUI {
    val mmeta = Meta

    def create(uir : ElementAttachmentPoint, arity : Int, options : $[hrf.meta.GameOption], resources : Resources, title : String, callbacks : hrf.Callbacks) = new UI(uir, arity, title, options, resources, callbacks)
}

class UI(val uir : ElementAttachmentPoint, arity : Int, title : String, val options : $[hrf.meta.GameOption], val resources : Resources, callbacks : hrf.Callbacks) extends GUI {
    def factionElem(f : Faction) = f.name.styled(f)

    override def randomTip() = Meta.tips.shuffle.starting

    var tip = randomTip()

    val statuses = 1.to(arity)./(i => newPane("status-" + i, Content, styles.status, styles.fstatus, ExternalStyle("hide-scrollbar")))

    val campaign : Boolean = options.of[CampaignOption].any

    def stars = callbacks.settings.has(StarStarports)
    def starport : String = callbacks.settings.has(StarStarports).?("starport-alt").|("starport")


    val overlayPane = newOuterPane("map-small-overlay", Content)
    overlayPane.invis()
    overlayPane.attach.parent.parentElement.style.zIndex = "300"


    var flagship : |[Faction] = None

    val court : CanvasPane = new CanvasPaneX(newPane("court", Content), 2/2, Inside)(resources) {
        moveSpeedUp = 3.8

        dY = 130

        val lanes = 4 + campaign.??(1)
        val d = 12

        val margins = Margins(d, d, d, d)

        object card {
            val width = 744
            val height = 1039
        }

        val width = card.width * lanes + d * lanes - d
        val height = card.height

        def makeScene(reposition : Boolean) = {
            if (flagship.any) {
                val layer = new OrderedLayer

                val panel = img("flagship-panel")

                layer.add(Sprite($(ImageRect(new RawImage(panel), Rectangle(0, 0, panel.width, panel.height), 1.0)), $(Rectangle(0, 0, panel.width, panel.height)), $(Flagship)))(0, 0)

                Flagship.functions(flagship.get).indexed.foreach { (q, i) =>
                    q.$.foreach { u =>
                        val id = u.faction.short + "-" + u.piece.is[City.type].?("city").|(starport) + u.damaged.??("-damaged")

                        layer.add(Sprite($(ImageRect(new RawImage(img(id)), Rectangle(0, 0, 122, 122), 1.0)), $(Rectangle(0, 0, 122, 122))))(25 + 161 * i + (i > 2).??(208), 155 + (u.piece.is[Starport.type] && stars).??(9))
                    }

                    q.armor.$.foreach { u =>
                        val id = u.faction.short + "-" + u.piece.is[City.type].?("city").|(starport) + u.damaged.??("-damaged")

                        layer.add(Sprite($(ImageRect(new RawImage(img(id)), Rectangle(0, 0, 122, 122), 1.0)), $(Rectangle(0, 0, 122, 122))))(25 + 161 * i + (i > 2).??(208), 23  + (u.piece.is[Starport.type] && stars).??(9))
                    }
                }


                layer.add(Sprite($(ImageRect(new RawImage(img("flagship-overlay")), Rectangle(0, 0, panel.width, panel.height), 1.0)), $(Rectangle(0, 0, panel.width, panel.height))))(0, 0)

                |(new Scene($(layer), panel.width, panel.height, Margins(0, 0, 0, 0)))
            }
            else {
                val cards = new OrderedLayer

                if (game.market.any) {
                    game.market.indexed.foreach { (m, n) =>
                        val l = m.$

                        l.indexed.foreach { (c, i) =>
                            cards.add(Sprite($(ImageRect(new RawImage(img(c.id)), Rectangle(0, 0, card.width, card.height), 1.0)), $(Rectangle(0, 0, card.width, card.height)), $(c)))(card.width * n + d * n, (card.height + d) * -i /*+ bleed*/)

                            if (c == ImperialCouncilDecided || c == ImperialCouncilInSession) {
                                game.decided.of[Faction].foreach { f =>
                                    val scale = 12.6/2/2
                                    cards.add(Sprite($(ImageRect(new RawImage(img(f.short + "-agent")), Rectangle(-21*2*scale, -21*2*scale, 42*2*scale, 68*2*scale), 0.6)), $))(card.width * n + d * n + card.width / 2, 400)
                                }

                                if (game.decided.has(Blights) || (game.decided.none && (factions.exists(_.played.exists(_.is[EventCard]))))) {
                                    val scale = 12.6/2/2
                                    cards.add(Sprite($(ImageRect(new RawImage(img("blight")), Rectangle(-78*scale, -180*scale, 142*scale, 180*scale), 0.7)), $), 0.5, 0)(card.width * n + d * n + card.width / 2, 400)
                                    cards.add(Sprite($(ImageRect(new RawImage(img("imperial-ship")), Rectangle(-185.0/2*scale, 99*scale, 185*scale, 89*scale), 0.6)), $), 0.8, 0)(card.width * n + d * n + card.width / 2, 400)
                                }
                            }
                        }
                    }

                    game.market.indexed.foreach { (m, n) =>
                        val l = game.figures.get(Influence(m.index))

                        val scale = 2.6
                        val shadow = 3

                        l.foreach { u =>
                            val w = (l.num < 7).?(112).|(card.width / l.num)
                            cards.add(Sprite($(ImageRect(new RawImage(img("agent-background")), Rectangle(0, 0, (42+shadow+shadow)*scale, (68+shadow+shadow)*scale), 1.0)), $))(card.width * n + d * n + card.width / 2 - w / 2 * l.num + w * l.indexOf(u) - shadow*scale, 400 - shadow*scale /*+ bleed*/)
                            cards.add(Sprite($(ImageRect(new RawImage(img(u.faction.short + "-agent")), Rectangle(0, 0, 42*scale, 68*scale), 1.0)), $))(card.width * n + d * n + card.width / 2 - w / 2 * l.num + w * l.indexOf(u), 400 /*+ bleed*/)
                        }

                        game.feudal.get(m.index).foreach { x =>
                            cards.add(Sprite($(ImageRect(new RawImage(img("feudal-court-" + x)), Rectangle(0, 0, 280, 280), 1.0)), $))(card.width * n + d * n + card.width / 2 - 280 / 2, 666)
                        }
                    }
                }

                |(new Scene($(cards), width, height, margins))
            }
        }

        def processHighlight(target : $[Any], xy : XY) {
        }

        def processTargetClick(target : $[Any], xy : XY) {
            onClick(target)
        }

        def adjustCenterZoom() {
            if (flagship.any) {
                dX = 0
                dY = 0
                zoomBase = 0
                return
            }

            dY -= zoomBase * 2.5

            zoomBase = 0

            val maxY = campaign.??((card.height + d) * game.market./(_.num - 1).max)

            dY = dY.clamp(0, maxY)

            dX = 0
        }

        dY = card.height / 2 * 0
    }

    val ambitions : |[CanvasPane] = campaign.?(new CanvasPaneX(newPane("ambitions", Content), 2/2, Inside)(resources) {
        moveSpeedUp = 3.8

        dY = 240

        object card {
            val width = 431
            val height = 602
        }

        object LawRegions extends Regions[Law] {
            def regionAt(p : XY) = |(TheDeadLive)
            def center(k : Law) = XY(card.width / 2, card.height / 2)
            def random(k : Law) = XY(randomInRange(card.width*24/100, card.width*76/100), randomInRange(card.height*24/100, card.height*88/100))
        }

        val deadlive = new FitLayer[Law, Figure](LawRegions, FitOptions())

        def makeScene(reposition : Boolean) = {
            val mp = img("ambitions")

            var offsets : Map[Layer, XY] = Map()

            val background = new OrderedLayer
            background.add(Sprite($(ImageRect(new RawImage(mp), Rectangle(0, -96, mp.width, mp.height), 1.0)), $))(0, 0)

            val grand = game.act == 3 && game.chapter > 0

            if (game.act > 0)
                background.add(Sprite($(ImageRect(new RawImage(img("act-" + game.act)), Rectangle(0, 0, 431, 76), 1.0)), $))(0, -292 - grand.??(80))

            if (game.chapter > 0)
                background.add(Sprite($(ImageRect(new RawImage(img("chapter-" + game.chapter)), Rectangle(0, 0, 431, 76), 1.0)), $))(0, -212 - grand.??(80))

            if (grand)
                background.add(Sprite($(ImageRect(new RawImage(img("grand-ambitions-" + game.chapter)), Rectangle(0, 0, 431, 76), 1.0)), $))(0, -212)

            var line = -14

            val tokens = new OrderedLayer

            game.ambitionable.lift(0)./(m => tokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 123, 139), 1.0)), $))(20, line))
            game.ambitionable.lift(1)./(m => tokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 123, 139), 1.0)), $))(154, line))
            game.ambitionable.lift(2)./(m => tokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 123, 139), 1.0)), $))(288, line))

            def ambitionTokens(a : Ambition) {
                val l1 = game.declared.get(a).|($)
                val l2 = game.conspired.get(a).|($)
                val l3 = game.revealed.get(a).|($)

                val l = l1 ++ l2 ++ l3
                val n = l1.num + l2.num + l3.num

                val d = (n <= 3).?(134).|(134 * 3 / n)

                l.indexed./ { (m, i) =>
                    val x = m @@ {
                        case AmbitionMarker(high, low) => "ambition-values-" + high + "-" + low
                        case Conspired(f) => "conspiracy"
                        case Revealed(f) => "conspiracy-" + f.short
                    }
                    val w = m @@ {
                        case AmbitionMarker(high, low) => 123
                        case Conspired(f) => 139
                        case Revealed(f) => 139
                    }
                    tokens.add(Sprite($(ImageRect(new RawImage(img(x)), Rectangle(0, 0, w, 139), 1.0)), $))(154 + (123 - w) / 2 + d * (2 * i - n + 1) / 2, line)
                }
            }

            line += 188
            ambitionTokens(Tycoon)
            line += 200
            ambitionTokens(Tyrant)
            line += 198
            ambitionTokens(Warlord)
            line += 199
            ambitionTokens(Keeper)
            line += 200
            ambitionTokens(Empath)

            if (game.laws.has(EdenguardAmbition)) {
                line += 200

                val amb = img("ambition-edenguard")

                background.add(Sprite($(ImageRect(new RawImage(amb), Rectangle(0, -44, amb.width, amb.height), 1.0)), $))(0, line)

                ambitionTokens(Edenguard)
            }

            if (game.laws.has(BlightkinAmbition)) {
                line += 200

                val amb = img("ambition-blightkin")

                background.add(Sprite($(ImageRect(new RawImage(amb), Rectangle(0, -44, amb.width, amb.height), 1.0)), $))(0, line)

                ambitionTokens(Blightkin)
            }


            line += 200
            background.add(Sprite($(ImageRect(new RawImage(img("resources")), Rectangle(0, 0, 431, 76), 1.0)), $))(0, line)

            if (game.factions.forall(game.states.contains)) {
                $(
                    (Material, MaterialCartel, -2),
                    (Fuel, FuelCartel, -1),
                    (Weapon, WeaponCartel, 0),
                    (Relic, RelicCartel, 1),
                    (Psionic, PsionicCartel, 2),
                ).foreach { case (r, c, n) =>
                    val full = game.availableNum(r)
                    val semi = PreludeHold(r).num

                    val step = (full + semi <= 5).?(75).|(300 /~/ (full + semi - 1))

                    1.to(full).foreach { i =>
                        tokens.add(Sprite($(ImageRect(new RawImage(img(r.id)), Rectangle(-35, -35, 70, 70), game.factions.exists(_.hasGuild(c)).?(0.2).|(1.0))), $))(217 + 75*n, (line + 43) + 75 + (i-1) * step)
                    }
                    (full + 1).to(full + semi).foreach { i =>
                        tokens.add(Sprite($(ImageRect(new RawImage(img("nothingness")), Rectangle(-35, -35, 70, 70), 0.4)), $))(217 + 75*n, (line + 43) + 75 + (i-1) * step)
                    }
                }

                line += 493
            }

            object card {
                val width = 431
                val height = 602
            }

            if (game.laws.any) {
                background.add(Sprite($(ImageRect(new RawImage(img("laws")), Rectangle(0, 0, 431, 76), 1.0)), $))(0, line)
                line += 90

                game.laws.indexed./ { (l, i) =>
                    if (l == TheDeadLive) {
                        tokens.add(Sprite($(ImageRect(new RawImage(img(l.id)), Rectangle(0, 0, card.width, card.height), TheDeadLive.any.?(0.4).|(1.0))), $(Rectangle(0, 0, card.width, card.height)), $(l)))(0, line)

                        offsets += deadlive -> XY(0, line)

                        deadlive.flush()

                        val shipScale = callbacks.settings.of[ShipsSizeOption].starting @@ {
                            case Some(StandardShipsSize) | None => 100
                            case Some(SmallShipsSize) => 92
                            case Some(SmallerShipsSize) => 84
                            case Some(SmallestShipsSize) => 76
                        } / 100.0

                        TheDeadLive.foreach { u =>
                            def prefix = u.faction.as[Faction]./(_.short.toLowerCase + "-") || (u.faction == Empire).?("imperial-") || (u.faction == Free).?("free-") | ""

                            val status = u.faction.damaged.has(u).??("-damaged")

                            val a = u.piece match {
                                case Ship => $(ImageRect(img(prefix + "ship" + status), 97, 71, shipScale * 1.066))
                            }

                            var q = u.piece match {
                                case Ship => Sprite(a, $(Rectangle(-70, -10, 135, 20), Rectangle(-97, -30, 194, 20), Rectangle(5, -50, 75, 20), Rectangle(35, -70, 35, 20))./(_.scale(shipScale)), $((l, u)))
                            }

                            var z = 4

                            val xy = deadlive.addFloat(l, u, z, reposition)(q)
                        }
                    }
                    else
                        tokens.add(Sprite($(ImageRect(new RawImage(img(l.id)), Rectangle(0, 0, card.width, card.height), 1.0)), $(Rectangle(0, 0, card.width, card.height)), $(l)))(0, line)

                    line += 610
                }

                line += 30
            }

            if (game.edicts.any) {
                background.add(Sprite($(ImageRect(new RawImage(img("edicts")), Rectangle(0, 0, 431, 76), 1.0)), $))(0, line)
                line += 90

                game.edicts.sortBy(_.priority).indexed./ { (e, i) =>
                    tokens.add(Sprite($(ImageRect(new RawImage(img(e.id)), Rectangle(0, 0, card.width, card.height), 1.0)), $(Rectangle(0, 0, card.width, card.height)), $(e)))(0, line)

                    line += 610
                }
            }

            val scene = new Scene($(background, tokens, deadlive), 431, 1600, Margins(0, 0, 0, 0), offsets)

            |(scene)
        }

        def processHighlight(target : $[Any], xy : XY) {
        }

        def processTargetClick(target : $[Any], xy : XY) {
            onClick(target)
        }

        def adjustCenterZoom() {
            dY -= zoomBase * 2.5

            zoomBase = 0

            val extra = (game.edicts.num - 1) * 610 + game.laws.any.??(120) + game.laws.num * 610 + game.laws.has(EdenguardAmbition).??(200) + game.laws.has(BlightkinAmbition).??(200)

            if (dY > 3000)
                dY -= 6000 + extra

            if (dY < -3000 - extra)
                dY += 6000 + extra


            dX = 0
        }
    })

    var highlightCoordinates : |[XY] = None
    var highlightAssassinate = $[Figure]()
    var highlightFire = $[Figure]()
    var highlightBuy = $[Figure]()
    var highlightMove = $[Figure]()
    var highlightRemoveTrouble = $[Figure]()
    var highlightSpreadTrouble = $[System]()
    var highlightPlaceMinion = Map[System, $[Color]]()
    var highlightBuild = Map[System, $[Color]]()
    var highlightUseBuilding = $[Figure]()
    var highlightAreas = $[System]()

    val titleImage = new Bitmap(1200, 120)

    titleImage.context.font = "50px FMBolyarPro-900"
    titleImage.context.fillStyle = "#cccccc"
    titleImage.context.textAlign = "center"
    titleImage.context.fillText(title, 600, 80)

    val map : CanvasPane = new CanvasPaneX(newPane("map-small", Content), 2/2, Inside)(resources) {
        object regions {
            val centers = Map[System, XY](
                System(1, Gate) -> XY(1300, 550),
                System(1, Arrow) -> XY(1050, 360),
                System(1, Crescent) -> XY(1320, 130),
                System(1, Hex) -> XY(1630, 400),
                System(2, Gate) -> XY(1630, 780),
                System(2, Arrow) -> XY(1810, 580),
                System(2, Crescent) -> XY(1920, 730),
                System(2, Hex) -> XY(1900, 900),
                System(3, Gate) -> XY(1590, 1110),
                System(3, Arrow) -> XY(1860, 1060),
                System(3, Crescent) -> XY(2110, 1420),
                System(3, Hex) -> XY(1670, 1370),
                System(4, Gate) -> XY(1170, 1260),
                System(4, Arrow) -> XY(1350, 1660),
                System(4, Crescent) -> XY(940, 1700),
                System(4, Hex) -> XY(570, 1630),
                System(5, Gate) -> XY(870, 990),
                System(5, Arrow) -> XY(640, 1230),
                System(5, Crescent) -> XY(240, 1160),
                System(5, Hex) -> XY(530, 900),
                System(6, Gate) -> XY(910, 690),
                System(6, Arrow) -> XY(630, 730),
                System(6, Crescent) -> XY(630, 520),
                System(6, Hex) -> XY(830, 450),
                System(7, Gate) -> XY(1250, 894),
            )

            val gates = Map[System, $[XY]](
                System(1, Arrow) -> $(XY(1049, 223), XY(1166, 170)),
                System(1, Crescent) -> $(XY(1434, 212)),
                System(1, Hex) -> $(XY(1745, 147), XY(1846, 228)),
                System(2, Arrow) -> $(XY(2010, 440)),
                System(2, Crescent) -> $(XY(2300, 618)),
                System(2, Hex) -> $(XY(2116, 880), XY(2221, 936)),
                System(3, Arrow) -> $(XY(2186, 1127)),
                System(3, Crescent) -> $(XY(1846, 1249)),
                System(3, Hex) -> $(XY(1929, 1534), XY(1830, 1610)),
                System(4, Arrow) -> $(XY(1529, 1573), XY(1430, 1497)),
                System(4, Crescent) -> $(XY(1060, 1584), XY(1159, 1660)),
                System(4, Hex) -> $(XY(776, 1505)),
                System(5, Arrow) -> $(XY(255, 1458)),
                System(5, Crescent) -> $(XY(434, 1101)),
                System(5, Hex) -> $(XY(223, 876), XY(125, 952)),
                System(6, Arrow) -> $(XY(431, 683)),
                System(6, Crescent) -> $(XY(397, 313), XY(299, 389)),
                System(6, Hex) -> $(XY(678, 228)),
            )

            lazy val place = new IndexedImageRegions[System](new RawImage(img("map-regions")), 0, 0, centers)

            lazy val select = new IndexedImageRegions[System](new RawImage(img("map-regions-select")), 0, 0, centers)
        }

        lazy val pieces = new FitLayer[System, Figure](regions.place, FitOptions())

        lazy val outOfPlay = new OrderedLayer

        lazy val highlighted = new OrderedLayer

        lazy val ambTokens = new OrderedLayer

        val width = 2528
        val height = 1776
        val margins = Margins(0, 0, 0, 0)

        lazy val scene = {
            val mp = img("map-no-slots")
            val mr = img("map-regions")
            val ms = img("map-regions-select")

            val background = new OrderedLayer
            background.add(Sprite($(ImageRect(new RawImage(mp), Rectangle(0, 0, mp.width, mp.height), 1.0)), $))(0, 0)

            val areas = new HitLayer(regions.select)

            new Scene($(background, outOfPlay, highlighted, areas, pieces, ambTokens), mp.width, mp.height, margins)
        }

        override def adjustCenterZoom() {
            zoomBase = zoomBase.clamp(-990, 990*2)

            val qX = (width + margins.left + margins.right) * (1 - 1 / zoom) / 2
            val minX = -qX + margins.right - zoomBase / 5
            val maxX = qX - margins.left + zoomBase / 5
            dX = dX.clamp(minX, maxX)

            val qY = (height + margins.top + margins.bottom) * (1 - 1 / zoom) / 2
            val minY = -qY + margins.bottom - zoomBase / 5
            val maxY = qY - margins.top + zoomBase / 5
            dY = dY.clamp(minY, maxY)
        }

        override def processRightClick(target : $[Any], xy : XY) {
            // lastActions.of[Cancel].single.foreach(onClick)
        }

        def processHighlight(target : $[Any], xy : XY) {
            highlightCoordinates = |(xy)
        }

        def processTargetClick(target : $[Any], xy : XY) {
            lastActions.of[Cancel].single.foreach { a =>
                return onClick(a)
            }

            println("processTargetClick unresolved " + target)
        }

        case class Plaque(area : System)

        def makeScene(reposition : Boolean) : |[Scene] = {
            if (img("map-no-slots").complete.not | img("map-regions").complete.not | img("map-regions-select").complete.not)
                return None

            outOfPlay.clear()

            1.to(6).diff(game.board.clusters).foreach { i =>
                val mo = img("map-out-" + i)

                outOfPlay.add(Sprite($(ImageRect(new RawImage(mo), Rectangle(0, 0, mo.width, mo.height), 1.0)), $))(0, 0)
            }

            1.to(6).diff(game.board.clusters).intersect($(3, 4)).$.starting.foreach { i =>
                val am = img("map-ambitions-" + i)

                outOfPlay.add(Sprite($(ImageRect(new RawImage(am), Rectangle(0, 0, am.width, am.height), 1.0)), $))(0, 0)

                if (game.chapter.in(1, 2, 3, 4, 5)) {
                    val ch = img("chapter-" + game.chapter)

                    val gl = img("goal-" + game.seating.num @@ {
                        case 2 => "33"
                        case 3 => "30"
                        case 4 => "27"
                    })

                    if (i == 3) {
                        outOfPlay.add(Sprite($(ImageRect(new RawImage(ch), Rectangle(0, 0, ch.width / 2, ch.height / 2), 1.0)), $))(1536, 1284)
                        outOfPlay.add(Sprite($(ImageRect(new RawImage(gl), Rectangle(0, 0, 112, 112), 1.0)), $))(1616, 1332)
                    }

                    if (i == 4) {
                        outOfPlay.add(Sprite($(ImageRect(new RawImage(ch), Rectangle(0, 0, ch.width / 2, ch.height / 2), 1.0)), $))(1156, 1198)
                        outOfPlay.add(Sprite($(ImageRect(new RawImage(gl), Rectangle(0, 0, 112, 112), 1.0)), $))(970, 1222)
                    }
                }
            }

            if (game.broken.any) {
                val tp = img("map-twisted-passage")

                outOfPlay.add(Sprite($(ImageRect(new RawImage(tp), Rectangle(0, 0, tp.width, tp.height), 1.0)), $))(0, 0)

                game.exit.foreach { s =>
                    game.broken.foreach { i =>
                        outOfPlay.add(Sprite($(ImageRect(new RawImage(img("map-broken-gate-" + i + "-" + s)), Rectangle(0, 0, tp.width, tp.height), 1.0)), $))(0, 0)
                    }
                }
            }
            else {
                if (campaign || Meta.easterEgg == "map-out-3")
                    outOfPlay.add(Sprite($(ImageRect(new CanvasImage(titleImage.canvas), Rectangle(1050, 966, 500, 50), 1.0)), $))(0, 0)
            }

            pieces.flush()

            case class ResourceMarker(s : System) extends Piece

            systems.reverse.foreach { s =>
                game.resources(s).%(_ => s.gate.not).foreach { r =>
                    val (x, y) = s @@ {
                        case System(1, Arrow) => (1045, 125)
                        case System(1, Crescent) => (1455, 120)
                        case System(1, Hex) => (1860, 115)
                        case System(2, Arrow) => (2100, 390)
                        case System(2, Crescent) => (2405, 585)
                        case System(2, Hex) => (2300, 880)
                        case System(3, Arrow) => (2300, 1175)
                        case System(3, Crescent) => (1965, 1315)
                        case System(3, Hex) => (1965, 1680)
                        case System(4, Arrow) => (1445, 1645)
                        case System(4, Crescent) => (1050, 1690)
                        case System(4, Hex) => (685, 1580)
                        case System(5, Arrow) => (160, 1525)
                        case System(5, Crescent) => (330, 1130)
                        case System(5, Hex) => (140, 800)
                        case System(6, Arrow) => (330, 660)
                        case System(6, Crescent) => (255, 310)
                        case System(6, Hex) => (610, 185)
                        case _ => (1150 + random(100), 800 + random(100))
                    }

                    val rect = Rectangle(-32, -32, 64, 64)
                    val hit = Rectangle(-24, -24, 48, 48)

                    pieces.addFixed(s, Figure(Free, ResourceMarker(s), 1), 0)(Sprite($(ImageRect(new RawImage(img(r.id)), rect, 1.0)), $(hit)))(x, y)
                }
            }

            val shipScale = callbacks.settings.of[ShipsSizeOption].starting @@ {
                case Some(StandardShipsSize) | None => 100
                case Some(SmallShipsSize) => 92
                case Some(SmallerShipsSize) => 84
                case Some(SmallestShipsSize) => 76
            } / 100.0

            systems.reverse.foreach { s =>
                var figures = game.at(s)
                var gates = regions.gates.get(s).|(Nil).sortBy(_.y)

                var extra : $[Figure] = $

                if (game.campaign.not && game.chapter == 0 && s.none) {
                    game.starting.lazyZip(factions).foreach { case ((a, b, c), f) =>
                        if (a == s)
                            pieces.addFixed(s, Figure(f, Agent, 1), 5)(Sprite($(ImageRect(img(f.short + "-agent-a"), 21, 66/2, 2)), $))(gates.first.x, gates.first.y)

                        if (b == s)
                            pieces.addFixed(s, Figure(f, Agent, 2), 5)(Sprite($(ImageRect(img(f.short + "-agent-b"), 21, 66/2, 2)), $))(gates.first.x, gates.first.y)

                        if (c.has(s))
                            pieces.addFloat(s, Figure(f, Agent, 3), 5, true)(Sprite($(ImageRect(img(f.short + "-agent-c"), 21, 66/2, 2)), $))
                    }
                }

                import hrf.ui.sprites._

                if (s.gate && s.cluster < 7)
                    if (game.laws.has(Ceasefires))
                        if (game.ceasefire.has(s.cluster))
                            pieces.addFloat(s, Figure(Neutrals, Agent, s.cluster), 2, true)(Sprite($(ImageRect(img("ceasefire-peace"), 64, 64, 0.6)), $(Rectangle(-32, -32, 64, 64))))
                        else
                            pieces.addFloat(s, Figure(Neutrals, Agent, s.cluster), 2, true)(Sprite($(ImageRect(img("ceasefire-war"), 64, 64, 0.6)), $(Rectangle(-32, -32, 64, 64))))

                var l = (extra ++ figures ++ 1.to(game.freeSlots(s))./(i => Figure(Free, Slot, systems.indexOf(s) * 10 + i)))

                l = l.sortBy(u => u.piece @@ {
                    case Flagship => 0
                    case Ship if u.faction == Empire => 1
                    case Blight => 2
                    case Ship => 3
                    case _ => 999
                })

                if (reposition && false)
                    l = l.shuffle

                l.foreach { p =>
                    def prefix = p.faction.as[Faction]./(_.short.toLowerCase + "-") || (p.faction == Empire).?("imperial-") || (p.faction == Free).?("free-") | ""

                    val target = false

                    val selected = extra.has(p)

                    val status = (p.piece != Slot).??(p.faction.damaged.has(p).??("-damaged"))

                    val a = p.piece match {
                        case Slot => $(ImageRect(img("city-empty"), 61, 61, 1.0 + 0.4 * selected.??(1)).copy(alpha = 0.4))
                        case City => $(ImageRect(img(prefix + "city" + game.seats.values.$.has(p).??("-seat") + status), 61, 61, 1.0 + 0.4 * selected.??(1)))
                        case Starport => $(ImageRect(img(prefix + starport + status), 61, 61, 1.0 + 0.4 * selected.??(1)))
                        case Ship => $(ImageRect(img(prefix + "ship" + status), 97, 71, shipScale + 0.4 * selected.??(1)))
                        case Flagship => $(ImageRect(img(prefix + "flagship" + status), 66, 170, shipScale + 0.4 * selected.??(1)))
                        case Blight => $(ImageRect(img("blight" + status), 43, 79, 1.0 + 0.4 * selected.??(1)))
                        case Banner => $(ImageRect(img("banner" + status), 64, 64, 0.76))
                        case Bunker => $(ImageRect(img("bunker" + status), 64, 64, 0.76))
                        case Portal => $(ImageRect(img("portal"), 64, 64, 0.6))
                        case ClueRight => $(ImageRect(img("clue-right"), 64, 64, 0.6))
                        case ClueWrong => $(ImageRect(img("clue-wrong"), 64, 64, 0.6))
                        case Agent => $(ImageRect(img(prefix + "agent" + status), 21, 66, 1.4))
                        case ProtoGolem(_) => $(ImageRect(img("golem-sleep"), 64, 64, 0.6))
                        case HammerFragment | HammerToken => $(ImageRect(img("hammer"), 64, 64, 0.6))
                        case BrokenWorld => $(ImageRect(img("broken-world"), 64, 64, 0.6))
                        case Witness => $(ImageRect(img("witness"), 64, 64, 0.6))
                        case Pilgrim => $(ImageRect(img("pilgrim"), 64, 64, 0.6))
                        case _ : Rumor if game.unrumored.has(s.cluster).not => $(ImageRect(img("rumor"), 64, 64, 0.6))
                        case FalseClueRumor => $(ImageRect(img("rumor-false-clue"), 64, 64, 0.6))
                        case ClusterCorrectRumor => $(ImageRect(img("rumor-correct-cluster"), 64, 64, 0.6))
                        case SymbolCorrectRumor => $(ImageRect(img("rumor-correct-symbol"), 64, 64, 0.6))
                    }

                    var q = p.piece match {
                        case Slot | City | Starport => Sprite(a, $(Rectangle(-60, 21, 120, 28), Rectangle(-46, -7, 92, 28), Rectangle(-32, -35, 64, 28), Rectangle(-18, -54, 36, 19)), $((s, p)))
                        case Ship => Sprite(a, $(Rectangle(-70, -10, 135, 20), Rectangle(-97, -30, 194, 20), Rectangle(5, -50, 75, 20), Rectangle(35, -70, 35, 20))./(_.scale(shipScale)), $((s, p)))
                        case _ => Sprite(a, $(a.head.rect))
                    }

                    var z = p.piece match {
                        case Flagship | Ship | Blight | Agent => 4
                        case City | Starport | Banner => 3
                        case Slot => 1
                        case _ => 2
                    }

                    if (extra.has(p)) {
                        q = q.copy(images = q.images./(i => i.copy(alpha = 0.7)), hitboxes = $)
                        pieces.addFixed(s, p, z + 8)(q)(highlightCoordinates.get.x, highlightCoordinates.get.y)
                    }
                    else
                    if (p.piece == Starport && gates.any && game.unslotted.has(p).not) {
                        gates.starting.foreach { g =>
                            pieces.addFixed(s, p, z)(q)(g.x, g.y)
                        }
                        gates = gates.dropFirst
                    }
                    else
                    if (p.piece.is[Building] && gates.any && game.unslotted.has(p).not) {
                        gates.ending.foreach { g =>
                            pieces.addFixed(s, p, z)(q)(g.x, g.y)
                        }
                        gates = gates.dropLast
                    }
                    else {
                        val xy = pieces.addFloat(s, p, z, reposition)(q)
                    }
                }
            }

            highlighted.clear()

            ambTokens.clear()

            systems.reverse.foreach { s =>
                game.overridesHard.get(s).foreach { r =>
                    val (x, y) = s @@ {
                        case System(1, Arrow) => (1045, 125)
                        case System(1, Crescent) => (1455, 120)
                        case System(1, Hex) => (1860, 115)
                        case System(2, Arrow) => (2100, 390)
                        case System(2, Crescent) => (2405, 585)
                        case System(2, Hex) => (2300, 880)
                        case System(3, Arrow) => (2300, 1175)
                        case System(3, Crescent) => (1965, 1315)
                        case System(3, Hex) => (1965, 1680)
                        case System(4, Arrow) => (1445, 1645)
                        case System(4, Crescent) => (1050, 1690)
                        case System(4, Hex) => (685, 1580)
                        case System(5, Arrow) => (160, 1525)
                        case System(5, Crescent) => (330, 1130)
                        case System(5, Hex) => (140, 800)
                        case System(6, Arrow) => (330, 660)
                        case System(6, Crescent) => (255, 310)
                        case System(6, Hex) => (610, 185)
                        case _ => (1150 + random(100), 800 + random(100))
                    }

                    ambTokens.add(Sprite($(ImageRect(new RawImage(img(r.id)), Rectangle(-32, -32, 64, 64), 1.0)), $), 1.28, 10)(x, y)
                }
            }

            1.to(6).diff(game.board.clusters).intersect($(3, 4)).$.starting.foreach { i =>
                if (i == 3) {
                    val x1 = 1724
                    val y1 = 1017

                    game.ambitionable.lift(0)./(m => ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))( 34+x1, 82+y1))
                    game.ambitionable.lift(1)./(m => ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154+x1, 82+y1))
                    game.ambitionable.lift(2)./(m => ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(274+x1, 82+y1))

                    game.declared.get(Tycoon)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2+x1, 250+y1)
                    })

                    val x2 = 1724 + 391
                    val y2 = 1017 - 359
                    game.declared.get(Tyrant)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2+x2, 430+y2)
                    })
                    game.declared.get(Warlord)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2+x2, 608+y2)
                    })
                    game.declared.get(Keeper)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2+x2, 787+y2)
                    })
                    game.declared.get(Empath)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2+x2, 967+y2)
                    })

                    if (game.factions.forall(game.states.contains)) {
                        if (game.factions.exists(_.hasGuild(MaterialCartel)).not) {
                            val n = game.availableNum(Material)
                            val p = PreludeHold(Material).num
                            0.until(n).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Material.id)), Rectangle(-32, -32, 64, 64), 1.0)), $))(1933 - 68*2, 1458 + i * 68)
                            }
                            n.until(n + p).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Material.id)), Rectangle(-32, -32, 64, 64), 0.4)), $))(1933 - 68*2, 1458 + i * 68)
                            }
                        }

                        if (game.factions.exists(_.hasGuild(FuelCartel)).not) {
                            val n = game.availableNum(Fuel)
                            val p = PreludeHold(Fuel).num
                            0.until(n).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Fuel.id)), Rectangle(-32, -32, 64, 64), 1.0)), $))(1933 - 68*1, 1458 + i * 68)
                            }
                            n.until(n + p).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Fuel.id)), Rectangle(-32, -32, 64, 64), 0.4)), $))(1933 - 68*1, 1458 + i * 68)
                            }
                        }

                        if (game.factions.exists(_.hasGuild(WeaponCartel)).not) {
                            val n = game.availableNum(Weapon)
                            val p = PreludeHold(Weapon).num
                            0.until(n).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Weapon.id)), Rectangle(-32, -32, 64, 64), 1.0)), $))(1933 + 68*0, 1458 + i * 68)
                            }
                            n.until(n + p).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Weapon.id)), Rectangle(-32, -32, 64, 64), 0.4)), $))(1933 + 68*0, 1458 + i * 68)
                            }
                        }

                        if (game.factions.exists(_.hasGuild(RelicCartel)).not) {
                            val n = game.availableNum(Relic)
                            val p = PreludeHold(Relic).num
                            0.until(n).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Relic.id)), Rectangle(-32, -32, 64, 64), 1.0)), $))(1933 + 68*1, 1458 + i * 68)
                            }
                            n.until(n + p).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Relic.id)), Rectangle(-32, -32, 64, 64), 0.4)), $))(1933 + 68*1, 1458 + i * 68)
                            }
                        }

                        if (game.factions.exists(_.hasGuild(PsionicCartel)).not) {
                            val n = game.availableNum(Psionic)
                            val p = PreludeHold(Psionic).num
                            0.until(n).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Psionic.id)), Rectangle(-32, -32, 64, 64), 1.0)), $))(1933 + 68*2, 1458 + i * 68)
                            }
                            n.until(n + p).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(Psionic.id)), Rectangle(-32, -32, 64, 64), 0.4)), $))(1933 + 68*2, 1458 + i * 68)
                            }
                        }
                    }
                }

                if (i == 4) {
                    game.ambitionable.lift(0)./(m => ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(1100, 1280))
                    game.ambitionable.lift(1)./(m => ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(1220, 1280))
                    game.ambitionable.lift(2)./(m => ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(1340, 1280))

                    game.declared.get(Tycoon)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2 + 419, 1628)
                    })
                    game.declared.get(Tyrant)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2 + 641, 1450)
                    })
                    game.declared.get(Warlord)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2 + 813, 1628)
                    })
                    game.declared.get(Keeper)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2 + 1035, 1450)
                    })
                    game.declared.get(Empath)./(l => l.indexed./{ (m, i) =>
                        ambTokens.add(Sprite($(ImageRect(new RawImage(img("ambition-values-" + m.high + "-" + m.low)), Rectangle(0, 0, 111, 125), 1.0)), $))(154 + 120*(2*i - l.num + 1)/2 + 1207, 1628)
                    })

                    if (game.factions.forall(game.states.contains)) {
                        $(
                            (Material, MaterialCartel, $((400, 1734), (332, 1734), (400, 1666), (332, 1666), (400, 1598))),
                            (Fuel, FuelCartel, $((622, 1554), (554, 1554), (622, 1486), (554, 1486), (622, 1418))),
                            (Weapon, WeaponCartel, $((918, 1306), (850, 1306), (918, 1238), (850, 1238), (918, 1170))),
                            (Relic, RelicCartel, $((1474, 1554), (1542, 1554), (1474, 1486), (1542, 1486), (1474, 1418))),
                            (Psionic, PsionicCartel, $((1646, 1734), (1714, 1734), (1646, 1666), (1714, 1666), (1646, 1598))),
                        ).foreach { case (r, c, l) =>
                            val full = game.availableNum(r)
                            val semi = PreludeHold(r).num

                            1.to(full).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img(r.id)), Rectangle(-32, -32, 64, 64), game.factions.exists(_.hasGuild(c)).?(0.2).|(1.0))), $))(l(i-1)._1, l(i-1)._2)
                            }
                            (full + 1).to(full + semi).foreach { i =>
                                ambTokens.add(Sprite($(ImageRect(new RawImage(img("nothingness")), Rectangle(-32, -32, 64, 64), 0.4)), $))(l(i-1)._1, l(i-1)._2)
                            }
                        }
                    }
                }
            }

            |(scene)
        }
    }

    def factionStatus(f : Faction) {
        val container = statuses(game.seating.indexOf(f))

        val name = hrf.HRF.flag("nonames").?(f.name).|(resources.getName(f).|(f.name))

        if (!game.states.contains(f)) {
            container.replace(Div(Div(name).styled(f), styles.smallname, xlo.pointer), resources)
            return
        }

        val initative = game.seized.%(_ == f)./(_ => DoubleDagger).||((game.factions.first == f && game.seized.none).?(Dagger)).|("")
        val bonus = (f.pooled(City) < 1).?(DoubleAsterisk).||((f.pooled(City) < 2).?(LowAsterisk)).|("")

        val title = (initative.styled(styles.title)(styles.initative) ~ name).div.styled(f).div(styles.smallname)(xlo.pointer)
        val hand = Hint("Hand: " + f.hand.num + " cards",
            (f.hand.none && f.taking.none).?("~~~".txt ~ Image("card-back-small", styles.fund, xlo.hidden)).|(
                (f.hand.num > 666).?(
                    (f.hand.num / 5).times(Image("card-back-5", styles.fund)) ~ (f.hand.num % 5).times(Image("card-back-small", styles.fund))
                ).|(
                    f.hand.$.count(_.suit != Faithful).times(Image("card-back-small", styles.fund)).merge ~
                    f.hand.$.count(_.suit == Faithful).times(Image("card-back-small-faithful", styles.fund)).merge
                ) ~
                f.taking./(d => Image(d.id, styles.fund)).merge ~
                f.takingBlind./(_ => Image("card-back-small-faithful", styles.fund)).merge
            ).spn(styles.hand)
        )

        val powerHand = (bonus.styled(styles.bonus) ~ f.power.power ~ " ".pre ~ hand).div(xstyles.larger110)

        val outrage = f.outraged./(r => Image(r.name + "-outrage", styles.token)).merge.div(styles.outrageLine)

        val keys = (f.displayable./(k => Image("half-keys-" + k.raidable.|("4"), styles.token)) ++ f.overflow.num.times(Image("half-keys-1", styles.token)(xstyles.hidden))).merge.div(styles.keyLine)
        val res = (f.displayable./(k => Image(k.$.option./(_.id).|("nothingness"), styles.token)).merge ~ f.overflow./(r => Image(r.id, styles.token))).div

        val flagship = f.flagship./(_ => (
            Flagship.armors(f).exists(_.any).?(
                Flagship.armors(f)   ./(q => q./(u => Image(f.short + "-" + u.piece.is[City.type].?("city").|("starport") + u.damaged.??("-damaged"), styles.building)).some./(_.merge).|(Image("city-empty", styles.building))).join(" ").div
            ) ~ Flagship.functions(f)./(q => q./(u => Image(f.short + "-" + u.piece.is[City.type].?("city").|("starport") + u.damaged.??("-damaged"), styles.building)).some./(_.merge).|(Image("city-empty", styles.building))).join(" ").div
        ).div(styles.flagship).pointer.onClick.param((Flagship, f)))

        val pieces = (
            (
                (5 - f.pooled(City)).hlb.styled(xstyles.smaller85) ~ "×" ~ Image(f.short + "-city", styles.building) ~ " " ~
                (5 - f.pooled(Starport)).hlb.styled(xstyles.smaller85) ~ "×" ~ Image(f.short + "-" + "starport", styles.building)
            ).& ~ " " ~
            (
                (15 - f.pooled(Ship)).hlb.styled(xstyles.smaller85) ~ "×" ~ Image(f.short + "-ship", styles.ship) ~ " " ~
                (10 - f.pooled(Agent) - f.outraged.num).hlb.styled(xstyles.smaller85) ~ "×" ~ Image(f.short + "-agent", styles.ship)
            ).&
        ).div

        val trophies = f.trophies./( u =>
            Image(game.showFigure(u, 1).image, u.piece.is[Building].?(styles.building).|(styles.ship))
        )./(_.&).join(" ").div

        val captives = f.captives./( u =>
            Image(game.showFigure(u).image, u.piece.is[Building].?(styles.building).|(styles.ship))
        )./(_.&).join(" ").div

        val favors = f.favors./( u =>
            Image(u.faction.short + "-" + u.piece.name + "-star", styles.ship)
        )./(_.&).join(" ").div

        val leader = f.leader.$./(l => l.elem.div(xstyles.smaller75)(styles.cardName).pointer.onClick.param(l)).merge
        val subtitle0 = game.campaign.?((f.primus.?("\u0158").||(f.regent.?("\u0158")).|("\u00D8")).styled(f.regent.?(Empire).|(Free)).spn(styles.title)(styles.cardName).pointer.onClick.param(CardId(f.regent.?("aid06a").|("aid06b"))))
        val fate = f.fates.single.%(_ => factions.forall(_.fates.num < 2))./(l => subtitle0 ~ " " ~ l.name.styled(f)(styles.title).pointer.onClick.param(l) ~ f.objective./(o => (Image("objective", styles.tokenObj) ~ (f.progress > 0).?(f.progress.hh).|(Checkmark.hlb).spn(styles.title)).pointer.onClick.param(o)))./(_.div(xstyles.smaller75)(styles.cardName))
        val failed = f.failed.lastOption.%(_ => f.fates.none)./(l => subtitle0 ~ " " ~ l.name.styled(f)(styles.title).spn(styles.used).pointer.onClick.param(l) ~ (Image("objective", styles.tokenObj) ~ Failmark.spn(styles.title)(styles.hit)).pointer.onClick.param(f.objective))./(_.div(xstyles.smaller75)(styles.cardName))

        val lores = f.lores./(l => (l.elem.div(xstyles.smaller75)(styles.cardName) ~ (l @@ {
            case GolemHearth => GolemHearthSlots./(t => Image(t.id, styles.tokenTopPlus)).merge
            case PirateHoard => PirateHoardSlots./(t => Image(t.id, styles.tokenTopPlus)).merge
            case WellOfEmpathy => WellOfEmpathySlots./(t => Image(t.id, styles.tokenTopPlus)).merge
            case GreenVault => GreenVaultSlots.$.of[ResourceToken]./(r => Image(r.resource, styles.tokenTop)).some./(_.merge.div(styles.merchantLeague)).|(Empty)
            case WardensLevy => WardensLevy.$./(u => Image(game.showFigure(u).image, u.piece.is[Building].?(styles.building).|(styles.ship)))./(_.&).join(" ").div
            case BlightHunger => BlightHunger.$./(u => Image(game.showFigure(u).image, u.piece.is[Building].?(styles.building).|(styles.ship)))./(_.&).join(" ").div
            case _ => Empty
        })).pointer.onClick.param(l)).merge

        val abilities = f.abilities./(l => l.@@{
            case HammerFragments => l.elem ~ " " ~ FatePieces(PlanetBreaker).$.piece(HammerFragment).num.hh ~ "/" ~ "6".hlb
            case Pilgrims => l.elem ~ " " ~ FatePieces(Pathfinder).$.piece(Pilgrim).num.hh ~ "/" ~ "6".hlb
            case BreakingWorlds => l.elem ~ " " ~ BreakingWorlds.$./(u => Image(game.showFigure(u).image, u.piece.is[Building].?(styles.building).|(styles.ship)))./(_.&).join(" ").div
            case _ => l.elem
        }.div(xstyles.smaller75)(styles.cardName).pointer.onClick.param(l) ~ l.@@{
            case SpreadingTheFaith => BelieverCourtDeck.$./(c => Image(c.id, styles.fund)).merge.pointer.onClick.param(BelieverCourtDeck)
            case _ => Empty
        }).merge

        val subtitles = f.primus.?("First Regent".styled(styles.title).div(xstyles.smaller75)(styles.cardName)(styles.firstRegent).pointer.onClick.param(None)) ~
            f.primus.?((ImperialTrust.some./(_./(t => Image(t.id, styles.tokenTop)).merge).|(Image("nothingness", styles.tokenTop, xlo.hidden))).div(styles.imperialTrust)(styles.dashedRect))

        val loyal = f.loyal.$.of[GuildCard]./(c => ((Image(c.suit.name, styles.tokenTop) ~ c.elem ~ (Image("keys-" + (c.keys < 999).?(c.keys).|("x"), styles.tokenTop))).div(xstyles.smaller75)(styles.hand) ~ (c.effect @@ {
            case MaterialCartel => game.availableNum(Material).times(Image(Material.name, styles.tokenTop)).some./(_.merge.div(styles.materialCartel)(styles.dashedRect))
            case FuelCartel => game.availableNum(Fuel).times(Image(Fuel.name, styles.tokenTop)).some./(_.merge.div(styles.fuelCartel)(styles.dashedRect))
            case WeaponCartel => game.availableNum(Weapon).times(Image(Weapon.name, styles.tokenTop)).some./(_.merge.div(styles.weaponCartel)(styles.dashedRect))
            case RelicCartel => game.availableNum(Relic).times(Image(Relic.name, styles.tokenTop)).some./(_.merge.div(styles.relicCartel)(styles.dashedRect))
            case PsionicCartel => game.availableNum(Psionic).times(Image(Psionic.name, styles.tokenTop)).some./(_.merge.div(styles.psionicCartel)(styles.dashedRect))
            case MerchantLeague => MerchantLeagueSlots.$.of[ResourceToken]./(r => Image(r.resource, styles.tokenTop)).some./(_.merge.div(styles.merchantLeague)(styles.dashedRect))
            case ArsenalKeepers => ArsenalKeepersSlots.$.of[ResourceToken]./(r => Image(r.resource, styles.tokenTop)).some./(_.merge.div(styles.weaponCartel)(styles.dashedRect))
            case _ => None
        })).pointer.onClick.param(c)).merge

        val play =
            f.played.starting./{ d => f.displayed./(_.suit).use(suit =>
                f.zeroed.?(Image("zeroed", styles.plaque)) ~
                (f.lead && f.zeroed.not).?(Image(suit.get.name + "-number-" + d.strength, styles.plaque)) ~
                f.mirror.?(Image("event-number", styles.plaque)) ~
                f.surpass.?(Image(suit.get.name + "-number-" + d.strength, styles.plaque)) ~
                f.pivot.?(Image(suit.get.name + "-number-" + d.strength, styles.plaque)) ~

                f.lead.?(Image(suit.get.name + "-pips-" + d.pips, styles.plaque)) ~
                f.mirror.?(Image(game.lead.get.suit + "-pips-" + game.lead.get.pips, styles.plaque)) ~
                f.surpass.?(Image(suit.get.name + "-pips-" + d.pips, styles.plaque)) ~
                f.pivot.?(Image(suit.get.name + "-pips-pivot", styles.plaque)) ~

                f.mirror.not.?(Image(suit.get.name + "-plaque-new", styles.plaque)) ~
                f.mirror.?(Image(game.lead.get.suit + "-plaque-new", styles.plaque))
            ).div(styles.plaqueContainer) }.||(
            f.blind.starting./ { d =>
                (Image("hidden" + (d.suit == Faithful).??("-faithful"), styles.plaque) ~ Image(game.lead.get.suit + "-pips-copy", styles.plaque) ~ Image(game.lead.get.suit + "-plaque-new", styles.plaque)).div(styles.plaqueContainer)
            }).|(
                Empty
            )

        val seized = game.seized.%(_ == f)./(_ => "Seized Initative".hl.div(styles.title)(xstyles.smaller50)).||((game.factions(0) == f && game.seized.none).?("Initative".hl.div(styles.title)(xstyles.smaller50))).|(Empty)

        val content = ((title ~ powerHand ~ leader ~ fate ~ failed ~ favors ~ abilities ~ lores ~ subtitles ~ outrage ~ keys ~ res ~ flagship ~ loyal ~ trophies ~ captives).div ~ "~".txt.div(xstyles.hidden) ~ play).div(styles.statusUpper)(xlo.flexVX)(ExternalStyle("hide-scrollbar")).pointer.onClick.param(f) ~
            play.div(styles.play)

        container.replaceCached(content.toString, fixActionElem(content), resources, {
            case f : Faction => onFactionStatus(f, false)
            case x => onClick(x)
        })

        if (f == game.current && game.isOver)
            container.attach.parent.style.background = f @@ {
                case Red => "#680016"
                case Yellow => "#684f19"
                case Blue => "#05274c"
                case White => "#666666"
            }
        else
        if (f == game.current)
            container.attach.parent.style.outline = "2px solid #aaaaaa"
        else
        if (game.highlightFaction.has(f))
            container.attach.parent.style.outline = "2px dashed #aaaaaa"
        else
            container.attach.parent.style.outline = ""
    }

    def onIntermissionReport(report : IntermissionReport) {
        def desc(l : Any*) = game.desc(l : _*).div

        showOverlay(overlayScrollX((
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            "INTERMISSION REPORT".styled(styles.title)(styles.intermission).styled(xstyles.larger150).div ~
            ("Between Acts " ~ (report.act - 1).times("I".hlb) ~ " and " ~ report.act.times("I".hlb)).hh.div ~
            (report.act == 3).?("INCOMPLETE".styled(xstyles.error)(xstyles.larger150)) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            "Players".styled(styles.titleW).larger.larger.larger.larger ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            report.players./~((f, r) =>
                $(
                    f.elem.larger.larger.larger.styled(xstyles.bold),
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    desc("Power".styled(f).larger) ~
                    HGap ~
                    HGap ~
                    desc(r.power.power.larger.larger) ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    desc("Fate".styled(f).larger) ~
                    HGap ~
                    HGap ~
                    desc(Image(r.fate.id, styles.fateCard, xlo.inlineBlock), r.past.reverse.but(r.fate)./(x => Image(x.id, styles.fateCard, xlo.inlineBlock, xstyles.unavailableCard))) ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    desc(r.regent.not.?("Outlaw".styled(Free)).|((r.primus.??("First ") + "Regent").styled(Empire)).larger) ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    (r.favors.any).? {
                        desc("Favors".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        desc(r.favors./(u => Image(u.faction.short + "-" + u.piece.name + "-star", (u.piece == Ship).?(styles.ship3x).|(styles.ship3x)))) ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    } ~
                    (r.outraged.any).?{
                        desc("Outrage".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        desc(f.outraged./(r => Image(r.name + "-outrage", styles.token3x))) ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    }.|{
                        desc("No Outrage".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    } ~
                    (r.resources.any).?{
                        desc("Resources".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        desc(r.resources./(k => Image(k.id, styles.token3x))) ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    }.|{
                        desc("No Resources".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    } ~
                    (r.abilities.any).?{
                        desc("Abilities".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        r.abilities./(l => OnClick(l, Div(l.img, styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer, xstyles.larger150))).merge.div ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    } ~
                    (r.lores.any).?{
                        desc("Lore".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        r.lores./(l => OnClick(l, Div(l.img, styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer, xstyles.larger150))).merge.div ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    } ~
                    (r.guilds.any).?{
                        desc("Guild Cards".styled(f).larger) ~
                        HGap ~
                        HGap ~
                        r.guilds./(l => OnClick(l, Div(l.img, styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer, xstyles.larger150))).merge.div ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap ~
                        HGap
                    } ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap
                )
            ).merge ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            "Court Cards".styled(styles.titleW).larger.larger.larger.larger ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            desc(report.court./(c => OnClick(c, Div(Image(c.id, styles.courtCard), styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer)))) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            "Deck Cards".styled(styles.titleW).larger.larger.larger.larger ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            desc(report.deck./(c => OnClick(c, Div(Image(c.id, styles.courtCard), styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer)))) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            "Systems".styled(styles.titleW).larger.larger.larger.larger ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            report.units./~((s, ll) =>
                $(
                    s.elem.larger.styled(xstyles.bold),
                    HGap,
                    (s.gate.not || s.$.cities.any).?(game.desc(game.resources(s)./(r => ResourceRef(r, None)).intersperse(" ")).div).|(Empty),
                    HGap
                ) ++
                ll.groupBy(_.faction).values.$.sortBy(l => l.buildings.num * 20 + l.ships.num).reverse./(_.sortBy(_.piece.is[Building].not)./(u => Image(game.showFigure(u, 0).image, (u.piece == Ship).?(styles.ship3x).|(styles.token3x))))./(game.desc(_).div(styles.figureLine)) ++
                $(game.desc(game.freeSlots(s).times(Image("city-empty", styles.token3x))).div(styles.figureLine)) ++
                $(
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap,
                    HGap
                )
            ).merge
        ).div(xlo.flexvcenter)(styles.infoStatus)), onClick)
    }

    def onFactionStatus(implicit f : Faction, isMore : Boolean) : Unit = {
        def desc(l : Any*) = game.desc(l : _*).div
        def more(l : Any*) = isMore.?(desc(l : _*))
        def less(l : Any*) = isMore.not.?(desc(l : _*))
        def moreGap = isMore.?(HGap)
        def lessGap = isMore.not.?(HGap)

        def info() =
            less(("More Info".hh).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(new CustomStyle(rules.width("60ex"))(new StylePrefix("test")){}).pointer.onClick.param(f, !isMore)) ~
            more(("Less Info".hh).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(new CustomStyle(rules.width("60ex"))(new StylePrefix("test")){}).pointer.onClick.param(f, !isMore))

        val ww = f.spendable.num + f.overflow.num

        showOverlay(overlayScrollX((
            HGap ~
            HGap ~
            HGap ~
            f.elem.larger.larger.larger.styled(xstyles.bold) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            desc("Power".hl.larger) ~
            more("(victory points)") ~
            HGap ~
            HGap ~
            desc(f.power.power.larger.larger) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            (f.leader.any).?{
                desc("Leader".hl.larger) ~
                HGap ~
                HGap ~
                desc(f.leader./(l => OnClick(l, l.img))) ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap
            } ~
            (f.lores.any).?{
                desc("Lore".hl.larger) ~
                HGap ~
                HGap ~
                f.lores./(l => OnClick(l, Div(l.img, styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer))).merge.div ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap
            } ~
            desc("Cards".hl.larger) ~
            HGap ~
            desc(f.hand.num.times(Image("card-back", styles.token3x)), f.taking./(d => Image(d.id, styles.token3x)), f.takingBlind./(d => Image("card-back-small-faithful", styles.token3x))) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            (f.outraged.any).?{
                desc("Outrage".hl.larger) ~
                more("(resources of these type can't be used for actions)") ~
                HGap ~
                HGap ~
                desc(f.outraged./(r => Image(r.name + "-outrage", styles.token3x))) ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap
            } ~
            desc("Resources".hl.larger) ~
            HGap ~
            HGap ~
            desc(f.displayable./(k => Image("keys-" + k.raidable.|("x"), styles.token3x)) ++ f.overflow.num.times(Image("discard-resource", styles.token3x))) ~
            HGap ~
            desc(f.displayable./(k => Image(k.option./(_.id).|("nothingness"), styles.token3x)) ++ f.overflow.$./(r => Image(r.id, styles.token3x))) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            desc("Cities".hl.larger) ~
            HGap ~
            HGap ~
            desc(systems./~(f.at(_).cities)./(u => Image(u.faction.short + "-city" + f.damaged.has(u).??("-damaged"), styles.token3x)),
                $(
                    Image("building-empty-keys-1", styles.token3x),
                    Image("building-empty-keys-2", styles.token3x),
                    Image("building-empty-keys-1-3", styles.token3x),
                    Image("building-empty-plus-2", styles.token3x),
                    Image("building-empty-plus-3", styles.token3x),
                ).drop(5 - f.pooled(City))
            ) ~
            (f.pooled(City) < 2).?(desc("Total bonus for won ambitions", "+" ~ ((f.pooled(City) < 2).??(2) + (f.pooled(City) < 1).??(3)).power)) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            desc("Starports".hl.larger) ~
            HGap ~
            HGap ~
            desc(systems./~(f.at(_).starports)./(u => Image(u.faction.short + "-" + starport + f.damaged.has(u).??("-damaged"), styles.token3x)), f.pooled(Starport).times(Image(starport + "-empty", styles.token3x))) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            desc("Ships".hl.larger) ~
            HGap ~
            HGap ~
            (systems./~(f.at(_).ships)./(u => Image(u.faction.short + "-ship" + f.damaged.has(u).??("-damaged"), styles.ship3x)) ++ f.pooled(Ship).times(Image("ship-empty", styles.ship3x))).grouped(5)./(desc) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            desc("Agents".hl.larger) ~
            HGap ~
            HGap ~
            desc(game.market./~(m => Influence(m.index).$).%(_.faction == f)./(u => Image(u.faction.short + "-agent", styles.ship3x)), f.pooled(Agent).times(Image("agent-empty", styles.ship3x))) ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            fixActionElem(f.trophies.any.? {
                desc("Trophies".hl.larger) ~
                HGap ~
                HGap ~
                f.trophies./(u => Image(game.showFigure(u, 1).image, (u.piece == Ship).?(styles.ship3x).|(styles.ship3x))).grouped(5)./(desc) ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap
            }) ~
            (f.captives.any).? {
                desc("Captives".hl.larger) ~
                HGap ~
                HGap ~
                desc(f.captives./(u => Image(game.showFigure(u).image, (u.piece == Ship).?(styles.ship3x).|(styles.ship3x)))) ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap
            } ~
            (f.loyal.any).? {
                desc("Guild Cards".hl.larger) ~
                HGap ~
                f.loyal./(c => OnClick(c, Div(Image(c.id, styles.courtCard), styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer))).merge.div ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap
            } ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            info() ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap ~
            HGap
        ).div(xlo.flexvcenter)(styles.infoStatus)), {
            case (f : Faction, more : Boolean) => onFactionStatus(f, more)
            case (c : CourtCard) => onClick(c)
            case (l : Leader) => onClick(l)
            case (l : Lore) => onClick(l)
            case _ =>
                overlayPane.invis()
                overlayPane.clear()
        })
    }

    def updateStatus() {
        0.until(arity).foreach { n =>
            factionStatus(game.seating(n))
        }

        if (overlayPane.visible)
            overlayPane.vis()
        else
            overlayPane.invis()

        map.draw()
        court.draw()
        ambitions.foreach(_.draw())
    }

    val layoutZoom = 0.49 * 0.88

    def layouts = $(Layout("base",
        $(
            BasicPane("status", 24, 26, Priorities(top = 3, left = 2, maxXscale = 1.4, maxYscale = 1.8, grow = 1)),
            BasicPane("court", 18*(4 + campaign.??(1)), 26*(1 + campaign.??(1)*0.0620), Priorities(top = 3, right = 3, maxXscale = 1.0, maxYscale = 1.0, grow = -2)),
            BasicPane("log", 38, 16 + callbacks.settings.has(ExpandedLogPane).??(32), Priorities(right = 1)),
            BasicPane("map-small", 71, 50, Priorities(top = 2, left = 1, right = -1, grow = 11, maxYscale = 4.0, maxXscale = 4.0)),
            BasicPane("action-a", 64, 36, Priorities(bottom = 1, right = 3, grow = 1, maxYscale = 2.0, maxXscale = 2.0)),
            BasicPane("action-b", 50, 47, Priorities(bottom = 1, right = 3, grow = 1, maxXscale = 1.2)),
            // BasicPane("action-c", 60, 40, Priorities(bottom = 1, right = 3, grow = 1)),
        ).++(
            campaign.?(BasicPane("ambitions", 16, 60, Priorities(right = -1, left = 1, grow = -4, maxXscale = 1.0)))
        )
       ./(p => p.copy(kX = p.kX * layoutZoom, kY = p.kY * layoutZoom))
    ))./~(l =>
        l.copy(name = l.name + "-fulldim", panes = l.panes./{
            case p : BasicPane if p.name == "map-small" => FullDimPane(p.name, p.kX, p.kY, p.pr)
            case p => p
        }, boost = 1.2) ::
        l.copy(name = l.name + "-plus20", panes = l.panes./{
            case p : BasicPane if p.name == "map-small" => BasicPane(p.name, p.kX * 1.2, p.kY * 1.2, p.pr)
            case p => p
        }, boost = 1.1) ::
        l.copy(name = l.name + "-normal") ::
        $
    )./~(l =>
        callbacks.settings.has(VerticalFactionPanes).not.$(
            l.copy(name = l.name + "-horizontal", boost = l.boost * 1.02, panes = l.panes./{
                case p : BasicPane if p.name == "status" => p.copy(name = "status-horizontal", kX = p.kX * arity)
                case p => p
            })
        ) ++
        callbacks.settings.has(HorizontalFactionPanes).not.$(
            l.copy(name = l.name + "-vertical", panes = l.panes./{
                case p : BasicPane if p.name == "status" => p.copy(name = "status-vertical", kY = p.kY * arity)
                case p => p
            })
        )
    )./~(l =>
        l.copy(name = l.name + "-actionA", panes = l.panes./~{
            case p : BasicPane if p.name == "action-a" => Some(p.copy(name = "action"))
            case p : BasicPane if p.name == "action-b" => None
            case p : BasicPane if p.name == "action-c" => None
            case p => Some(p)
        }) ::
        l.copy(name = l.name + "-actionB", panes = l.panes./~{
            case p : BasicPane if p.name == "action-a" => None
            case p : BasicPane if p.name == "action-b" => Some(p.copy(name = "action"))
            case p : BasicPane if p.name == "action-c" => None
            case p => Some(p)
        }) ::
        // l.copy(name = l.name + "-actionC", panes = l.panes./~{
        //     case p : BasicPane if p.name == "action-a" => None
        //     case p : BasicPane if p.name == "action-b" => None
        //     case p : BasicPane if p.name == "action-c" => Some(p.copy(name = "action"))
        //     case p => Some(p)
        // }) ::
        $
    )

    def layouter = Layouter(layouts,
    _./~{
        case f if f.name == "action" => $(f, f.copy(name = "undo"), f.copy(name = "settings"))
        case f if f.name == "status-horizontal" => 1.to(arity)./(n => f.copy(name = "status-" + n, x = f.x + ((n - 1) * f.width  /~/ arity), width  = (n * f.width  /~/ arity) - ((n - 1) * f.width  /~/ arity)))
        case f if f.name == "status-vertical"   => 1.to(arity)./(n => f.copy(name = "status-" + n, y = f.y + ((n - 1) * f.height /~/ arity), height = (n * f.height /~/ arity) - ((n - 1) * f.height /~/ arity)))
        case f => $(f)
    },
    x => x,
    ff => ff :+ Fit("map-small-overlay", ff./(_.x).min, ff./(_.y).min, ff./(_.right).max - ff./(_.x).min, ff./(_.bottom).max - ff./(_.y).min))

    val settingsKey = Meta.settingsKey

    def layoutKey = "v" + 5 + "." +
        campaign.?("campaign.").|("base.") +
        callbacks.settings.has(VerticalFactionPanes).??("factions-vertical.") +
        callbacks.settings.has(HorizontalFactionPanes).??("factions-horizontal.") +
        callbacks.settings.has(ExpandedLogPane).??("log-expanded.") +
        "arity-" + arity

    def overlayScrollX(e : Elem) = overlayScroll(e)(styles.seeThroughInner).onClick
    def overlayFitX(e : Elem) = overlayFit(e)(styles.seeThroughInner).onClick

    def showOverlay(e : Elem, onClick : Any => Unit) {
        overlayPane.vis()
        overlayPane.replace(e, resources, onClick, _ => {}, _ => {})
    }

    override def onClick(a : Any) = a @@ {
        case ("notifications", Some(f : Faction)) =>
            shown = $
            showNotifications($(f))

        case ("notifications", None) =>
            shown = $
            showNotifications(game.factions)

        case ImperialCouncilDecided =>
            showOverlay(overlayScrollX(game.desc(Image(ImperialCouncilDecided.id, styles.artwork), Image(ImperialCouncilInSession.id, styles.artwork, xstyles.unavailableCard)).div).onClick, onClick)

        case ImperialCouncilInSession =>
            showOverlay(overlayScrollX(game.desc(Image(ImperialCouncilInSession.id, styles.artwork), Image(ImperialCouncilDecided.id, styles.artwork, xstyles.unavailableCard)).div).onClick, onClick)

        case card : DeckCard =>
            showOverlay(overlayFitX(Image(card.id, styles.artwork)).onClick, onClick)

        case card : CourtCard =>
            showOverlay(overlayFitX(Image(card.id, styles.artwork)).onClick, onClick)

        case fate : Fate =>
            showOverlay(overlayFitX(Image(fate.id, styles.artwork)).onClick, onClick)

        case leader : Leader =>
            showOverlay(overlayFitX(Image(leader.id, styles.artwork)).onClick, onClick)

        case lore : Lore =>
            showOverlay(overlayFitX(Image(lore.id, styles.artwork)).onClick, onClick)

        case PolicyOfPeace =>
            showOverlay(overlayScrollX(game.desc(
                Image(PolicyOfPeace.id, styles.artwork),
                Image(PolicyOfEscalation.id, styles.artwork, xstyles.unavailableCard),
                Image(PolicyOfWar.id, styles.artwork, xstyles.unavailableCard),
            ).div).onClick, onClick)

        case PolicyOfEscalation =>
            showOverlay(overlayScrollX(game.desc(
                Image(PolicyOfEscalation.id, styles.artwork),
                Image(PolicyOfWar.id, styles.artwork, xstyles.unavailableCard),
                Image(PolicyOfPeace.id, styles.artwork, xstyles.unavailableCard),
            ).div).onClick, onClick)

        case PolicyOfWar =>
            showOverlay(overlayScrollX(game.desc(
                Image(PolicyOfWar.id, styles.artwork),
                Image(PolicyOfPeace.id, styles.artwork, xstyles.unavailableCard),
                Image(PolicyOfEscalation.id, styles.artwork, xstyles.unavailableCard),
            ).div).onClick, onClick)

        case edict : Edict =>
            showOverlay(overlayFitX(Image(edict.id, styles.artwork)).onClick, onClick)

        case objective : Objective =>
            showOverlay(overlayFitX(Image(objective.id, styles.artwork)).onClick, onClick)

        case ability : Ability =>
            showOverlay(overlayFitX(Image(ability.id, styles.artwork)).onClick, onClick)

        case law : Law =>
            showOverlay(overlayFitX(Image(law.id, styles.artwork)).onClick, onClick)

        case card : CardId =>
            showOverlay(overlayFitX(Image(card.id, styles.artwork)).onClick, onClick)

        case $(f : Faction, x) =>
            onClick(x)

        case (Flagship, f : Faction) =>
            flagship = |(f)

            court.draw()

        case Flagship =>
            flagship = None

            court.draw()

        case "discourt" =>
            showOverlay(overlayScrollX(Div("Court Cards Discard Pile".hh) ~
                game.discourt./(c => OnClick(c, Div(Image(c.id, styles.courtCard), styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer))).merge ~
                Break ~ "Court Cards Draw Deck".hh ~ SpacedDash ~ game.court.num.hlb ~ (" card".s(game.court.num)) ~ Break ~
                options.has(DebugInterface).?(
                    Break ~ "COURT CONTENT DEBUG" ~ Break ~
                    game.court./(c => OnClick(c, Div(Image(c.id, styles.courtCard), styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer))).merge
                )
            ).onClick, onClick)

        case "discard" =>
            showOverlay(overlayScrollX(Div("Action Cards Discard Pile".hh) ~
                options.has(DebugInterface).?(Break ~ "DECK CONTENT DEBUG" ~ Break ~
                game.deck./(c => OnClick(c, Div(Image(c.id, styles.courtCard), styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer))).merge)
            ).onClick, onClick)

        case "seen" =>
            showOverlay(overlayScrollX(Div("Played Action Cards".hl) ~
                game.seen.groupBy(_._1).$.sortBy(_._1)./{ case (n, l) =>
                    Div("Round " ~ n.hh) ~
                    l./{ case (_, f, d) =>
                        OnClick(d, Div(Image(d./(_.id).|("card-back"), styles.card), xstyles.choice, xstyles.xx, styles.cardI, elem.borders.get(f), styles.inline, xlo.pointer))
                    }.merge
                }.merge
            ).onClick, onClick)

        case BelieverCourtDeck =>
            showOverlay(overlayScrollX(Div("Believer Cards Stack".hh) ~
                BelieverCourtDeck.$./(c => OnClick(c, Div(Image(c.id, styles.card), styles.cardX, xstyles.xx, styles.inline, styles.nomargin, xlo.pointer))).merge
            ).onClick, onClick)

        case "readout" =>
            showOverlay(overlayScrollX((
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                HGap ~
                systems./~(s =>
                    $(
                        game.factions.%(_.rules(s)).single.||(campaign.??(Empire.rules(s)).?(Empire))./(f => s.name.styled(f) ~ s.symbol.smb.styled(f)).|(s.darkElem).larger.styled(xstyles.bold),
                        HGap,
                        (s.gate.not || s.$.cities.any).?(game.desc(game.resources(s)./(r => ResourceRef(r, None)).intersperse(" ")).div).|(Empty),
                        HGap
                    ) ++
                    s.$.groupBy(_.faction).values.$.sortBy(l => l.buildings.num * 20 + l.ships.num).reverse./(_.sortBy(_.piece.is[Building].not)./(u => /*Serialize.write(u) ~*/ fixActionElem(Image(game.showFigure(u).image, (u.piece == Ship).?(styles.ship3x).|(styles.token3x)))))./(game.desc(_).div(styles.figureLine)) ++
                    $(game.desc(game.freeSlots(s).times(Image("city-empty", styles.token3x))).div(styles.figureLine)) ++
                    $(
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap,
                        HGap
                    ) ++
                    $
                )
            ).div(xlo.flexvcenter)(styles.infoStatus)), onClick)

        case "readout" =>
            showOverlay(overlayScrollX((
                systems./(s =>
                    s.elem.div ~
                    HGap ~
                    HGap ~
                    (game.at(s)./(u => Image(u.faction.short + "-" + u.piece.name + "-damaged", (u.piece == Ship).?(styles.ship3x).|(styles.ship3x)))).merge.div ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap ~
                    HGap
                ).merge ~
                "".hl.div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(new CustomStyle(rules.width("60ex"))(new StylePrefix("test")){}).pointer.onClick
            ).div(xlo.flexvcenter)(styles.infoStatus)), onClick)

        case report : IntermissionReport =>
            onIntermissionReport(report)

        case action : Action if lastThen != null =>
            clearOverlay()

            highlightAssassinate = $
            highlightFire = $
            highlightBuy = $
            highlightMove = $
            highlightRemoveTrouble = $
            highlightSpreadTrouble = $
            highlightPlaceMinion = Map()
            highlightBuild = Map()

            val then = lastThen
            lastThen = null
            lastActions = $
            keys = $

            asker.clear()

            then(action.as[UserAction].||(action.as[ForcedAction]./(_.as("Do Action On Click"))).|(throw new Error("non-user non-forced action in on click handler")))

        case Nil =>
            clearOverlay()

        case Left(x) => onClick(x)
        case Right(x) => onClick(x)
        case Some(x) => onClick(x)
        case List(x) => onClick(x)

        case x =>
            println("unknown onClick: " + x)
    }

    def clearOverlay() {
        overlayPane.invis()
        overlayPane.clear()
    }

    override def info(self : |[Faction], aa : $[UserAction]) = {
        val actions = game.info($, self, aa)

        val sorted = actions.sortWith { (aa, bb) =>
            val a = aa @@ {
                case ViewCardInfoAction(_, c : DeckCard) => Some(c)
                case _ => None
            }

            val b = bb @@ {
                case ViewCardInfoAction(_, c : DeckCard) => Some(c)
                case _ => None
            }

            if (callbacks.settings.has(SortByValueCards))
                (a, b) @@ {
                    case Some(a) -> Some(b) => a.strength * 100 + a.suit.sortKey < b.strength * 100 + b.suit.sortKey
                    case _ => false
                }
            else
            if (callbacks.settings.has(SortBySuitCards))
                (a, b) @@ {
                    case Some(a) -> Some(b) => a.strength + a.suit.sortKey * 100 < b.strength + b.suit.sortKey * 100
                    case _ => false
                }
            else
                false
        }

        sorted.any.??($(ZOption(Empty, Break)) ++ convertActions(self.of[Faction], sorted)) ++
            (options.has(DebugInterface)).$(ZBasic(Break ~ Break, "Action Cards Discard Pile".hh, () => { onClick("discard") }).copy(clear = false)) ++
            $(ZBasic(Break ~ Break, "Played Action Cards".hh, (game.seen.any || options.has(DebugInterface)).??(() => { onClick("seen") })).copy(clear = false)) ++
            $(ZBasic(Break ~ Break, "Court: ".hh ~
                " Discard " ~ game.discourt.some./(_.num)./(n => n.hlb ~ " card".s(n)).|("empty") ~
                ", Deck " ~ game.court.some./(_.num)./(n => n.hlb ~ " card".s(n)).|("empty"),
                (game.discourt.any || options.has(DebugInterface)).??(() => { onClick("discourt") })).copy(clear = false)
            ) ++
            $(ZBasic(Break ~ Break, "Map Readout".hh, () => { onClick("readout") }).copy(clear = false)) ++
            (currentGame.isOver && hrf.HRF.flag("replay").not).$(
                ZBasic(Break ~ Break ~ Break, "Save Replay As File".hh, () => {
                    showOverlay(overlayScrollX("Saving Replay...".hl.div).onClick, null)

                    callbacks.saveReplay {
                        overlayPane.invis()
                        overlayPane.clear()
                    }
                }).copy(clear = false)
            ) ++
            (hrf.HRF.param("lobby").none && hrf.HRF.offline.not).$(
                ZBasic(Break ~ Break ~ Break, "Save Game Online".hh, () => {
                    showOverlay(overlayScrollX("Save Game Online".hlb(xstyles.larger125) ~
                        ("Save".hlb).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(xstyles.width60ex).pointer.onClick.param("***") ~
                        ("Save and replace bots with humans".hh).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(xstyles.width60ex).pointer.onClick.param("///") ~
                        ("Save as a single-player multi-handed game".hh).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(xstyles.width60ex).pointer.onClick.param("###") ~
                        ("Cancel".txt).div.div(xstyles.choice)(xstyles.xx)(xstyles.chm)(xstyles.chp)(xstyles.thu)(xlo.fullwidth)(xstyles.width60ex).pointer.onClick.param("???")
                    ).onClick, {
                        case "***" => callbacks.saveReplayOnline(false, false) { url => onClick(Nil) }
                        case "///" => callbacks.saveReplayOnline(true , false) { url => onClick(Nil) }
                        case "###" => callbacks.saveReplayOnline(true , true ) { url => onClick(Nil) }
                        case _ => onClick(Nil)
                    })
                }).copy(clear = false)
            ) ++
            $(ZBasic(Break ~ Break ~ Break, "Notifications".spn, () => { onClick("notifications", self) }).copy(clear = false)).%(_ => self.any || game.isOver).%(_ => false) ++
            $(ZBasic(Break, tip.|(Empty).spn, () => { tip = randomTip() }, ZBasic.infoch).copy(clear = false)).%(_ => callbacks.settings.has(hrf.HideTips).not) ++
            $(ZBasic(Break, "Settings".spn, () => {
                tip = randomTip()
                val old = callbacks.settings.of[FactionPanesOption] ++ callbacks.settings.of[LogPaneOption]
                callbacks.editSettings {
                    val neu = callbacks.settings.of[FactionPanesOption] ++ callbacks.settings.of[LogPaneOption]
                    if (old != neu)
                        resize()
                    else
                        updateStatus()
                }
            }).copy(clear = false))
    }

    var shown : $[Notification] = $

    override def showNotifications(self : $[F]) : Unit = {
        val newer = game.notifications
        val older = shown

        shown = game.notifications

        val display = newer.diff(older).%(_.factions.intersect(self).any)./~(n => convertActions(self.single, n.infos)).some./~(_ :+ ZOption(Empty, Break))

        if (display.none)
            return

        overlayPane.vis()

        overlayPane.attach.clear()

        val ol = overlayPane.attach.appendContainer(overlayScrollX(Content), resources, onClick)

        val asker = new NewAsker(ol, s => img(s))

        asker.zask(display)(resources)
    }

    val messages : $[F => Elem] = $(
        (f : Faction) => f.elem ~ " contemplates the next move",
        (f : Faction) => f.elem ~ " consults the space oracle",
        (f : Faction) => f.elem ~ " rolls the cosmic dice",
        (f : Faction) => f.elem ~ " stares blankly at the map",
        (f : Faction) => f.elem ~ " considers available options",
        (f : Faction) => f.elem ~ " asks the loyal advisors",
        (f : Faction) => f.elem ~ " plans upsetting the world order",
        (f : Faction) => f.elem ~ " imagines the worst outcome",
        (f : Faction) => f.elem ~ " feels the victory is within grasp",
        (f : Faction) => f.elem ~ " tries to remember the cheat codes",
        (f : Faction) => f.elem ~ " plays idly with a meeple",
        (f : Faction) => f.elem ~ " devises an escape plan",
        (f : Faction) => f.elem ~ " murmurs prays to the elder gods",
        (f : Faction) => f.elem ~ " analyzes the situation",
        (f : Faction) => f.elem ~ " plays out different scenarios",
    ).shuffle

    override def wait(self : $[F], factions : $[F], message : Elem) {
        lastActions = $
        lastThen = null

        showNotifications(self)

        val m =
            if (factions.single.any && message == Empty) {
                val f = factions.only
                val n = hrf.HRF.uptime() / 5000
                messages(n % messages.num)(f)
            }
            else
                message

        super.wait(self, factions, m)
    }

    var lastActions : $[UserAction] = $
    var lastThen : UserAction => Unit = null

    var keys = $[Key]()

    override def ask(faction : |[F], actions : $[UserAction], then : UserAction => Unit) {
        lastActions = actions
        lastThen = then

        showNotifications(faction.$)

        keys = actions./~(a => a.as[Key] || a.unwrap.as[Key])

        keys ++= actions.of[SoftKeys].%(_.isSoft)./~(game.performContinue(None, _, false).continue match {
            case Ask(f, l) if faction.has(f) => l./~(a => a.as[Key] || a.unwrap.as[Key])
            case _ => $()
        })

        updateStatus()

        lazy val choice = actions./~{
            case _ : Info => None
            case _ : Hidden => None
            case _ : DeadlockAction => None
            case a => Some(a)
        }

        lazy val expand = actions./~{
            case a : HalfExplode => a.expand(None)
            case _ => $
        }./~{
            case _ : Info => None
            case _ : Hidden => None
            case a => Some(a)
        }

        if (choice.num == 1 && actions./(_.unwrap).of[DeadlockAction].any) {
            scalajs.js.timers.setTimeout(0) { then(choice(0)) }
            return
        }

        if (choice.num == 1 && actions./(_.unwrap).of[CheckNoFleetAction].any && (callbacks.settings.has(AutoEndOfTurn))) {
            scalajs.js.timers.setTimeout(0) { then(choice(0)) }
            return
        }

        if (choice.num == 1 && actions./(_.unwrap).of[StartRoundAction.type].any && (callbacks.settings.has(AutoEndOfRound))) {
            scalajs.js.timers.setTimeout(0) { then(choice(0)) }
            return
        }

        if (choice.num == 2 && actions./(_.unwrap).of[BattleReRollAction].any && actions./(_.unwrap).of[Cancel].any && (callbacks.settings.has(AutoDiceRolls))) {
            scalajs.js.timers.setTimeout(0) { then(choice(0)) }
            return
        }

        val sorted = actions.sortWith { (aa, bb) =>
            val a = aa @@ {
                case YYSelectObjectAction(_, _, c : DeckCard, _, _) => Some(c)
                case YYDeselectObjectAction(_, _, c : DeckCard, _, _) => Some(c)
                case UnavailableReasonAction(YYSelectObjectAction(_, _, c : DeckCard, _, _), _) => Some(c)
                case _ => None
            }

            val b = bb @@ {
                case YYSelectObjectAction(_, _, c : DeckCard, _, _) => Some(c)
                case YYDeselectObjectAction(_, _, c : DeckCard, _, _) => Some(c)
                case UnavailableReasonAction(YYSelectObjectAction(_, _, c : DeckCard, _, _), _) => Some(c)
                case _ => None
            }

            if (callbacks.settings.has(SortByValueCards))
                (a, b) @@ {
                    case Some(a) -> Some(b) => a.strength * 100 + a.suit.sortKey < b.strength * 100 + b.suit.sortKey
                    case _ => false
                }
            else
            if (callbacks.settings.has(SortBySuitCards))
                (a, b) @@ {
                    case Some(a) -> Some(b) => a.strength + a.suit.sortKey * 100 < b.strength + b.suit.sortKey * 100
                    case _ => false
                }
            else
                false
        }

        super.ask(faction, sorted, a => {
            clearOverlay()
            keys = $
            then(a)
        })
    }

    override def fixActionElem(e : Elem) : Elem = e @@ {
        case Div(e, l) => Div(fixActionElem(e), l)
        case Span(e, l) => Span(fixActionElem(e), l)
        case Concat(a, b) => Concat(fixActionElem(a), fixActionElem(b))
        case ElemList(l, e) => ElemList(l./(fixActionElem), fixActionElem(e))
        case Image(ImageId(i), s, d) => Image(ImageId(callbacks.settings.has(StarStarports).?(i.replace("starport", "starport-alt").replace("starport-alt-alt", "starport-alt")).|(i)), s, d)
        case Parameter(p, e) => Parameter(p, fixActionElem(e))
        case OnClick(e) => OnClick(fixActionElem(e))
        case e => e
    }

    override def styleAction(faction : |[F], actions : $[UserAction], a : UserAction, unavailable : Boolean, view : |[Any]) : $[Style] =
        view @@ {
            case _ if unavailable.not => $()
            case Some(_ : Figure) => $(styles.unquasi)
            case Some(_) => $(xstyles.unavailableCard)
            case _ => $(xstyles.unavailableText)
        } ++
        a @@ {
            case _ if view.any && view.get.is[Resource] => $(styles.card0, styles.circle)
            case _ if view.any && view.get.is[|[ResourceLike]] => $(styles.card0, styles.circle)
            case _ if view.any && view.get.is[(|[ResourceLike], ResourceSlot)] => $(styles.card0, styles.circle)
            case _ : Info => $(xstyles.info)
            case _ if unavailable => $(xstyles.info)
            case _ => $(xstyles.choice)
        } ++
        a @@ {
            case _ if view.any && view.get.is[Resource] => $()
            case _ if view.any && view.get.is[|[ResourceLike]] => $()
            case _ if view.any && view.get.is[(|[ResourceLike], ResourceSlot)] => $()
            case _ => $(xstyles.xx, xstyles.chp, xstyles.chm)
        } ++
        faction @@ {
            case Some(f : Faction) => $(elem.borders.get(f))
            case _ => $()
        } ++
        a @@ {
            case a : Selectable if a.selected => $(styles.selected)
            case _ => $()
        } ++
        view @@ {
            case Some(_ : Figure)                    => $(styles.inline, styles.quasi) ++
                a @@ {
                    case a : XXSelectObjectAction[_] => $($(), $(styles.selfigure1), $(styles.selfigure2))(a.selecting.count(a.n))
                    case a : XXDeselectObjectAction[_] => $($(), $(styles.selfigure1), $(styles.selfigure2))(a.selecting.count(a.n))
                    case _ => $()
                }
            case Some(_) => $(styles.inline)
            case _ => $(xstyles.thu, xstyles.thumargin, xlo.fullwidth)
        } ++
        a @@ {
            case _ if unavailable => $()
            case _ : Extra[_] => $()
            case _ : Choice | _ : Cancel | _ : Back | _ : OnClickInfo => $(xlo.pointer)
            case _ => $()
        }

}
