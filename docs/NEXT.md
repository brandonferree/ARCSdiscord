# Next session — handoff (2026-06-25)

Small, action-focused. For full detail read [STATUS.md](STATUS.md); roadmap in
[ROADMAP.md](ROADMAP.md).

## Where we are
- **M1, M2, M3 done; M5 engine/bridge done.** Path B renders a faithful Blighted
  Reach board headless. The bridge now plays full Act I→III campaigns (Fates and
  all) and replays them to the identical winner.
- Work is on branch **`m3-board-render`** (PR #17 open).

## Do first
1. PR [#17](https://github.com/brandonferree/ARCSdiscord/pull/17) is open
   (labels: renderer, enhancement). `gh` is installed + authenticated on this
   machine now. Merge once reviewed.

## Then: M4 — Discord vertical slice (next milestone)
Goal: play a real (small) Arcs decision through Discord end-to-end.
- JDA bot: `/arcs new|join|start`, channel+role creation, seat→faction map.
- On a `Turn`: post the board PNG + present `actions` as buttons/select; ping player.
- On interaction: `EngineSession.apply` → append → advance → re-post.
- Private hand/objective info via ephemeral/DM.
- Keep `engine-bridge` the only module importing `arcs.*`/`hrf.*`.
- The renderer is ready to call: `PathBRenderer.render(session, viewer)`; set
  `RENDER_BROWSER_CHANNEL=chrome` locally on Windows. Reuse one long-lived renderer
  (launching Chromium per render is the expensive part; it's not thread-safe —
  serialise calls).

## M5 — full campaign (engine/bridge done 2026-06-25)
- Bridge plays full Act I→III. Run `sbt "engineBridge/runMain arcsbot.engine.Repl
  m5probe [seed]"` (no seed → seeds 1–8): random campaigns to game-over + replay
  check. See STATUS key fact #3 for the two fixes (interactive-select handling +
  `BuildInfo.version` = `test-0.8.140` to enable dev mode / Acts II–III).
- Default game config is now `NoFate` full campaign (`DefaultOptionIds`). Do **not**
  use `Act1Only` for rendered games — it's absent from dev-mode's option list and
  desyncs the browser (render `MatchError`).
- Remaining M5 is UI-side (M4): surface fate mode / intermission reports in Discord.

## Loose ends (small, optional)
- **`f03/f03-25.webp` 404** (cosmetic; degrades gracefully). The mirror has
  `f03-25a/b` but not plain `f03-25`. HRF's asset list (`arcs/meta.scala:1025`) has
  plain `f03-25` *and* `a/b` while `f03-26` has only `a/b` — so `f03-25` may be a
  stale upstream entry. Confirm before chasing.
- **Per-faction tableau cropping** (`PathBRenderer.renderTableau`) and viewer-gated
  private renders are first cut → later refinements.

## Handy commands
```bash
sbt hrfWeb/fastLinkJS                                          # after any loader/Shell/runner/base edit
RENDER_BROWSER_CHANNEL=chrome \
  sbt "renderer/runMain arcsbot.render.RenderSmoke 60"         # -> hrf-web/render-out/board.png
sbt "engineBridge/runMain arcsbot.engine.ReplayCheck"          # JVM-replay a journal (debug replay fidelity)
sbt "engineBridge/runMain arcsbot.engine.RenderExport"         # write lobby.txt/replay.txt
```
(PowerShell env form: `$env:RENDER_BROWSER_CHANNEL='chrome'; sbt "..."`.)
