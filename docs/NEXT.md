# Next session — handoff (2026-06-25)

Small, action-focused. For full detail read [STATUS.md](STATUS.md); roadmap in
[ROADMAP.md](ROADMAP.md).

## Where we are
- **M1, M2, M3 done.** Path B renders a faithful Blighted Reach board headless
  (`board.png` ~5.8 MB). All three M3 blockers (Windows Chromium launch, asset-404
  loader hang, browser `Then`/`Milestone` replay-fidelity bug) are fixed.
- Work is on branch **`m3-board-render`** (pushed, 3 commits on top of `main`).

## Do first
1. **Open the PR** (not yet opened — no `gh`/token on this machine):
   https://github.com/brandonferree/ARCSdiscord/pull/new/m3-board-render
   Body is drafted in the session transcript. Or install `gh` / set `GH_TOKEN`
   and run `gh pr create`. Merge once reviewed.

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

## Loose ends (small, optional)
- **`f03/f03-25.webp` 404** (cosmetic; degrades gracefully). The mirror has
  `f03-25a/b` but not plain `f03-25`. HRF's asset list (`arcs/meta.scala:1025`) has
  plain `f03-25` *and* `a/b` while `f03-26` has only `a/b` — so `f03-25` may be a
  stale upstream entry. Confirm before chasing.
- **Per-faction tableau cropping** (`PathBRenderer.renderTableau`) and viewer-gated
  private renders are first cut → later refinements.
- **M5 gap still open:** multi-act Fates / hidden-info selection — the bridge
  surfaces these as `Rejected` (see STATUS key fact #3). A non-`HostTest` game hits
  this at the first intermission.

## Handy commands
```bash
sbt hrfWeb/fastLinkJS                                          # after any loader/Shell/runner/base edit
RENDER_BROWSER_CHANNEL=chrome \
  sbt "renderer/runMain arcsbot.render.RenderSmoke 60"         # -> hrf-web/render-out/board.png
sbt "engineBridge/runMain arcsbot.engine.ReplayCheck"          # JVM-replay a journal (debug replay fidelity)
sbt "engineBridge/runMain arcsbot.engine.RenderExport"         # write lobby.txt/replay.txt
```
(PowerShell env form: `$env:RENDER_BROWSER_CHANNEL='chrome'; sbt "..."`.)
