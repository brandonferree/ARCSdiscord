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


case object ImperialBattle extends Effect
case object ChosenBattle extends Effect


case class MayBattleAction(self : Faction, effect : |[Effect], then : ForcedAction) extends ForcedAction with ThenDesc { def desc = "(then may " ~ "Battle".hh ~ ")" }
case class MustBattleAction(self : Faction, effect : |[Effect], then : ForcedAction) extends ForcedAction with ThenDesc { def desc = "(then must " ~ "Battle".hh ~ ")" }
case class BattleMainAction(self : Faction, cost : Cost, effect : |[Effect], skip : Boolean, cancel : Boolean, then : ForcedAction) extends ForcedAction with Soft
case class BattleSystemAction(self : Faction, cost : Cost, effect : |[Effect], s : System, allies : $[$[Color]], l : $[Color], then : ForcedAction) extends ForcedAction with Soft
case class BattleFactionAction(self : Faction, cost : Cost, effect : |[Effect], s : System, allies : $[Color], e : Color, then : ForcedAction) extends ForcedAction
case class BattleStartAction(self : Faction, s : System, allies : $[Color], e : Color, used : $[Effect], then : ForcedAction) extends ForcedAction
case class BattleDiceAction(self : Faction, s : System, allies : $[Color], e : Color, skirmish : Int, assault : Int, raid : Int, used : $[Effect], then : ForcedAction) extends ForcedAction with Soft
case class BattlePromptRollAction(self : Faction, s : System, allies : $[Color], e : Color, skirmish : Rolled, assault : Rolled, raid : Rolled, skirmishD : Rolled, assaultD : Rolled, raidD : Rolled, skirmishN : Int, assaultN : Int, raidN : Int, effect : |[Effect], used : $[Effect], then : ForcedAction) extends ForcedAction with Soft
case class BattleReRollAction(self : Faction, s : System, allies : $[Color], e : Color, skirmish : Rolled, assault : Rolled, raid : Rolled, skirmishD : Rolled, assaultD : Rolled, raidD : Rolled, skirmishN : Int, assaultN : Int, raidN : Int, effect : |[Effect], used : $[Effect], then : ForcedAction) extends ForcedAction
case class BattleRolledAction(self : Faction, s : System, allies : $[Color], e : Color, skirmish : Rolled, assault : Rolled, raid : Rolled, rolled1 : Rolled, rolled2 : Rolled, rolled3 : Rolled, used : $[Effect], then : ForcedAction) extends Rolled3Action[$[BattleResult], $[BattleResult], $[BattleResult]]
case class BattlePostRollAction(self : Faction, s : System, allies : $[Color], e : Color, skirmish : Rolled, assault : Rolled, raid : Rolled, used : $[Effect], then : ForcedAction) extends ForcedAction with Soft
case class BattleProcessAction(self : Faction, s : System, allies : $[Color], e : Color, skirmish : Rolled, assault : Rolled, raid : Rolled, used : $[Effect], then : ForcedAction) extends ForcedAction
case class BattleRaidAction(self : Faction, s : System, e : Color, raid : Int, used : $[Effect], then : ForcedAction) extends ForcedAction with Soft
case class BattleRaidResourceAction(self : Faction, e : Faction, r : ResourceLike, k : ResourceSlot, then : ForcedAction) extends ForcedAction
case class BattleRaidFreeResourceAction(self : Faction, r : Resource, then : ForcedAction) extends ForcedAction
case class BattleRaidCourtCardAction(self : Faction, e : Faction, c : GuildCard, then : ForcedAction) extends ForcedAction
case class BattleRaidTrophyAction(self : Faction, e : Faction, u : Figure, then : ForcedAction) extends ForcedAction
case class BattleRaidCaptiveAction(self : Faction, e : Faction, u : Figure, then : ForcedAction) extends ForcedAction
case class RaidedAction(self : Faction, e : Color, then : ForcedAction) extends ForcedAction
case class PostBattleAction(self : Faction, s : System, e : Color, used : $[Effect], then : ForcedAction) extends ForcedAction

case class PromptRepairsAction(self : Faction, s : System, n : Int, effect : |[Effect], then : ForcedAction) extends ForcedAction with Soft
case class RepairsAction(self : Faction, s : System, l : $[Figure], effect : |[Effect], then : ForcedAction) extends ForcedAction

case class AssignHitsAction(self : Faction, s : System, f : Color, e : Color, l : $[Figure], hits : Int, bombardments : Int, raid : Int, effect : |[Effect], used : $[Effect], then : ForcedAction) extends ForcedAction with Soft
case class DealHitsAction(self : Faction, s : System, f : Color, e : Color, l : $[Figure], raid : Int, effect : |[Effect], used : $[Effect], then : ForcedAction) extends ForcedAction
case class RansackMainAction(self : Faction, e : Faction, s : System, then : ForcedAction) extends ForcedAction with Soft
case class RansackAction(self : Faction, n : Int, then : ForcedAction) extends ForcedAction


object BattleExpansion extends Expansion {
    def perform(action : Action, soft : Void)(implicit game : Game) = action @@ {
        // BATTLE
        case MustBattleAction(f, effect, then) =>
            Ask(f)
                .add(BattleMainAction(f, NoCost, effect, false, false, then).as("Battle").!!!)
                .add(DeadlockAction(f, BattleWith(effect), then))

        case MayBattleAction(f, effect, then) =>
            BattleMainAction(f, NoCost, effect, true, false, then)

        case BattleMainAction(f, x, effect, skip, cancel, then) if campaign.not =>
            val forces = systems.%(s => f.at(s).ships.any)

            Ask(f).group(f, "battles", x, effect./("with" -> _), "in")
                .some(forces)(s => f.rivals.%(_.present(s)).some./(l => BattleSystemAction(f, x, effect, s, $($), l, then).as(s, "|", l)))
                .group(" ")
                .skipIf(skip)(then)
                .cancelIf(cancel)
                .needOk

        case BattleMainAction(f, x, effect, skip, cancel, then) if campaign =>
            val forces = systems.%(s => f.at(s).use(l => l.ships.any || (l.flagship.any && Flagship.scheme(f).exists(_.$.starports.fresh.any))))

            val officers = f.regent && f.officers
            val rogue = f.regent && f.hasGuild(RogueAdmirals)
            val outlaws = f.rivals.%(_.regent.not || rogue) ++ $(Blights, Free)
            val targets = $(Empire) ++ f.rivals ++ $(Blights, Free)

            val allies = f.regent.$(Empire) ++ f.abilities.has(JudgesChosen).$(f.rivals.%(_.fates.has(Judge)).only)
            val combinations = 1.to(allies.num).$.reverse./~(i => allies.combinations(i))

            def able(s : System)(a : Color) = a @@ {
                case Empire => Empire.at(s).ships.any && (officers || f.present(s))
                case a : Faction => a.at(s).use(l => l.ships.any || (l.flagship.any && Flagship.scheme(a).exists(_.$.starports.fresh.any)))
            }

            def victims(s : System) = (f.regent && Empire.present(s)).?(outlaws).|(targets).%(_.targetable(s)).%(f.canHarm(_, s))

            Ask(f)
                .group(f, "battles", x, effect./("with" -> _), "in")
                .some(systems) { s =>
                    val cc = combinations.%(_.forall(able(s))) ++ able(s)(f).$($())
                    val vv = victims(s).%(v => cc.exists(_.has(v).not))
                    cc.some./~(c => vv.some./(v => BattleSystemAction(f, x, effect, s, c, v, then).as(s, "|", v)))
                }
                .group(" ")
                .skipIf(skip)(then)
                .cancelIf(cancel)
                .needOk

        case BattleSystemAction(f, x, effect, s, allies, targets, then) =>
            Ask(f).group(f, "battles in", s, effect./("with" -> _))
                .some(targets)(e =>
                    allies.%!(_.has(e))./(aa => TryHarmAction(f, e, s, x, BattleFactionAction(f, x, effect, s, aa, e, then)).as(e, (aa.none && allies.%!(_.has(e)).any).?("alone"), aa./("using" -> _)))
                )
                .cancel
                .needOk

        case BattleFactionAction(f, x, effect, s, allies, e, then) =>
            f.pay(x)

            f.log(allies./("and" -> _), "battled", e, "in", s, x, effect./("with" -> _))

            BattleStartAction(f, s, allies, e, $, then)

        case BattleStartAction(f, s, allies, e, used, then)
        if (f +: allies).exists(a => a.at(s).ships.any || a.as[Faction].?(a => a.at(s).flagship.any && Flagship.scheme(a).exists(_.$.starports.fresh.any))).not =>
            f.log(allies./("and" -> _), "had no ships remaining")

            then

        case BattleStartAction(f, s, allies, e, used, then)
        if f.hasLore(SignalBreaker) && used.has(SignalBreaker).not && (f +: allies).forall(a => a.at(s).ships.damaged.none) =>
            BattleStartAction(f, s, allies, e, used :+ SignalBreaker, then)

        case BattleStartAction(f, s, allies, e : Faction, used, then)
        if e.hasLore(PredictiveSensors) && used.has(PredictiveSensors).not =>
            Ask(e).group(PredictiveSensors, "move to", s)
                .some(game.connected(s)) { t =>
                    e.at(t).ships.fresh./(u => PredictiveSensorsAction(f, t, u, s, BattleStartAction(f, s, allies, e, used, then)).as(u, "from", t))
                }
                .done(BattleStartAction(f, s, allies, e, used :+ PredictiveSensors, then))

        case BattleStartAction(f, s, allies, e : Faction, used, then)
        if e.hasLore(RailgunArrays) && used.has(RailgunArrays).not && (e.at(s).ships.fresh.any || (e.regent && Empire.at(s).ships.fresh.any)) =>
            AssignHitsAction(f, s, e, f, f.at(s).ships, 1, 0, 0, |(RailgunArrays), used, BattleStartAction(f, s, allies, e, used :+ RailgunArrays, then))

        case BattleStartAction(f, s, allies, e, used, then) =>
            val l = f.at(s)

            val limit =
                (f +: allies)./(a => a.at(s).ships.num + a.as[Faction]./(a => a.at(s).flagship.any.??(Flagship.scheme(a).%(_.$.starports.fresh.any).num)).|(0)).sum +
                s.gate.??(f.hasGuild(Gatekeepers).??(2)) +
                f.hasTrait(Committed).??(2)

            // f.log("could roll up to", limit.hlb, (limit > 1).?("dice").|("die"))

            val noRaid =
                if (e == Empire)
                    true
                else
                if (e == Blights)
                    true
                else
                if (e.as[Faction].?(_.hasLore(HiddenHarbors)) && e.at(s).starports.fresh.any)
                    true
                else
                if (e.as[Faction].?(e => e.at(s).flagships.any && TractorBeam(e).any))
                    true
                else
                    false

            val freeRaid =
                if (noRaid)
                    0
                else
                if (e == Free && e.at(s).buildings.any)
                    6
                else
                if (e.as[Faction].?(e => e.at(s).use(l => l.buildings.any || l.flagships.any)))
                    6
                else
                if (e.as[Faction].?(e => systems.forall(e.at(_).buildings.none) && e.flagship.none))
                    6
                else
                if (f.hasLore(RaiderExosuits))
                    1
                else
                    0

            val limitedRaid =
                if (noRaid)
                    0
                else
                if (f.hasGuild(PirateFleet) && e.is[Faction])
                    6
                else
                    freeRaid

            val combinations : $[(Int, Int, Int)] = 1.to(min(18, limit)).reverse./~(n =>
                max(0, n - 12).to(min(limitedRaid, n))./~(raid =>
                    max(0, n - raid - 6).to(min(6, n - raid))./~(assault =>
                        |(n - raid - assault).%(_ <= 6).%(_ >= f.hasTrait(Wary).??(assault))./(skirmish =>
                            (skirmish, assault, raid)
                        )
                    )
                )
            )

            Ask(f).group(f, "battles", e, "in", s)
                .each(combinations) { case (skirmish, assault, raid) =>
                    BattleDiceAction(f, s, allies, e, skirmish, assault, raid, used ++ (raid > freeRaid).$(PirateFleet), then)
                        .as(skirmish.times(Image("skirmish-die", styles.inlineToken)), assault.times(Image("assault-die", styles.inlineToken)), raid.times(Image("raid-die", styles.inlineToken)))
                }

        case BattleDiceAction(f, s, allies, e, n1, n2, n3, used, then) =>
            BattlePromptRollAction(f, s, allies, e, $, $, $, $, $, $, n1, n2, n3, None, used, then)

        case BattlePromptRollAction(f, s, allies, e, Nil, Nil, Nil, Nil, Nil, Nil, n1, n2, n3, effect, used, then) =>
            Ask(f).group("Roll")
                .add(BattleReRollAction(f, s, allies, e, Nil, Nil, Nil, Nil, Nil, Nil, n1, n2, n3, effect, used, then).as(n1.times(Image("skirmish-die", styles.inlineToken)), n2.times(Image("assault-die", styles.inlineToken)), n3.times(Image("raid-die", styles.inlineToken))))
                .cancel

        case BattlePromptRollAction(f, s, allies, e, o1, o2, o3, d1, d2, d3, n1, n2, n3, effect, used, then) =>
            Ask(f).group("Keep",
                o1.diff(d1)./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)) ~
                o2.diff(d2)./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)) ~
                o3.diff(d3)./(x => Image("raid-die-" + (Raid.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)),
                (o1.diff(d1).none && o2.diff(d2).none && o3.diff(d3).none).?("none")
            )
                .add(BattleReRollAction(f, s, allies, e, o1, o2, o3, d1, d2, d3, n1, n2, n3, effect, used, then).as("Reroll", effect./("with" -> _),
                    d1./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineTokenDarken)) ~
                    d2./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineTokenDarken)) ~
                    d3./(x => Image("raid-die-" + (Raid.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineTokenDarken)),
                ))
                .cancel

        case BattleReRollAction(f, s, allies, e, o1, o2, o3, d1, d2, d3, n1, n2, n3, effect, used, then) =>
            if (d1.any || d2.any || d3.any)
                f.log("rerolled",
                    d1./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineTokenDarken)) ~
                    d2./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineTokenDarken)) ~
                    d3./(x => Image("raid-die-" + (Raid.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineTokenDarken)),
                    effect./("with" -> _)
                )

            Roll3[$[BattleResult], $[BattleResult], $[BattleResult]](n1.times(Skirmish.die), n2.times(Assault.die), n3.times(Raid.die), (l1, l2, l3) => BattleRolledAction(f, s, allies, e, o1, o2, o3, l1, l2, l3, used ++ effect, then))

        case BattleRolledAction(f, s, allies, e, o1, o2, o3, n1, n2, n3, used, then) =>
            f.log("rolled",
                n1./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)) ~
                n2./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)) ~
                n3./(x => Image("raid-die-" + (Raid.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken))
            )

            BattlePostRollAction(f, s, allies, e, o1 ++ n1, o2 ++ n2, o3 ++ n3, used, then)

        case BattlePostRollAction(f, s, allies, e, l1, l2, l3, used, then) =>
            var ask = Ask(f)

            if (l1.any && f.hasGuild(Skirmishers) && used.has(Skirmishers).not) {
                val limit = f.countableResources(Weapon) + f.loyal.of[GuildCard].count(_.suit == Weapon)
                val miss = $()
                val hit = $(HitShip)
                val misses = l1.count(miss)
                val hits = l1.count(hit)
                val rerollable = 1.to(min(limit, misses)).reverse./(_.times(miss)) ++ 1.to(min(limit, hits))./(_.times(hit))

                ask = ask
                    .group(Skirmishers)
                    .each(rerollable)(q => BattlePromptRollAction(f, s, allies, e, l1.diff(q), l2, l3, q, $, $, q.num, 0, 0, |(Skirmishers), used, then).as("Reroll", q./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken))))
            }

            if (l2.any && f.hasLore(SeekerTorpedoes) && used.has(SeekerTorpedoes).not) {
                val limit = f.at(s).ships.fresh.num
                val rerollable = 1.to(limit).reverse./~(n => l2.combinations(n).$)

                ask = ask
                    .group(SeekerTorpedoes)
                    .each(rerollable)(q => BattlePromptRollAction(f, s, allies, e, l1, l2.diff(q), l3, $, q, $, 0, q.num, 0, |(SeekerTorpedoes), used, then).as("Reroll", q./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken))))
            }

            if (l2.any && f.hasGuild(HunterSquads) && used.has(HunterSquads).not && e.as[Faction].?(_.regent.not)) {
                val limit = f.countableResources(Weapon) + f.loyal.of[GuildCard].count(_.suit == Weapon)
                val rerollable = 1.to(limit).reverse./~(n => l2.combinations(n).$)

                ask = ask
                    .group(HunterSquads)
                    .each(rerollable)(q => BattlePromptRollAction(f, s, allies, e, l1, l2.diff(q), l3, $, q, $, 0, q.num, 0, |(HunterSquads), used, then).as("Reroll", q./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken))))
            }

            if (l3.any && f.hasTrait(Tricky) && used.has(Tricky).not) {
                val limit = Resources.all.count(f.hasCountableResource)
                val rerollable = 1.to(limit).reverse./~(n => l3.combinations(n).$)

                ask = ask
                    .group(Tricky)
                    .each(rerollable)(q => BattlePromptRollAction(f, s, allies, e, l1, l2, l3.diff(q), $, $, q, 0, 0, q.num, |(Tricky), used, then).as("Reroll", q./(x => Image("raid-die-" + (Raid.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken))))
            }

            if (game.declared.contains(Empath) && (l1.any || l2.any || l3.any) && f.hasLore(EmpathsVision) && used.has(EmpathsVision).not) {
                val rerollable1 = 0.to(l1.num).reverse./~(n => l1.combinations(n).$)
                val rerollable2 = 0.to(l2.num).reverse./~(n => l2.combinations(n).$)
                val rerollable3 = 0.to(l3.num).reverse./~(n => l3.combinations(n).$)

                val all = rerollable1./~(r1 => rerollable2./~(r2 => rerollable3./~(r3 => (r1.any || r2.any || r3.any).?(r1, r2, r3))))

                ask = ask
                    .group(EmpathsVision)
                    .each(all) { case (q1, q2, q3) =>
                        BattlePromptRollAction(f, s, allies, e, l1.diff(q1), l2.diff(q2), l3.diff(q3), q1, q2, q3, q1.num, q2.num, q3.num, |(EmpathsVision), used, then).as("Reroll",
                            q1./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)),
                            q2./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)),
                            q3./(x => Image("raid-die-" + (Raid.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)),
                        )
                    }
            }

            ask.skip(BattleProcessAction(f, s, allies, e, l1, l2, l3, used, then))

        case BattleProcessAction(f, s, allies, e, l1, l2, l3, used, then) =>
            if (used.intersect($(Skirmishers, SeekerTorpedoes, HunterSquads, Tricky, EmpathsVision)).any)
                f.log("ended with",
                    l1./(x => Image("skirmish-die-" + (Skirmish.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)) ~
                    l2./(x => Image("assault-die-" + (Assault.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken)) ~
                    l3./(x => Image("raid-die-" + (Raid.die.values.indexed.%(_ == x).indices.shuffle(0) + 1), styles.inlineToken))
                )

            val ll = (l1 ++ l2 ++ l3).flatten

            val sb = used.has(SignalBreaker).$(Intercept)

            if (ll.intersect(sb).any)
                f.log("used", SignalBreaker, "to cancel", (ll.count(Intercept) > 1).?("just"), "one", "Intercept".hl)

            val mp = (e.as[Faction].?(_.hasLore(MirrorPlating)) && l2.any).$(Intercept)

            if (mp.any)
                e.log("used", MirrorPlating, "to add one", "Intercept".hl)

            val l = ll.diff(sb) ++ mp

            val sd = l.count(OwnDamage)
            var ic = l.has(Intercept).??(e.at(s).ships.fresh.num + campaign.??(e.as[Faction].?(e => e.regent.??(Empire.at(s).ships.fresh.num) + e.at(s).flagship.any.??(DefenseArray(e).any.??(Flagship.scheme(e)./(_.$.starports.fresh.num).sum)))))
            val hs = l.count(HitShip)
            val bb = l.count(HitBuilding)
            val rd = l.count(RaidKey)

            e.as[Faction].foreach { e =>
                if (l.has(Intercept) && e.hasTrait(Irregular)) {
                    ic = e.countableResources(Weapon) + e.loyal.of[GuildCard].count(_.suit == Weapon)

                    if (e.hasResource(Weapon)) {
                        val w = e.spendable.resources.%<(_.is(Weapon)).some./(_.minBy(_._2.raidable.|(999))._1)

                        w.foreach { w =>
                            w --> w.supply

                            e.log("discarded", w, "due to", Irregular)
                        }
                    }
                }
            }

            if (sd > 0)
                f.log("suffered", sd.hit)

            if (ic > 0)
                f.log("got intercepted for", ic.hit)

            if (hs > 0)
                f.log("dealt", hs.hit)

            if (bb > 0)
                f.log("bombarded for", bb.hit)

            if (rd > 0)
                f.log("raided with", rd.hl, "keys")

            val attackers = f.at(s).shiplikes ++ allies./~(_.at(s).shiplikes)
            val defenders = e.at(s) ++ (e.regent && allies.has(Empire).not).??(Empire.at(s).ships)

            val u = allies.has(Empire).$(ImperialBattle) ++ f.rivals.%(_.fates.has(Judge)).exists(allies.has).$(ChosenBattle) ++ used

            AssignHitsAction(f, s, e, f, attackers, sd + ic, 0, 0, None, used,
                AssignHitsAction(f, s, f, e, e.at(s) ++ campaign.??((e.regent && allies.has(Empire).not).??(Empire.at(s).ships)), hs, bb, rd, None, u,
                    HarmAction(f, e, s,
                        BattleRaidAction(f, s, e, rd, u,
                            PostBattleAction(f, s, e, used, then)))))

        case BattleRaidAction(f, s, e, raid, used, then) if raid <= 0 =>
            Then(then)

        case BattleRaidAction(f, s, Empire, raid, used, then) =>
            Then(then)

        case BattleRaidAction(f, s, Blights, raid, used, then) =>
            Then(then)

        case BattleRaidAction(f, s, e, raid, used, then)
        if f.at(s).shiplikes.none
        && (used.has(ImperialBattle).not || Empire.at(s).ships.none)
        && (used.has(ChosenBattle).not || factions.%(_.fates.has(Judge)).only.at(s).shiplikes.none) =>
            Then(then)

        case BattleRaidAction(f, s, e : Faction, raid, used, then) if used.has(PirateFleet) && used.count(PirateRaid) >= e.at(s).ships.damaged.num =>
            Then(then)

        case BattleRaidAction(f, s, e : Faction, raid, used, then) =>
            def spend(k : Int) = (k >= raid).?(then).|(BattleRaidAction(f, s, e, raid - k, used ++ used.has(PirateFleet).$(PirateRaid), then))

            Ask(f).group("Raid", e, "with", raid.times(Image("raid-key", styles.inlineToken)).merge)
                .each(e.raidable.desc) { case (r, k) =>
                    BattleRaidResourceAction(f, e, r, k, spend(k.raidable.|(999))).as(r -> k)
                        .!(k.raidable.none)
                        .!(k.raidable.get > raid)
                        .!(e.hasGuild(SwornGuardians), SwornGuardians.name)
                        .!(game.declared.contains(Keeper) && e.hasLore(KeepersTrust) && r.as[ResourceToken]./(_.resource).?(f.hasCountableResource), "Keeper's Trust")
                }
                .useIf(used.has(PirateFleet).not) { _
                    .each(e.loyal.of[GuildCard]) { c =>
                        BattleRaidCourtCardAction(f, e, c, spend(c.keys)).as(c, Image("keys-" + |(c.keys).but(999).|("x"), styles.token))
                            .!(c.keys > raid)
                            .!(e.loyal.but(c).exists(_.as[GuildCard].?(_.effect == SwornGuardians)), SwornGuardians.name)
                            .!(game.declared.contains(Keeper) && e.hasLore(KeepersSolidarity) && e.hasCountableResource(c.suit), KeepersSolidarity.name)
                            .!(c.suit == Weapon && e.hasGuild(HonorGuard) && e.hasCountableResource(Weapon), HonorGuard.name)
                    }
                }
                .useIf(e.hasLore(VowOfFairness)) { _
                    .each(e.trophies)(u => BattleRaidTrophyAction(f, e, u, spend(1)).as(u, game.showFigure(u, 1), Image("keys-" + 1, styles.token)))
                    .each(e.captives)(u => BattleRaidCaptiveAction(f, e, u, spend(1)).as(u, game.showFigure(u), Image("keys-" + 1, styles.token)))
                }
                .needOk
                .done(then)

        case BattleRaidAction(f, s, Free, raid, used, then) =>
            val l = game.resources(s).distinct.%(game.available)

            if (l.any)
                Ask(f).each(l)(r => BattleRaidFreeResourceAction(f, r, then).as("Gain", ResourceRef(r, None))("Raid", Free))
            else
                NoAsk(f)(then)

        case BattleRaidFreeResourceAction(f, r, then) =>
            f.steal(Supply(r).first)

            f.log("stole", ResourceRef(r, None))

            then

        case BattleRaidResourceAction(f, e, r, k, then) =>
            StealResourceAction(f, e, r, k, then)

        case BattleRaidCourtCardAction(f, e, c, then) =>
            StealGuildCardAction(f, e, c, then)

        case BattleRaidTrophyAction(f, e, u, then) =>
            if (u.faction == f)
                u --> f.reserve
            else
                u --> f.trophies

            f.log("stole trophy", u)

            then

        case BattleRaidCaptiveAction(f, e, u, then) =>
            if (u.faction == f)
                u --> f.reserve
            else
                u --> f.captives

            f.log("stole captive", u)

            then

        case RaidedAction(f, e, then) =>
            then

        case AssignHitsAction(self, s, f, e, l, hits, bombardments, raid, effect, used, then) =>
            val attacker = self == e
            val defender = self == f

            val blightToughness = f.as[Faction].?(_.hasGuild(BlightHunters)).?(1).|(2)

            var h = hits
            var b = bombardments

            val levied = attacker.??(e.as[Faction].get.hasLore(WardensLevy).??(WardensLevy.$))
            val upgrades = l.flagships./~(u => Flagship.scheme(u.faction.as[Faction].get)./~(_.$))
            val ships = l.ships ++ attacker.??(upgrades)
            val buildings = l.buildings ++ l.banners ++ defender.??(upgrades)
            val blights = l.blights
            val bunkers = l.bunkers

            val leviedN = levied./(u => u.fresh.?(2).|(1)).sum
            val shipsN = ships./(u => u.fresh.?(2).|(1)).sum
            val buildingsN = buildings./(u => u.fresh.?(2).|(1)).sum
            val blightsN = blights./(u => u.fresh.?(2).|(1)).sum
            val bunkersN = bunkers./(u => u.fresh.?(2).|(1)).sum

            if (h >= leviedN + shipsN + blightsN * blightToughness) {
                val newh = leviedN + shipsN + blightsN * blightToughness
                b = b + (h - newh)
                h = newh
            }
            else {
                if (blightToughness > 1)
                    if (leviedN == 0 && shipsN == 0)
                        if (h % 2 == 1)
                            h -= 1
            }

            if (b > buildingsN + bunkersN * 2)
                b = buildingsN + bunkersN * 2

            if (buildingsN == 0)
                if (b % 2 == 1)
                    b -= 1

            val flagships = l.flagships./~(u => Flagship.armors(u.faction.as[Faction].get) ++ Flagship.functions(u.faction.as[Faction].get))

            val strict = (shipsN >= 15) && (hits >= 7)

            def order(u : Figure) = (u.piece @@ {
                case Flagship => -1
                case City | Starport => 3
                case Banner => 4
                case Bunker => 5
                case _ => 0
            }, u.faction.is[Faction], u.index)

            val ll = l.sortBy(order)

            implicit def convert(u : Figure, k : Int) = game.showFigure(u, u.damaged.??(1) + k)

            if (h + b > 0)
                XXSelectObjectsAction(self, levied ++ ll ++ flagships./(_.$.single.|(Figure(Free, Slot, 0))))
                    .withGroup(f, "dealt", hits.hit, (bombardments > 0).?(("and", bombardments.hlb), "Bombardment".s(bombardments).styled(styles.hit)), "to", e, "in", s, strict.??("[strict]"),
                        (l ++ upgrades).%!(_.piece.in(Slot, Flagship))./(u => game.showFigure(u, 0) ~ game.showFigure(u, 1) ~ game.showFigure(u, 2)).distinct.merge.div(xstyles.displayNone)
                    )
                    .withSplit(levied.any.$(levied.num) ++ upgrades.any.$(l.num, 6) ++ (upgrades.num > 12).$(6, 6))
                    .withRule(_
                        .upTo(h + b)
                        .useIf(flagships.any)(_.each(_.piece != Flagship))
                        .useIf(flagships.any)(_.each(_.piece != Slot))
                        .useIf(strict)(_.all(d => d.none || d.forall(x => ll.has(x).not || ll.takeWhile(_ != x).exists(u => d.has(u).not && u.piece == x.piece && u.faction == x.faction && u.damaged == x.damaged).not)))
                        .useIf(leviedN > 0)(_.all(d => (d.num <= leviedN && d.forall(_.in(levied))) || (d.count(_.in(levied)) == leviedN)))
                        .all(d => d.forall(x => x.in(upgrades).not || Flagship.functions(x.faction.as[Faction].get).%(_.has(x)).single.use(q => q.none || q.get.use(q => q.armor.none || q.armor.only.use(a => d.count(a) == 1 + a.fresh.??(1))))))
                        .all    (d => d.%(levied.has).num + d.%(ships.has).num + d.%(blights.has).num * blightToughness == h ||  d.%(buildings.has).num + d.%(bunkers.has).num * 2 == 0)
                        .all    (d => d.%(levied.has).num + d.%(ships.has).num + d.%(blights.has).num * blightToughness <= h &&  d.%(buildings.has).num + d.%(bunkers.has).num * 2 <= b)
                        .matches(d => d.%(levied.has).num + d.%(ships.has).num + d.%(blights.has).num * blightToughness == h && (d.%(buildings.has).num + d.%(bunkers.has).num * 2 == b || (d.%(buildings.has).num + d.%(bunkers.has).num * 2 == b - 1 && d.%(buildings.has).num == buildingsN)))
                    )
                    .withMultipleSelects(_.fresh.?(2).|(1))
                    .withThen(d => DealHitsAction(self, s, f, e, d, raid, effect, used, then))(_ => "Damage".hl)
                    .ask
            else
                Then(then)

        case DealHitsAction(self, s, f, e, l, k, effect, used, then) =>
            val attacking = self == f
            val defending = self == e

            val destroyed = l.%(_.damaged) ++ l.diff(l.distinct)

            if (destroyed.any)
                f.log("destroyed", destroyed.comma, effect./("with" -> _))

            val damaged = l.distinct.diff(destroyed)

            if (damaged.any)
                f.log("damaged", damaged.comma, effect./("with" -> _))

            var outraged : $[(Figure, System)] = $

            destroyed.foreach { u =>
                if (u.piece == City)
                    outraged ++= u.region.of[System]./(u -> _)

                game.onRemoveFigure(u)

                val default = (u.piece == Ship && game.laws.has(TheDeadLive)).?(TheDeadLive).|(u.faction.reserve)

                u.faction.damaged :-= u

                u.faction.as[Faction].foreach { f =>
                    f.worked :-= u
                    f.taxed.cities :-= u
                }

                if (game.unslotted.has(u))
                    game.unslotted :-= u

                if (u.faction == f)
                    u --> default
                else
                if (f == Free)
                    u --> default
                else
                if (f == Blights)
                    u --> default
                else
                if (u.region.has(WardensLevy)) {
                    u --> default
                }
                else
                if (f == Empire) {
                    u --> factions.%(_.primus).single./(_.trophies).|(default)
                }
                else
                if (defending && f.as[Faction].get.hasLore(WellOfEmpathy)) {
                    u --> default

                    f.as[Faction].get.gain(Psionic, $("with", WellOfEmpathy))
                }
                else
                if (f.as[Faction].get.hasLore(OathOfPeace) && u.faction != Blights)
                    u --> default
                else
                    u --> f.as[Faction].get.trophies
            }

            if (self.hasLore(BlightSociety) && l.blights.any)
                if (self.pool(Agent)) {
                    self.reserve.$.agents.first --> Scrap

                    self.log("scrapped", Agent.of(self), "due to", BlightSociety)
                }
                else
                    self.log("had no", Agent.sof(self), "to scrap due to", BlightSociety)

            if (self.fates.has(Admiral))
                if (used.has(ImperialBattle))
                    self.advance(destroyed.num, $("destroying"))

            damaged.foreach { u =>
                u.faction.damaged :+= u
            }

            var next = then

            e.as[Faction].foreach { e =>
                if (destroyed.any && e.hasTrait(Beloved) && effect.has(GalacticRifles).not && e != self) {
                    e.log("triggered", Beloved)

                    next = BelovedAction(e, next)
                }
            }

            if (attacking)
                f.as[Faction].foreach { f =>
                    e.as[Faction].foreach { e =>
                        if ((f.isLordIn(s.cluster) && e.isVassalIn(s.cluster)) || (e.isLordIn(s.cluster) && f.isVassalIn(s.cluster)))
                            systems.%(_.cluster == s.cluster).%(_.$.cities.any)./~(game.resources).distinct.foreach { r =>
                                next = OutrageAction(f, r, |(FeudalLaw), next)
                            }
                    }
                }

            outraged./{ (u, s) =>
                f.as[Faction].foreach { f =>
                    var rr = game.resources(s)

                    e.as[Faction].foreach { e =>
                        if (u.faction != f)
                            if (e.hasTrait(Beloved).not || effect.has(GalacticRifles))
                                next = RansackMainAction(f, e, s, next)

                        if (game.seats.contains(s.cluster) && f.isVassalIn(s.cluster) && e.isVassalIn(s.cluster))
                            rr = $
                    }

                    rr.distinct.foreach { r =>
                        next = OutrageAction(f, r, None, next)
                    }
                }
            }

            destroyed.bunkers.foreach { _ =>
                f.as[Faction].foreach { f =>
                    e.as[Faction].foreach { e =>
                        next = ScrapCaptiveMainAction(f, e, next)
                    }
                }
            }

            destroyed.cities./(_.faction).distinct.of[Faction].foreach(_.recalculateSlots())

            CheckCourtScrapAction(next)

        case PostBattleAction(f, s, e, used, then) =>
            if (f.hasLore(RepairDrones) && used.has(RepairDrones).not && f.at(s).ships.damaged.any)
                PromptRepairsAction(f, s, 1, |(RepairDrones), PostBattleAction(f, s, e, used :+ RepairDrones, then))
            else
            if (f.hasTrait(Resilient) && used.has(AttackerResilient).not && f.at(s).ships.damaged.any)
                PromptRepairsAction(f, s, systems.%(f.rules)./(_.$.starports.num).sum, |(Resilient), PostBattleAction(f, s, e, used :+ AttackerResilient, then))
            else
            if (e.as[Faction].?(_.hasTrait(Resilient)) && used.has(DefenderResilient).not && e.at(s).ships.damaged.any)
                PromptRepairsAction(e.as[Faction].get, s, systems.%(e.rules)./(_.$.starports.num).sum, |(Resilient), PostBattleAction(f, s, e, used :+ DefenderResilient, then))
            else
                AdjustResourcesAction(then)

        case PromptRepairsAction(f, s, 0, effect, then) =>
            NoAsk(f)(then)

        case PromptRepairsAction(f, s, n, effect, then) =>
            val ll = f.at(s).ships

            implicit def convert(u : Figure, selected : Boolean) = game.showFigure(u, u.faction.damaged.has(u).??(1) - selected.??(1))

            if (ll.damaged.any)
                XXSelectObjectsAction(f, ll)
                    .withGroup(f, "repairs", n.hl, "ships in", s, $(convert(Figure(f, Ship, 0), false)).merge.div(xstyles.displayNone))
                    .withRule(_.num(min(ll.damaged.num, n)).each(_.damaged))
                    .withThen(l => RepairsAction(f, s, l, effect, then))(_ => "Repair")
                    .withExtras(then.as("Skip"))
                    .ask
            else
                NoAsk(f)(then)

       case RepairsAction(f, s, l, effect, then) =>
            l.foreach { u =>
                f.damaged :-= u
            }

            f.log("repaired", l.comma, "in", s, effect./("with" -> _))

            then

        case RansackMainAction(f, e, s, then) =>
            Ask(f).group("Ransack".hl)
                .each(game.market.%(m => Influence(m.index).exists(_.faction == e))) { m =>
                    RansackAction(f, m.index, then).as(m.$)
                        .!(game.feudal.get(m.index).?(_ == s.cluster), "feudal court")
                }
                .needOk
                .bailout(then.as("No cards in court with", Agent.sof(e)))

        case RansackAction(f, n, then) =>
            val l = Market(n).$

            if (f.abilities.has(Conspiracies))
                f.used :+= Conspiracies

            f.log("ransacked", l.commaAnd)

            l.foldLeft(ExecuteAgentsCourtCardAction(f, n, ReplenishMarketAction(then)) : ForcedAction)((q, c) => GainCourtCardAction(f, c, n, c == l.first, q))


        // ...
        case _ => UnknownContinue
    }
}
