# ARCS Discord — Asynchronous *Arcs: The Blighted Reach* over Discord

This repository is the **foundation** for playing **Arcs: The Blighted Reach**
(the Arcs campaign) asynchronously through Discord — in the same style as
[async TI4](https://asyncti4.com): players take their turns over hours or days
by issuing commands in Discord, a bot enforces the rules, and the board is
rendered server-side as an image and posted back to the channel.

It is built to **reuse the existing [HRF](https://hrf.im) (`haunt-roll-fail`)
Arcs rules engine** rather than re-implementing the rules. HRF already has a
complete, battle-tested implementation of Arcs + The Blighted Reach campaign,
including legal-move generation, dice/shuffle resolution, and a fully
serializable action log. This project wraps that engine and gives it a Discord
front end plus a headless board renderer.

> **Status: design + scaffold.** This repo currently contains the architecture
> documents and a module skeleton with documented integration seams. It does
> **not** yet compile end-to-end — wiring it to the HRF engine sources is the
> first implementation milestone (see `docs/ROADMAP.md`). The Scala files under
> `modules/` are intentionally stubs that mark exactly where the HRF engine
> plugs in; they are commentary, not working code, until Milestone 1 lands.

## Why this is tractable

The HRF engine already does the hard part. Three properties make an async
Discord port mostly an *integration* job, not a *re-implementation* job:

1. **Rules are enforced by enumerating legal actions.** At every decision point
   the engine emits `Ask(faction, legalActions)` — it only ever offers actions
   that are legal in the current state. The bot's job is to render that list in
   Discord and feed back the player's choice. The bot never needs to know the
   rules.
2. **A game is an append-only log of one-line text actions.** Every action
   round-trips through `Serialize.write` / `parseAction`. Replay the log →
   reconstruct the exact state. This *is* the async save format.
3. **The engine already runs headless on the JVM.** `arcs/host.scala`
   (`BaseHost`) drives a full game with no browser, auto-resolving dice and
   shuffles. The bot is essentially an interactive `BaseHost` whose "ask a
   faction" step routes to Discord instead of a bot AI.

See `docs/HRF-ENGINE.md` for the grounded details (with file/line references
into the HRF source).

## The one genuinely new piece: server-side rendering

HRF renders the board in the **browser** (Scala.js + DOM/canvas, see
`arcs/ui.scala`, `canvas.scala`, `sprites.scala`). A Discord bot has no browser,
so it needs a **headless renderer** that composes a board image (PNG) from game
state + art tiles — exactly what async TI4 does. `docs/RENDERING.md` lays out
two paths (re-host the Scala.js client in a headless browser, vs. a native
JVM image compositor) and recommends one.

## Documents

| Doc | What it covers |
|-----|----------------|
| [`docs/ASYNC-MODEL.md`](docs/ASYNC-MODEL.md) | What "async like TI4" means and how it maps onto HRF's journal model |
| [`docs/HRF-ENGINE.md`](docs/HRF-ENGINE.md) | How the HRF engine works: `Gaming`, `Action`, `Continue`/`Ask`, serialization, the journal server — with source references |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | The end-to-end system design: components, data flow, persistence, deployment |
| [`docs/RENDERING.md`](docs/RENDERING.md) | The headless board renderer (the new component) |
| [`docs/DISCORD-UX.md`](docs/DISCORD-UX.md) | Channel layout, slash commands, turn/ping flow, table talk |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phased milestones from "echo a legal-action list" to "full Blighted Reach campaign" |
| [`docs/PROJECT-SETUP.md`](docs/PROJECT-SETUP.md) | GitHub org structure: labels, milestones, and seed issues to create |

## Module layout (scaffold)

```
build.sbt                     multi-module build: engine-bridge, renderer, bot
project/
modules/
  engine-bridge/              drives the HRF Arcs engine headless; owns the journal
  renderer/                   headless board -> PNG
  bot/                        JDA Discord bot: maps Asks <-> Discord, pings, posts board
docs/                         the design
```

## Prerequisites for Milestone 1

- A JVM-published build of the HRF Arcs engine (HRF cross-compiled to JVM,
  excluding the Scala.js-only DOM/UI sources). See `docs/ARCHITECTURE.md` →
  "Getting HRF onto the JVM classpath".
- A Discord application + bot token.
- JDK 17+, sbt.

## License

No license chosen yet — add one before this goes public/collaborative. Note the
upstream HRF engine has its own license; respect it when vendoring or depending
on its sources.
