# Roadmap

Phased so each milestone is independently demonstrable. The risk is front-loaded:
get the HRF engine running headless on the JVM (M1) and a picture into Discord
(M3) early, because those are the two unknowns. Everything after is content and
polish.

> **Status (2026-06-25): M1, M2, and M3 are complete.** Path B renders a faithful
> Blighted Reach board headless. See [STATUS.md](STATUS.md) for the detailed handoff
> (what's built, how to run it, and where M4 starts).

## M0 — Foundation (this repo, now)
- [x] Architecture docs grounded in HRF source.
- [x] Module scaffold with documented integration seams.
- [x] License decided: HRF vendored under `hrf-engine/` (MIT; see `hrf-engine/LICENSE`).

## M1 — HRF Arcs engine on the JVM ✅ *(done 2026-06-24)*
**Goal:** call the real Arcs engine from a plain JVM `main`, with no browser.
- [x] JVM build of the HRF rules core + `arcs.*` (`sbt hrfEngine/compile`; tuned
      `excludeFilter` + synthesized `hrf.BuildInfo` in build.sbt).
- [x] `Serialize` reflection verified on the JVM (round-trip stable in self-play).
- [x] Headless game loop reproduced (`modules/selfplay`, reusing `arcs.CampaignHost`).
- **Demo:** `sbt "selfplay/runMain arcsbot.selfplay.SelfPlay"` plays a full game
  start→finish and prints the journal. *(Commits 968dfd5 / dcf0221 / 293f1a3.)*

## M2 — Journal + GameSession ✅ *(done 2026-06-25)*
**Goal:** persistent, replayable games behind a clean API.
- [x] `Journal` (in-memory + `SqlJournal`: JDBC, `(game_id, idx)` PK, optimistic
      concurrency).
- [x] `EngineSession.load/pending/apply/undoTo` (`engine-bridge`): replay log →
      state, surface `Turn`, apply a chosen option, append, advance.
- [x] `Elem → text` flattener (`ElemText`).
- **Demo:** `sbt "engineBridge/runMain arcsbot.engine.Repl [selftest|sqltest|play]"`.
  *(Commit 694dc2e.)* (The multi-act Fates / hidden-info caveat noted here is
  resolved in M5 below — the bridge now plays full campaigns.)

## M3 — First board render ✅ *(done 2026-06-25)*
**Goal:** an image of the live board.
- [x] `BoardRenderer` Path B: a localhost `RenderServer` serves the host page + the
      HRF Scala.js bundle + the local `/webp2/` art; `PathBRenderer` drives headless
      Chromium (Playwright) to replay the journal and screenshot the board. See RENDERING.
- [x] HRF browser UI cross-compiled to JS (`hrfWeb`, `sbt hrfWeb/fastLinkJS`) with an
      Arcs-only shell; engine→renderer projection is `EngineSession.replayBundle`.
- [x] Three blockers root-caused & fixed: Windows Chromium side-by-side launch
      (`RENDER_BROWSER_CHANNEL`), a missing-asset 404 hanging the loader, and a
      browser replay-fidelity bug (`Then`/`Milestone` skipped during replay).
- [x] `RenderSmoke` asserts real visual content; opt-in CI render job (`RENDER_SMOKE=1`).
- **Demo:** `RENDER_BROWSER_CHANNEL=chrome sbt "renderer/runMain arcsbot.render.RenderSmoke 60"`
  → `hrf-web/render-out/board.png` (faithful Blighted Reach board at the requested action).
- *Later refinements:* per-faction tableau cropping (`renderTableau`), viewer-gated
  private renders, and Path A (native compositor, M6).

## M4 — Discord vertical slice ⬅ **next**
**Goal:** play a real (small) Arcs decision through Discord end-to-end.
- [ ] JDA bot: `/arcs new/join/start`, channel+role creation, seat→faction map.
- [ ] On `Turn`: post board PNG + present `actions` as buttons/select; ping
      player.
- [ ] On interaction: `apply` → append → advance → re-post.
- [ ] Private hand/objective info via ephemeral/DM.
- **Demo:** 3–4 humans play the opening of an Arcs game asynchronously in a
  Discord server.

## M5 — Full Blighted Reach campaign 🟦 *(engine/bridge done 2026-06-25; UI polish in M4)*
- [x] **Multi-act flow + Fates working through the bridge.** Root cause of the
      old `Rejected` was `EngineSession.decideAsk` exposing HRF's un-pickable
      `HiddenChoice` "explode" marker for interactive selects (`YY/XXSelectObjects`,
      used by Fates/hidden-info). Now mirrors HRF's UI/bot: explode to concrete
      leaves, filter `Hidden`/`Info`/`Unavailable`.
- [x] **Acts II/III unlocked.** They are fully implemented upstream but gated by
      `MetaBR.development` (= `hrf.HRF.version.startsWith("test")`); the public
      build forces `Act1Only`. We set the vendored `BuildInfo.version` to
      `test-0.8.140` (build.sbt) to enable the full campaign. New default config is
      `NoFate` (the dev campaign mode); `Act1Only` is *not* in dev-mode's option
      list, so using it desyncs the renderer lobby (was the render `MatchError`).
- [x] **Verified:** random full Act I→III campaigns (8 seeds, ~800–965 decisions)
      play to a real game-over and replay to the identical winner
      (`Repl m5probe`); the renderer paints a full board; M2 self-tests pass.
- [ ] Campaign options surfaced in the Discord UI (M4): fate mode, setup cards.
- [ ] Round/act transition summaries (`IntermissionReport`) posted to Discord (M4).
- [ ] Undo etiquette (`UndoAction`, opponent-consent gating).

## M6 — Native renderer + polish
- [ ] `BoardRenderer` Path A (native JVM compositor) for fast, durable,
      mobile-friendly images; per-player private renders.
- [ ] Clocks/nudges, spectators, multi-table per guild, stats.

## Cross-cutting
- Keep `engine-bridge` the **only** module importing `arcs.*`/`hrf.*`.
- Never fork upstream Arcs rules — only build/packaging around them.
- Every action that mutates a game must go through the journal (single source of
  truth) so undo/replay/rendering all stay consistent.
