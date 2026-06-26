# Next session — handoff (2026-06-25)

Small, action-focused. For full detail read [STATUS.md](STATUS.md); roadmap in
[ROADMAP.md](ROADMAP.md).

## Where we are
- **M1, M2, M3 done; M5 engine/bridge done; M4 turn loop done.** Path B renders a
  faithful board headless; the bridge plays full Act I→III campaigns (Fates) and
  replays to the identical winner; the Discord bot's turn loop plays a full game
  end-to-end headlessly.
- PRs open: #17 (M3), #18 (M5, based on m3-board-render). M4 work is the latest.

## Do first
1. Merge PR [#17](https://github.com/brandonferree/ARCSdiscord/pull/17) (M3) →
   then [#18](https://github.com/brandonferree/ARCSdiscord/pull/18) (M5) →
   then the M4 PR. `gh` is installed + authenticated on this machine.

## M4 — Discord vertical slice (turn loop done; live run untested)
- `modules/bot` (JDA 5): `GameStore` (in-memory lifecycle), `TurnDriver` (pure
  loop → `BotEffect`s), `GameCommands` (JDA adapter: `/arcs new|join|start|board|
  moves|do`, buttons/select, seat-enforced).
- **Verify headless:** `sbt "bot/runMain arcsbot.discord.BotDryRun [seed]"` — plays
  a full game through the bot effects with the stub renderer (no token).
- **Run live (needs a token):** `DISCORD_TOKEN=… [DISCORD_GUILD=…]
  RENDER_BROWSER_CHANNEL=chrome sbt "bot/run"`. `DISCORD_GUILD` = instant guild
  commands; `RENDER_STUB=1` skips the browser. The live path is NOT yet tested.
- **Deferred follow-ups:** dedicated channel/role auto-creation, private hand/
  objective via DM, SQL persistence, `/arcs undo|log`, intermission-report posts.

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
