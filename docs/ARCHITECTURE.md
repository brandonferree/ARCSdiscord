# Architecture

This is the end-to-end design for ARCS Discord. Read `docs/ASYNC-MODEL.md` and
`docs/HRF-ENGINE.md` first — this document assumes their vocabulary.

## Components

```
                          Discord
                            │ slash commands, button clicks, DMs
                            ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │  modules/bot  (JDA)                                                     │
 │   • slash commands & component interactions                            │
 │   • channel/thread <-> game mapping, role/permission setup             │
 │   • per-player private views via DM/ephemeral                          │
 │   • turn pings                                                          │
 └──────────────────────────────────────────────────────────────────────┘
        │ "player P chose option i of this Ask"          ▲ board PNG, action menus
        ▼                                                 │
 ┌──────────────────────────────────────────────────────────────────────┐
 │  modules/engine-bridge                                                  │
 │   • GameSession: owns one Arcs Game, drives performContinue loop        │
 │   • Journal: append-only action log (source of truth), replay          │
 │   • turn detection: surfaces Ask(faction, actions)                      │
 │   • applies oracle (roll/shuffle/random) deterministically & logs       │
 └──────────────────────────────────────────────────────────────────────┘
        │ uses                                            │ Game state
        ▼                                                 ▼
 ┌───────────────────────────────┐      ┌─────────────────────────────────┐
 │  HRF Arcs engine (JVM jar)     │      │  modules/renderer                │
 │   arcs.* : Game, Meta, Serialize│      │   Game -> BufferedImage -> PNG   │
 │   hrf.base.Gaming, host.*       │      │   (see docs/RENDERING.md)        │
 └───────────────────────────────┘      └─────────────────────────────────┘
        ▲
        │ persisted
 ┌───────────────────────────────┐
 │  Storage                       │
 │   journals (action lines),     │
 │   games, players, channels     │
 └───────────────────────────────┘
```

### `modules/engine-bridge`
Owns the relationship with the HRF engine. Public surface (see
`EngineSession.scala`):

- `load(journal): GameSession` — replay the action log to rebuild `Game` + the
  current `Continue`.
- `pending(session): Option[Turn]` — drive the engine forward through all
  automatic / oracle continuations until it either ends or stops at
  `Ask(faction, actions)`; return `Turn(faction, actions)` (or `None` if game
  over). Oracle steps (`Roll`/`Shuffle`/`Random`) are resolved here using a
  seeded RNG and their `OracleAction` results are appended to the journal so the
  game stays replayable.
- `apply(session, chosen: UserAction): Result` — validate `chosen` is in the
  current `actions`, `Serialize.write` it, append to the journal, and re-derive
  the next pending turn.
- `describe(action): String` / `optionsFor(turn)` — render `UserAction.option` /
  `question` `Elem`s to plain text/markdown for Discord.

This is intentionally the *only* module that imports `arcs.*` / `hrf.*`.
Everything above it speaks in terms of `Turn`, `UserAction` ids, and rendered
strings.

### `modules/renderer`
Turns a `Game` (or a lightweight projection of it) into a PNG. See
`docs/RENDERING.md`. Kept separate so it can be developed/tested independently
and swapped (headless-browser vs. native compositor).

### `modules/bot`
The Discord application (JDA). Knows nothing about Arcs rules. It:
- maps a Discord channel/thread to a `journalId`;
- maps a Discord user to a `Faction` (seat);
- on a player interaction, asks `engine-bridge` to apply the chosen action;
- after each applied action, asks `engine-bridge` for the next `Turn`, renders
  the board, posts it, presents the action menu to the next faction's player,
  and pings them.

## Data flow: one turn

1. Engine-bridge reports `Turn(faction=Blue, actions=[A, B, C, ...])`.
2. Bot renders board → PNG; posts to the game channel with the move menu.
3. Bot resolves `faction=Blue` → Discord user; @-pings them. Private info
   (hand/objectives) goes to that user via DM or ephemeral reply.
4. Player picks option B (button / select / `/arcs do B`).
5. Bot → `engine-bridge.apply(session, B)`.
6. Engine-bridge validates B ∈ actions, writes `Serialize.write(B)`, appends to
   journal at the next index (optimistic-concurrency; reject on conflict).
7. Engine-bridge drives forward (auto-resolving forced + oracle continuations,
   logging oracle results) to the next `Turn` (possibly the same player again,
   e.g. multi-step actions) or `GameOver`.
8. Back to step 2.

Note that one Discord "turn" may map to several engine `Ask`s — Arcs actions are
often multi-step (declare → pick targets → confirm). The bot just keeps
presenting `Ask`s to the same player until the engine moves on to a different
faction. `Back`/`Cancel` `UserAction`s (`base.scala:284`/`:286`) let the player
unwind a partial action before committing.

## Getting HRF onto the JVM classpath

HRF's primary build target is Scala.js, but the **rules core is platform-neutral
and already JVM-capable** — `good-game` (akka) and the `BaseHost` simulators run
on the JVM today. The plan:

1. **Cross-compile HRF to JVM.** Produce a JVM artifact containing the
   game-logic packages — `hrf.base`, `hrf.colmat`, `hrf.serialize`, `hrf.meta`,
   `hrf.options`, `hrf.host`, and `arcs.*` — while **excluding the Scala.js /
   DOM / browser-UI sources**. The HRF build already excludes a set of files for
   the JS target (`build.sbt` `excludeFilter`); the JVM target needs the mirror
   of that: keep the `*-jvm.scala` variants (`log-jvm`, `host-jvm`,
   `timeline-jvm`, `reflect-jvm`, `grey-jvm`) and drop the DOM/canvas/UI ones
   (`elem`, `html`, `canvas`, `sprites`, `ui.scala`, `web`, `loader`, …) plus
   each game's `ui.scala`/`styles.scala` where they pull in browser APIs.
   - `Serialize.parseExpr` uses reflection to resolve action class names
     (`serialize.scala:152`). On JS this is `scalajs.reflect`; on the JVM it must
     be the JVM reflection variant (`reflect-jvm.scala`). Make sure action case
     classes are reachable by reflection (they are plain case classes, so JVM
     reflection via `Class.forName` works).
2. **Decouple `Elem` from the DOM for headless use.** `UserAction.question` /
   `option` return `Elem` (`hrf.elem`). For Discord we only need their *textual*
   content, not DOM rendering. Provide a small `Elem → String/Markdown`
   flattener in `engine-bridge` (or a JVM `elem` shim) so we don't drag in the
   browser renderer. The board image is produced by `modules/renderer`, not by
   `Elem`.
3. **Consume it.** Either:
   - **(a) git submodule + cross-build** the `haunt-roll-fail` sources into a
     `%% "hrf-arcs"` JVM library that this build depends on; or
   - **(b) vendor** the needed sources directly under `modules/engine-bridge`.

   (a) is cleaner for staying in sync with upstream HRF; (b) is faster to bring
   up. Milestone 1 may start with (b) and graduate to (a). Either way, do **not**
   modify upstream HRF game logic — only the build/packaging around it.

> Open task for Milestone 1: confirm which exact source files compile cleanly for
> JVM and produce the minimal exclude list. The `*-jvm.scala` files and the
> existence of `good-game` + `host.scala` simulators strongly indicate the core
> is already JVM-clean; the work is build configuration, not code changes.

## Persistence

Source of truth = the **journal** (ordered action lines per game). Recommended
schema (mirrors `good-game` but owned by this service):

- `games(id, meta='arcs-br', name, options_json, status, created_at)`
- `seats(game_id, faction, discord_user_id, secret)`
- `journal(game_id, idx, action_text, actor, created_at)`  — PK `(game_id, idx)`
- `channels(game_id, guild_id, channel_id, thread_id, role_id)`
- `snapshots(game_id, idx, state_blob)` *(optional)* — periodic cached state /
  rendered board to avoid full replay on every interaction.

Replaying from `idx=0` every time is simplest and always correct; add snapshots
only if replay latency becomes a problem (Arcs games are not huge, so a full
replay per interaction is likely fine to start).

Concurrency: append at `idx = max(idx)+1`; a unique constraint on
`(game_id, idx)` rejects races (the `good-game` 409 pattern, `GoodGame.scala:292`).

## Deployment

One JVM service (bot + engine-bridge + renderer in-process) + a database
(Postgres/SQLite) + the HRF Arcs jar. The renderer needs the Arcs **art assets**
(map tiles, piece icons) available on disk/CDN — HRF serves these from `hrf.im`
today; mirror the Arcs subset for the renderer (see `docs/RENDERING.md`). A
single container is enough for a hobby deployment; the renderer is the only
CPU/asset-heavy part and can be split out later behind the `modules/renderer`
boundary if needed.

## Multi-game / multi-table

Each Discord guild can host many concurrent games. The bot keys everything by
`game_id` derived from the channel/thread, so the same process serves all
tables. `GameSession`s can be loaded on demand and evicted (rebuild by replay),
so memory scales with *active* tables, not total tables.
