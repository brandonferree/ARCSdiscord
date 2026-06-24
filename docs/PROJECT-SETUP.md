# Project setup checklist (run once ARCSdiscord has the Claude GitHub App)

This is the exact org structure to create on GitHub. The next Claude session
(scoped to `brandonferree/ARCSdiscord`) can create all of it via the GitHub API
in one pass. Until then it's a manual checklist.

## Labels

Component + workflow labels so issues are filterable:

| Label | Color | Purpose |
|-------|-------|---------|
| `engine-bridge` | `1d76db` | The HRF-driving module |
| `renderer` | `0e8a16` | Board → PNG |
| `bot` | `5319e7` | Discord / JDA layer |
| `hrf-integration` | `b60205` | Anything touching the upstream HRF engine / JVM build |
| `docs` | `c5def5` | Documentation |
| `blocked` | `d93f0b` | Waiting on another task |
| `good-first-task` | `7057ff` | Small, self-contained starting point |

## Milestones (from docs/ROADMAP.md)

- **M1 — HRF Arcs engine on the JVM** (highest risk; do first)
- **M2 — Journal + GameSession**
- **M3 — First board render**
- **M4 — Discord vertical slice**
- **M5 — Full Blighted Reach campaign**
- **M6 — Native renderer + polish**

## Seed issues

### M1 — HRF Arcs engine on the JVM
1. **Produce a JVM build of the HRF rules core + `arcs.*`** — exclude DOM/UI
   sources, keep `*-jvm.scala`. Output: a `hrf-arcs` jar this build depends on.
   `labels: hrf-integration, engine-bridge`
2. **Determine the exact JVM include/exclude source list** — mirror HRF's JS
   `excludeFilter`; document which files compile clean for JVM.
   `labels: hrf-integration, docs`
3. **Verify `Serialize` action reflection on the JVM** — confirm
   `parseExpr`/`parseAction` resolve Arcs action classes via JVM reflection
   (`reflect-jvm.scala`). `labels: hrf-integration`
4. **Headless self-play CLI** — reproduce `BaseHost.main`: create `Game`, drive
   `performContinue`, auto-resolve oracle continuations, play start→finish,
   print the journal. `labels: engine-bridge, good-first-task`

### M2 — Journal + GameSession
5. **SQL-backed `Journal`** — append-only, `(game_id, idx)` PK, optimistic
   concurrency (409 on conflict). `labels: engine-bridge`
6. **Implement `EngineSession.load/pending/apply`** — replay → state, surface
   `Turn(faction, actions)`, apply choice, append, advance. `labels: engine-bridge`
7. **`Elem → markdown` flattener** — render `UserAction.option`/`question` to
   Discord text without the browser renderer. `labels: engine-bridge, good-first-task`
8. **REPL/HTTP harness** — load a journal, print legal moves, accept a choice,
   advance. `labels: engine-bridge`

### M3 — First board render
9. **`BoardRenderer` Path B (headless-browser screenshot)** — point HRF's own
   Arcs UI at a journal, screenshot the board to PNG. `labels: renderer`

### M4 — Discord vertical slice
10. **JDA bot bootstrap** — token, slash commands `new/join/options/start`,
    channel + role creation, seat→faction map. `labels: bot`
11. **Turn presentation** — on `Turn`: post board, present `actions` as
    buttons/select, ping active player; handle interaction → `apply`. `labels: bot`
12. **Private info via ephemeral/DM** — hand/objectives to the active player
    only. `labels: bot, renderer`

### M5 — Full Blighted Reach campaign
13. **Campaign options + flow** — `Act1Only`/fate mode/setup cards, multi-act,
    Fates, leaders & lore, blight, scoring. `labels: engine-bridge, bot`
14. **Undo with consent** — `UndoAction` → truncate+replay, gated on opponent
    confirmation. `labels: bot, engine-bridge`

### M6 — Native renderer + polish
15. **`BoardRenderer` Path A (native JVM compositor)** — state projection + Arcs
    art tiles → PNG; per-player private renders. `labels: renderer`
16. **Clocks/nudges, spectators, multi-table, stats.** `labels: bot`

## CI

`.github/workflows/ci.yml` runs `sbt compile` on push/PR to `main`. The current
scaffold compiles standalone; M1 wires in the HRF jar and the same job validates
it.
