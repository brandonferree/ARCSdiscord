# Discord UX: channels, commands, turn flow

Modeled on async TI4's conventions, adapted to Arcs and to HRF's `Ask`-driven
move model.

## Channel / role layout per game

A game ("table") gets its own space in a guild:

- `#arcs-<name>` — **the table**: board images, the public log, turn pings.
- `#arcs-<name>-bots` (optional) — verbose bot/log spam kept out of the table.
- A thread or DM per player for **private info** (hand, secret Fate, blind plays).
- A role `@arcs-<name>` mentioning all seated players (for "game started",
  "round over").

The bot creates these on game creation and records them in `channels`
(see `docs/ARCHITECTURE.md` schema).

## Slash commands

Setup / lifecycle:
- `/arcs new [name]` — create a table (channel + role), register a game with meta
  `arcs-br`.
- `/arcs join <faction>` — claim a seat (Red/Yellow/Blue/White). Seats map to
  `Faction` (`arcs/meta.scala:358`).
- `/arcs options ...` — set campaign options (e.g. `Act1Only`, `RandomPlayerOrder`,
  `RandomizePlanetResources`; `arcs/meta.scala:319`+). Defaults to the standard
  Blighted Reach campaign.
- `/arcs start` — validate the seating (`MetaBR.validateFactionSeatingOptions`,
  `arcs/meta.scala:336`), create the `Game`, drive to the first `Ask`, post the
  board, ping the first player.

Play:
- `/arcs board` — re-post the current board render.
- `/arcs moves` — (ephemeral) show *your* current legal options if it's your
  `Ask`.
- `/arcs do <n>` — choose option *n* from your current legal move list.
- `/arcs undo` — propose an undo (maps to `UndoAction`, `base.scala:194`;
  truncate journal to an index + replay). Gate behind confirmation / opponent
  consent per HRF etiquette (HRF tips: *"Ask other players before undoing dice
  rolls or card reveal."*, `meta.scala:204`).
- `/arcs log [n]` — show the last *n* log lines (the journal, human-readable).

Most "do" interactions are better as **buttons / select menus** than as
`/arcs do <n>` typing — see below.

## Presenting an `Ask` to a player

When `engine-bridge` reports `Turn(faction, actions)`:

1. Resolve `faction` → seated Discord user.
2. Render `actions` via the `Elem` flattener:
   - ≤ 5 options → a row of **buttons** (`option` text on each).
   - ≤ 25 options → a **select menu**.
   - more, or hierarchical (Arcs actions are multi-step) → paginated select, or
     fall back to a numbered list + `/arcs do <n>`.
3. Include `Back` / `Cancel` options (`base.scala:284`/`:286`) as their own
   buttons so a player can unwind a partial multi-step action before committing.
4. **@-ping** the player in the table channel; put the interactive controls in an
   **ephemeral** message (or DM) so only they can click, and so hidden info
   isn't leaked.

Because a single Arcs decision is often a chain of `Ask`s (declare action →
choose card → choose targets → confirm), the bot loops steps 1–3 with the *same*
player until the engine's `Ask.faction` changes or the action is committed. The
player experiences "take my whole turn," even though it's several engine `Ask`s.

## Turn pings & pacing

- On each new `Turn`, ping only the active player.
- Optionally support a per-game **clock** / nudge (async TI4 sends reminders).
  This is pure bot bookkeeping; the engine doesn't care about wall-clock time.
- Round/act transitions (`Log`/`Milestone` continuations) → post a summary +
  fresh board, ping the role.

## Table talk

A general `#arcs-<name>` chatter is fine; async TI4 separates "table talk" from
bot actions. Keep bot move-menus ephemeral and board posts pinned/updated so the
channel stays readable.

## Spectators

Anyone with read access to the table channel can watch (public board only).
Private renders go only to seated players. This matches HRF's read/append
access-rights split (`GoodGame.scala:59`).
