# The async model (what "like TI4 async" means) and how it maps onto HRF

## What async TI4 actually does

[Async TI4](https://asyncti4.com) lets a group play Twilight Imperium — a long,
heavy board game — without everyone being present at the same time. The shape of
it:

- **The game lives on a server, not on a table.** Its entire state is stored
  persistently and survives between turns that may be days apart.
- **Players act by typing commands** (slash commands / text) in Discord. There's
  no live shared canvas you drag pieces on; you issue an instruction like "move
  these ships to that system," "produce 2 units here," "play this strategy card."
- **The server holds the rules.** It validates each command, applies it, and
  rejects illegal ones. Players don't move pieces freely; they request legal
  operations and the engine performs them.
- **The board is rendered server-side as an image** and posted back into the
  channel (the big hex map in the screenshots). When it's your turn the bot
  **@-pings you**; you look at the freshly rendered map, issue your command, and
  the bot pings the next player.
- **Per-player private info** (your hand, your secret objectives) is shown only
  to you (DMs / private threads), while the public board is shared.

In short: **persistent server-authoritative game state + command-based turns +
server-rendered board + turn pings.** That's the whole model. Everything else is
UX polish (table-talk channels, undo, statistics, multiple concurrent games).

## HRF already has most of this substrate

The reason Arcs is a good fit is that HRF was built around almost the same
primitives — it just exposes them through a browser UI and a thin journal
server instead of through Discord.

| Async-TI4 concept | HRF equivalent | Where |
|-------------------|----------------|-------|
| Persistent game state | A **journal**: append-only log of action lines, replayed to rebuild state | `good-game/GoodGame.scala` (Journals/Entries tables); `arcs/serialize.scala` |
| Command that mutates state | An `Action` serialized to one line of text | `serialize.scala` `write`/`parseAction` |
| Server validates & applies | `Game.performContinue(continue, action, validating)` | `base.scala:726` |
| Only legal moves allowed | Engine emits `Ask(faction, legalActions)` listing only legal `UserAction`s | `base.scala:424`, `:1239` |
| Auto-resolve dice/shuffle | `Roll`/`Shuffle`/`Random` continuations resolved without a player | `arcs/host.scala`, `host.scala:31` `askFaction` |
| Turn belongs to a player | `Ask`'s `faction : F` is whose decision it is | `base.scala:424` |
| Per-player private info | HRF already distinguishes public vs. per-faction views in its UI | `arcs/ui.scala` |
| Headless run (no browser) | `BaseHost.main` runs a whole game on the JVM | `host.scala:136` |

The two things HRF does **not** already provide for a Discord bot:

1. **A Discord front end** that turns an `Ask(faction, actions)` into something a
   player can answer in chat, and turns their answer back into an `Action`
   appended to the journal. (→ `modules/bot`)
2. **A headless board renderer.** HRF draws to a browser canvas; Discord needs a
   PNG produced server-side. (→ `modules/renderer`, see `docs/RENDERING.md`)

## The core loop, in async terms

```
              ┌─────────────────────────────────────────────────┐
              │  Journal (append-only action log)  [persistent]  │
              └─────────────────────────────────────────────────┘
                        │ replay                    ▲ append(actionLine)
                        ▼                           │
   ┌─────────────────────────────────────────────────────────────────┐
   │  HRF Arcs engine (headless, JVM)                                  │
   │  performContinue(...) ──► Continue                                │
   │     • Roll/Shuffle/Random  ──► auto-resolve, append, continue     │
   │     • Force/Then/Log/...   ──► advance automatically              │
   │     • Ask(faction, actions)──► STOP: a human must choose          │
   │     • GameOver(winners)    ──► end                                │
   └─────────────────────────────────────────────────────────────────┘
                        │ Ask(faction, actions)
                        ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │  Discord bot                                                      │
   │  1. render board PNG, post to game channel                        │
   │  2. present `actions` to `faction`'s player (buttons / select /   │
   │     slash command), @-ping them                                   │
   │  3. on reply: map choice -> Action, validate, append to journal   │
   │  4. drive engine forward until the next Ask (or GameOver)         │
   └─────────────────────────────────────────────────────────────────┘
```

This loop is literally an interactive version of `BaseHost.main`
(`host.scala:136`). In `BaseHost`, when the engine reaches `Ask(f, actions)` it
calls `askBot(...)` to pick a move. In this project, that same hook routes to
**Discord** instead of to an AI bot. Replacing "ask a bot" with "ask a human in
Discord" is the essence of the port.

## Why async is the *easy* mode for this engine

Async actually removes the hardest real-time problems:

- **No live sync / no websockets required.** State only changes when a player
  submits a command. Between commands, nothing is happening.
- **No partial/abandoned interactions to reconcile.** Each submitted action is
  atomic: validate → append → advance. If the player walks away mid-decision,
  the journal simply hasn't grown.
- **Undo is already a first-class concept** in HRF (`UndoAction`,
  `base.scala:194`; the journal is index-addressed so you can truncate/rewind).
  Async TI4-style "undo to here" maps directly onto truncating the journal to an
  index and replaying.
