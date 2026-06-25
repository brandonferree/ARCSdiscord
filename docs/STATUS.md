# Status & handoff

Last updated: **2026-06-25**. Current state of the build, what's done, and the
exact starting point for the next milestone. Read this first when resuming work.

## TL;DR

- **M1 (HRF engine on the JVM) — DONE.**
- **M2 (Journal + EngineSession) — DONE.**
- **Next: M3 — first board render** (see [RENDERING.md](RENDERING.md); Path B =
  headless-browser screenshot of HRF's own Arcs UI → PNG).

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

3. **Fates / intermission hidden-info selection is M5, not done.** Those raise
   "empty explode" / hidden-question; the bridge catches and returns
   `Outcome.Rejected` rather than crashing. Implementing real Fate / hidden-info
   handling (private per-player options, secret selection) is the main remaining
   campaign gap. A non-`HostTest` game will hit this at the first intermission.

## Starting point for M3

Goal: given a journal (+ seating/options), produce `board.png`.

- Read [RENDERING.md](RENDERING.md). Path B (fast cut) = drive a headless browser
  (e.g. Playwright for JVM) against HRF's own Arcs UI pointed at the journal, and
  screenshot the board. Path A (native JVM compositor) is M6.
- The renderer should consume a *projection* of game state or the journal, not
  import `arcs.*` directly if avoidable — keep `engine-bridge` the only
  `arcs.*` importer (see ROADMAP cross-cutting rules). In practice Path B needs
  the journal/log + HRF's web assets, not the JVM `Game` object.
- Open issue: **#9** "BoardRenderer Path B (headless-browser screenshot)".

**First instruction for the M3 session:** "Read docs/STATUS.md and docs/RENDERING.md,
then start M3 (issue #9): stand up `BoardRenderer` Path B — render a journal to
board.png via a headless browser against HRF's Arcs UI."
