# Status & handoff

Last updated: **2026-06-25**. Current state of the build, what's done, and the
exact starting point for the next milestone. Read this first when resuming work.

## TL;DR

- **M1 (HRF engine on the JVM) — DONE.**
- **M2 (Journal + EngineSession) — DONE.**
- **M3 (board render, Path B) — RENDERS FAITHFULLY.** The full pipeline produces a
  real Arcs: Blighted Reach `board.png` at the requested action (verified: ~5.8 MB,
  2133 sampled colours — populated tableaus, court, ambitions, fleets). The Windows
  Chromium-launch blocker, the blank-board asset hang, and the Blighted-Reach
  replay-fidelity bug are all root-caused and fixed. Remaining: Phase 4 CI gating;
  see "M3 progress" below.

Everything is on `main` and CI is green (compile + three headless smoke tests on
JDK 17). GitHub issues #1–#8 are closed; #9–#16 remain.

## How to run what exists

```bash
sbt compile                                              # builds engine + all modules
sbt "selfplay/runMain arcsbot.selfplay.SelfPlay"         # M1: full headless game, prints journal
sbt "engineBridge/runMain arcsbot.engine.Repl selftest"  # M2: full game via the bridge + replay checks
sbt "engineBridge/runMain arcsbot.engine.Repl sqltest"   # M2: SQLite persist + conflict + reload
sbt "engineBridge/runMain arcsbot.engine.Repl play"      # M2: interactive stdin play
```

Toolchain: sbt + JDK 17/21, Scala 2.13.16 (matches upstream HRF).

## What's built

### Vendored engine (`hrf-engine/`)
The HRF Arcs + Blighted Reach engine, MIT-licensed, treated as a pristine
external dependency. **Do not edit the rules logic** — only build/packaging.
`build.sbt`'s `hrfEngine` project cross-compiles it to the JVM:
- `excludeFilter` drops browser/Scala.js sources (criterion: imports a browser
  package — `org.scalajs.dom` / `hrf.html` / `hrf.canvas` / `hrf.web` / `sprites`
  — and has no `*-jvm.scala` shim). JVM-clean rules deps that must stay IN:
  `selects2.scala`, `new-new-new-tracker.scala` (`hrf.tracker4`), `settings.scala`,
  `styles.scala` (the `arcs.elem` package object lives in `arcs/styles.scala`).
- `hrf.BuildInfo` is synthesized via a `sourceGenerator` (upstream uses
  sbt-buildinfo, which we don't vendor).

### `modules/selfplay`
Headless self-play CLI (M1 proof). Reuses `arcs.CampaignHost` (bot AI + oracle
resolution) to play a full game and print the round-trippable journal.

### `modules/engine-bridge` (the M2 work — the only module importing `arcs.*`)
- **`EngineSession`** — interactive `BaseHost.main`. `create`/`load`/`pending`/
  `apply`/`undoTo`. Drives `performContinue`, auto-resolves forced/oracle/
  single-Ask (journaling oracle results), expands raw `Ask` actions into
  performable leaves via `game.explode`, and stops at a real multi-option `Ask`
  as a `Turn`. `load()` replays the journal (forced steps regenerated, external
  actions consumed from the log).
- **`Journal`** — `trait` + `InMemory` + **`SqlJournal`** (JDBC, `(game_id, idx)`
  PK, optimistic concurrency via PK clash → `Conflict`; SQLite in tests,
  Postgres-compatible).
- **`ElemText`** — flattens HRF rich-text `Elem` → plain text (via `Elem.text`).
  The single Discord-facing text entry point.
- **`Repl`** — the M2 harness (`selftest` / `sqltest` / `play`).

The public bridge API (Discord-facing, no `arcs.*` leakage): `Seat`, `MoveOption`,
`Turn`, `Outcome`, `EngineSession`, `Journal`.

### `modules/renderer`, `modules/bot`
Still scaffolds (stubs). M3 fills in `renderer`; M4 fills in `bot`.

## Key facts a new session needs

1. **Seating + options live OUTSIDE the journal.** Journal line 0 is just
   `StartAction(version)`; the campaign start reads `game.seating`/`options` from
   the `arcs.Game` constructor args. So `EngineSession.load`/`create` take
   `factionIds` + `optionIds`, and any persistence must store these alongside the
   journal (the `good-game` model; see ARCHITECTURE → Persistence: `seats` +
   `options_json`).

2. **`HostTest` ends the game at act-1 setup** (`arcs/game-blight.scala:896`).
   The self-tests use it so a full game completes quickly. Real games omit it.

3. **Fates / multi-act campaign — DONE (M5, 2026-06-25).** The bridge now plays
   full Act I→III campaigns end-to-end (verified by `Repl m5probe`: random games
   reach a real game-over and replay to the identical winner). Two fixes:
   - **Interactive selects** (`YY/XXSelectObjectsAction` — Fates, hidden-info):
     `EngineSession.decideAsk` was exposing HRF's un-pickable `HiddenChoice`
     "explode" marker as an option (→ "unknown continue …ExplodeAction"). It now
     does what HRF's own UI/bot do — `game.explode(_, false, None)`, then filter
     `Hidden`/`Info`/`Unavailable`; degenerate selects fall through cleanly.
   - **Acts II/III** are implemented upstream but gated by `MetaBR.development`
     (`hrf.HRF.version.startsWith("test")`); the public build forces `Act1Only`.
     We set `BuildInfo.version` = `test-0.8.140` (build.sbt) to enable them. The
     default game config is now `NoFate` (`EngineSession.DefaultOptionIds`).
     Note: `Act1Only` is not in dev-mode's `MetaBR.options`, so a journal built
     with it desyncs the browser renderer (this was a render `MatchError`).

## M3 progress (Path B board renderer, issue #9)

Goal: given a journal (+ seating/options), produce `board.png` by driving HRF's
real browser UI headless and screenshotting it. **This now works end-to-end** —
a faithful Blighted Reach board (~5.8 MB, 2133 sampled colours: populated faction
tableaus, court cards, ambitions, laws, and a map full of fleets at the requested
action). See memory `m3-scalajs-build` for the deep build notes.

**Done & verified:**
- **Phase 0 — Scala.js build.** New `hrfWeb` project (`build.sbt`) compiles HRF's
  vendored *browser* UI (Arcs + Blighted Reach) to JS + our Arcs-only shell
  `hrf-web/src/.../hrf/Shell.scala` (replaces the un-vendorable upstream
  `hrf.scala`). `sbt hrfWeb/fastLinkJS` → `hrf-web/target/scala-2.13/
  hrf-web-fastopt/main.js`. Uses scalajs-dom 2.8.0 + a `package object ui` read
  shim + a **build-time patch** of `grey/grey-map/runner` for the DOM assignments
  2.x dropped (keeps `hrf-engine/` byte-for-byte pristine — re-applies on
  re-vendor). Not aggregated into `root` yet (mirrors how M1 staged hrfEngine).
- **Assets.** `assets/webp2/arcs/images/` holds the 1047 webp Arcs art files
  (extracted from `hrfn-arcs-assets-*.zip`; layout `hrfn/arcs/assets/<X>` maps
  1:1 to `webp2/arcs/images/<X>`). Git-ignored. **No hrf.im at runtime** — fully
  self-hosted; the asset base is `?assets=` configurable.
- **Phase 2 — exporter.** `EngineSession.replayBundle` + `ReplayBundle`
  (`modules/engine-bridge/.../ReplayExport.scala`) emit HRF's `#lobby` + `#replay`
  payloads (journal lines pass through verbatim — `writeActionExternal` ==
  `Serialize.write`). `sbt "engineBridge/runMain arcsbot.engine.RenderExport"`
  produced a real 95-line mid-game journal + correct lobby (`seating R Y B W`,
  options incl. `Act1Only`).

**Phase 3 — RENDERS (verified 2026-06-25):**
- `modules/renderer/.../RenderServer.scala` (localhost static server: host page +
  `main.js` + local `/webp2/` art), `PathBRenderer.scala` (Playwright Chromium →
  full-page screenshot), `RenderSmoke.scala` (+ `InstallBrowser`).
- **Launch blocker — root-caused & fixed.** It was never Defender. Playwright's
  bundled Chromium-1134 hits a Windows **side-by-side activation failure** —
  `chrome.exe` can't resolve its own private version assembly (`129.0.6668.29`),
  surfacing as the misleading `spawn UNKNOWN`. Confirmed via the Application event
  log (`SideBySide`) and a direct `chrome.exe` launch reproducing the SxS error
  outside Playwright. Fix: `PathBRenderer` now honours **`RENDER_BROWSER_CHANNEL`**
  (e.g. `chrome`/`msedge`) to drive the system-installed browser; unset = bundled
  Chromium (so Linux/CI is unchanged). Run locally with `RENDER_BROWSER_CHANNEL=chrome`.
- **Blank-board blocker — root-caused & fixed.** The board build runs inside
  `loader.wait(<all immediate assets>)` (`Shell.scala`). HRF's `Cached*` loaders
  (`hrf-engine/loader.scala`) did `cache.add(url).then{…}` with **no rejection
  handler**, so a single 404 left that URL `Loading` forever and `wait` never
  fired → blank page. The missing file is `webp2/arcs/images/f03/f03-25.webp`
  (the mirror has `f03-25a/b`; HRF's asset list at `arcs/meta.scala:1025` lists
  plain `f03-25` *and* `a/b`, while `f03-26` has only `a/b` — `f03-25` may be a
  stale upstream entry). Fixes: the three `Cached*` loaders now call the
  framework's `fail(url)` on rejection (it already treats `Error` as not-`Loading`,
  so `wait` proceeds), and `Shell.scala` skips failed assets when building the
  `loaded` map so `loader.get` doesn't throw. A missing asset now degrades
  gracefully instead of blanking the board. **Requires `hrfWeb/fastLinkJS`** after
  editing `loader.scala`/`Shell.scala`.
- **Hardening done:** `RenderSmoke` now asserts visual content (samples a pixel
  grid, requires ≥64 distinct colours) so a blank render can't pass the old
  size-only gate. `PathBRenderer` construction now closes Playwright + the server
  if `launch`/`newPage` throws (else the node driver's non-daemon threads hang the
  JVM — this is what hung the prior session). Added `onResponse`/`onRequestFailed`
  network diagnostics to the page logger.

**Blighted-Reach replay-fidelity bug — ROOT-CAUSED & FIXED.** Renders used to throw
(in the browser, swallowed by HRF) `NoSuchElementException: key not found: <faction>`
in `arcs.BlightExpansion.perform` → `factionToState` reading an **empty** `game.states`,
*and* the board canvas never appeared in time (the two were the same root cause).
Diagnosis: a JVM-replay diagnostic (`engineBridge/runMain arcsbot.engine.ReplayCheck`,
reads `replay.txt`) replayed the exact journal cleanly, proving the journal + rules
are correct and the bug was browser-replay-specific. HRF's main replay loop
(`runner.scala` `UIContinue`) performs `Force` continuations even with journal actions
pending, but `Then`/`Milestone` only when the pending list is `Nil` — with actions
pending they fell to the catch-all, which performed the next journal action and
**skipped the forced one**. The campaign setup `ArcsBlightedReachStartAction`
(populates `game.states`) is reached via `Then`, so it was skipped → empty states →
crash at the first `FactionState` access (a fate crisis). HRF's own journals record
`Then` actions; our engine-bridge journal is external-only (forced steps regenerated,
like the JVM `EngineSession.replayStep`). Fix (build-time patches, `hrf-engine/` stays
pristine):
- `runner.scala`: perform `Then`/`Milestone` inline during replay, mirroring `Force`
  (the Nil-only cases still win for live play, where they record). This is the fix
  the headless renderer exercises.
- `base.scala`: the analogous `Then`/`Milestone`-skip in the void replay used by
  undo/scrub (`generateGameVoid` → `performVoid` → `mapForceLog`) — same class of bug,
  fixed for consistency (not exercised by the headless one-shot render).

**Exact next steps:**
1. **Phase 4 — CI gating + ROADMAP.** Env-gate the smoke in CI (`RENDER_SMOKE=1`)
   for headless-browser availability; on Windows runners set `RENDER_BROWSER_CHANNEL`.
   Then update ROADMAP. Per-faction tableau cropping (`renderTableau`) and
   viewer-gated private renders are later refinements.
2. ~~**Cosmetic: `f03/f03-25.webp` 404**~~ FIXED 2026-06-26. Confirmed stale upstream
   entry: no card/disk asset uses bare `f03-25` (fates use `f03-25a/b`, like `f03-26a/b`).
   Removed `ImageAsset("f03-25")` from `arcs/meta.scala`. Clearing it in a running render
   needs `hrfWeb/fastLinkJS` + render restart.

Run the render pipeline anytime:
```bash
sbt hrfWeb/fastLinkJS                                        # build the JS bundle (after any loader/Shell edit)
sbt "engineBridge/runMain arcsbot.engine.RenderExport"       # write lobby.txt/replay.txt
RENDER_BROWSER_CHANNEL=chrome \                              # Windows: use system Chrome (SxS fix)
  sbt "renderer/runMain arcsbot.render.RenderSmoke 60"       # -> hrf-web/render-out/board.png
```
(PowerShell: `$env:RENDER_BROWSER_CHANNEL='chrome'; sbt "renderer/runMain arcsbot.render.RenderSmoke 60"`.)
