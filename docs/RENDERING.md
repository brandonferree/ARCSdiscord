# Headless board rendering

This is the one component HRF does not already give us. HRF renders the Arcs
board in the **browser** (Scala.js → DOM/canvas: `canvas.scala`, `sprites.scala`,
`arcs/ui.scala`, `arcs/styles.scala`). A Discord bot must produce a **PNG
server-side** from game state, like the composited hex map async TI4 posts.

## What has to be drawn (Arcs: The Blighted Reach)

Grounded in `arcs/game.scala`:

- The **map**: clusters of systems/regions; planets with resource slots
  (`CityResourceSlot`, `Overflow`, `game.scala:121`/`:130`), gates/connections.
- **Pieces** = `Figure(faction, piece, index)` (`arcs/serialize.scala:26`):
  ships, buildings (cities/starports), the Loyal/agent pieces, blight, etc.,
  drawn in each faction's color (`Faction extends Color`, `game.scala:27`).
- **Resources** = `ResourceToken(resource, index)` (`:27`) in slots.
- **Court** and **card areas**: `CourtLocation`s, `Hand/Played/Blind`
  (`game.scala:258`), the ambition boxes (`AmbitionBoxContent`, `:178`).
- **Per-faction boards**: power/VP, captives/trophies/favors
  (`Outrage/Trophies/Captives/Favors`, `game.scala:446`), resources.
- **Campaign overlays**: Fate cards (28 `fate-*.scala`), act/chapter markers,
  blight track, leaders & lore.

Not all of it must be one image. Async TI4 uses a big map image plus separate
"player area" images and text summaries. Plan for **several smaller renders**
(map, each player's tableau, the court) rather than one giant canvas — they're
easier to read on mobile and cheaper to regenerate.

## Two implementation paths

### Path A — Native JVM image compositor (recommended)
Write a renderer in the bot's JVM using `java.awt.Graphics2D` /
`BufferedImage` (or [skija]/skia for nicer output) that:

1. reads a **projection** of `Game` state (positions of `Figure`s, tokens,
   cards, tracks) exposed by `engine-bridge`;
2. composites pre-exported **art tiles** (map background, planet art, piece
   icons, card art) at computed coordinates;
3. draws counts/labels;
4. encodes PNG.

Pros: full control, fast, no browser, easy to run in a container, matches the
async-TI4 approach exactly. Cons: you re-derive layout coordinates and must
export the art assets.

Asset source: HRF already has all the Arcs art (served from `hrf.im`; the local
mirror lives under `haunt-roll-fail/hrf/` per `.gitignore`). Mirror the Arcs
subset (map regions, piece sprites, card faces) into this repo's asset store.
`sprites.scala` / `arcs/styles.scala` map logical pieces → image assets and
contain the sizing/scale metadata you can lift for layout.

Layout coordinates: the hard part. Two ways to get them:
- lift region/slot coordinates from `arcs/ui.scala` / map definitions (HRF
  positions everything for the browser already — those numbers are the layout);
- or define your own coordinate table for a custom-drawn map.

### Path B — Re-host the existing Scala.js UI in a headless browser
Run HRF's actual web client (which already renders Arcs perfectly) in a headless
browser (Playwright/Puppeteer), point it at a journal, and **screenshot** the
board. This is closest to "reuse what exists."

Pros: pixel-perfect, zero new layout work, automatically correct as HRF evolves.
Cons: heavyweight (a headless Chrome per render or a long-lived render worker),
needs the JS client served, slower, more fragile in CI/containers. Good for a
**fast first cut** (get *a* picture into Discord quickly), with Path A as the
durable solution.

> Recommendation: start with **Path B** to get end-to-end pictures in Discord on
> day one (prove the loop), then build **Path A** for the real product. Keep the
> `BoardRenderer` interface (`modules/renderer/.../BoardRenderer.scala`) stable
> so the implementation can swap underneath.

## Rendering text: `Elem` → Discord

Move menus and logs come from HRF as `Elem` (rich text, `hrf.elem`). The bot
needs plain text/markdown, not DOM. Provide an `Elem` flattener in
`engine-bridge` that walks the `Elem` tree and emits Discord markdown
(bold/italics/emoji for icons). The action list shown to a player is just
`actions.map(a => flatten(a.option))`. This avoids pulling the browser renderer
onto the JVM at all.

## Private vs. public renders

- **Public** (game channel): the map + face-up court + each player's public
  tableau (power, VP, visible pieces).
- **Private** (DM / ephemeral): the player's **hand**, **blind-played** cards
  (`Blind`, `game.scala:260`), secret Fate info. The renderer takes a
  `viewer: Option[Faction]` and includes hidden info only for that viewer —
  mirroring how HRF's UI already gates per-faction views.
