# Roadmap

Phased so each milestone is independently demonstrable. The risk is front-loaded:
get the HRF engine running headless on the JVM (M1) and a picture into Discord
(M3) early, because those are the two unknowns. Everything after is content and
polish.

## M0 — Foundation (this repo, now)
- [x] Architecture docs grounded in HRF source.
- [x] Module scaffold with documented integration seams.
- [ ] Choose a license; decide vendor-vs-submodule for HRF (see ARCHITECTURE).

## M1 — HRF Arcs engine on the JVM *(highest risk; do first)*
**Goal:** call the real Arcs engine from a plain JVM `main`, with no browser.
- [ ] Produce a JVM build of the HRF rules core + `arcs.*` (exclude DOM/UI
      sources; keep `*-jvm.scala`). See ARCHITECTURE → "Getting HRF onto the JVM
      classpath".
- [ ] Verify `Serialize.parseExpr` reflection resolves Arcs action classes on the
      JVM (`serialize.scala:152`).
- [ ] Reproduce a headless game loop like `arcs/host.scala` / `BaseHost.main`
      (`host.scala:136`): create `Game`, drive `performContinue`, auto-resolve
      oracle continuations, stop at `Ask`.
- **Demo:** a CLI that plays Arcs against itself (random/AI seats) start→finish,
  printing the journal. This proves the engine works off-browser. *(This is
  essentially what `BaseHost` already does — the milestone is mostly build
  config, not new logic.)*

## M2 — Journal + GameSession
**Goal:** persistent, replayable games behind a clean API.
- [ ] Implement `Journal` (append-only, index-keyed, optimistic concurrency).
- [ ] Implement `GameSession.load/pending/apply` (`engine-bridge`): replay log →
      state, surface `Turn(faction, actions)`, apply a chosen `UserAction`,
      append, advance.
- [ ] `Elem → markdown` flattener for move text.
- **Demo:** a REPL/HTTP harness that loads a journal, prints the current legal
  moves for the active faction, accepts a choice, and advances — no Discord yet.

## M3 — First board render *(second unknown; Path B fast cut)*
**Goal:** an image of the live board.
- [ ] Stand up `BoardRenderer` (Path B: headless-browser screenshot of HRF's own
      Arcs UI pointed at the journal). See RENDERING.
- **Demo:** given a journal, output `board.png`.

## M4 — Discord vertical slice
**Goal:** play a real (small) Arcs decision through Discord end-to-end.
- [ ] JDA bot: `/arcs new/join/start`, channel+role creation, seat→faction map.
- [ ] On `Turn`: post board PNG + present `actions` as buttons/select; ping
      player.
- [ ] On interaction: `apply` → append → advance → re-post.
- [ ] Private hand/objective info via ephemeral/DM.
- **Demo:** 3–4 humans play the opening of an Arcs game asynchronously in a
  Discord server.

## M5 — Full Blighted Reach campaign
- [ ] Campaign options surfaced (`Act1Only`, fate mode, setup cards;
      `arcs/meta.scala:319`+).
- [ ] Multi-act flow, Fate cards (28 `fate-*.scala`), leaders & lore, blight.
- [ ] Round/act transition summaries; campaign scoring.
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
