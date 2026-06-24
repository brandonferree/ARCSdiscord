# How the HRF engine works (the parts a Discord bot needs)

All file/line references are into the upstream `haunt-roll-fail` repository
(the `hauntgames` repo, `haunt-roll-fail/` directory). Read this before touching
`modules/engine-bridge`.

## 1. The `Gaming` abstraction

Everything hangs off `trait Gaming` (`base.scala:24`). A concrete game binds two
abstract types:

- `type F` — a **faction / player**. For Arcs: `type F = Faction`
  (`arcs/package.scala:2`), and `Faction` is `Red, Yellow, Blue, White`
  (`arcs/meta.scala:358`).
- `type G` — the **game state**. For Arcs: `type G = Game`
  (`arcs/package.scala:3`), `class Game(setup, options)` (`arcs/game.scala:1287`).

Per game there is also a `Meta` describing factions, options, setup, validation,
and (crucially) `parseAction` / `writeAction`. For Arcs the campaign meta is
`object MetaBR` — name `"arcs-br"`, label *"Arcs: The Blighted Reach"*
(`arcs/meta.scala:312`). The base game meta is `object Meta` / `CommonMeta`
(`arcs/meta.scala:346`).

## 2. Actions

`sealed trait Action` (`base.scala:137`). The important subtypes for us:

- **`ForcedAction`** (`base.scala:159`) — something the engine does to itself
  (resolve an effect, advance a phase). Not a player choice.
- **`ExternalAction`** (`base.scala:167`) — something that comes from outside the
  engine and gets written to the log. This is the category that lands in the
  journal. Notable ones:
  - **`UserAction`** (`base.scala:260`) — a choice a player makes. Has
    `question` and `option` (how it renders). Subtypes: `Choice`, `Info`, `Back`,
    `Cancel`.
  - `OracleAction` (`base.scala:186`) — the *result* of randomness (a specific
    roll/shuffle outcome), e.g. `ShuffledAction`, `RolledAction`, `RandomAction`.
    These also get logged, so replays are deterministic.
  - `UndoAction` (`base.scala:194`), `CommentAction` (`base.scala:183`),
    `StartGameAction` (`base.scala:188`).

Every action is a `Record` (a case class), which is what makes it serializable
(see §4).

## 3. `Continue` — the state machine, and where players come in

`performContinue(old, action, validating)` (`base.scala:726`) applies an action
and returns a **`Continue`** describing what happens next. The bot inspects the
`Continue` to decide whether it must stop and ask a player. The cases the bot
must handle are exactly the ones in `BaseHost.askFaction` (`host.scala:31`):

| Continue | Meaning | Bot behavior |
|----------|---------|--------------|
| `Force(action)` / `Then(action)` | engine has a forced next action | apply it, keep going |
| `Log(..)`, `Milestone(..)`, `DelayedContinue(..)` | bookkeeping / narration | record, keep going |
| `Roll`, `Roll2/3` | dice must be rolled | roll, append the `RolledAction`, keep going |
| `Shuffle`, `Shuffle2/3`, `ShuffleTake[Until]` | deck/bag shuffle | shuffle, append the `ShuffledAction`, keep going |
| `Random`, `Random2/3` | pick at random | pick, append, keep going |
| `Ask(faction, List(one))` | only one legal action | auto-apply it (no real choice) |
| **`Ask(faction, actions)`** | **the player must choose** | **STOP. This is the turn.** |
| `MultiAsk(...)` | several factions could act | resolve per policy / present to each |
| `GameOver(winners, ..)` | game ended | finish, announce winners |

So the **entire** "when does a human get involved" question reduces to: *the
engine returned `Ask(faction, actions)` with more than one meaningful action.*
That faction's player is on the clock; `actions` is the legal move list to show
them. Rule enforcement is free — illegal moves are simply never in `actions`.

`Ask` is defined at `base.scala:424`; its external/query form at `base.scala:1239`.

Each `UserAction` knows how to describe itself: `def question(implicit g)` and
`def option(implicit g)` (`base.scala:261`) return `Elem`s (HRF's rich-text/markup
type). Those are what the browser renders as buttons; the bot will render them as
Discord text/buttons (see `docs/DISCORD-UX.md` and `docs/RENDERING.md` on `Elem`).

## 4. Serialization — the save format and the wire format

`trait Serializer` (`serialize.scala:44`) gives every action a **one-line text
encoding** and a parser, built on a tiny S-expression-ish grammar (fastparse):

- `write(action)` → `String` (`serialize.scala:56`)
- `parseAction(string)` → `Action` (`serialize.scala:119`)

Arcs customizes this in `arcs/serialize.scala`:

- `prefix = "arcs."` (`arcs/serialize.scala:23`) — class names are resolved under
  the `arcs` package via reflection.
- Custom encodings for the value types that appear inside actions:
  - `Figure` (a piece on the map) → `faction.id + "/" + piece + "/" + index`
    (`arcs/serialize.scala:26`, parsed at `:44`)
  - `ResourceToken` → `resource.name + "#" + index` (`:27`, parsed at `:46`)
  - `Color` → `color.id` (`:28`)

Round-trip integrity is actively verified: `BaseHost.main` writes every action,
re-parses it, re-writes it, and screams if `write(parse(write(a))) != write(a)`
(`host.scala:164`). **This guarantees the journal format is stable** — exactly
what you want for a persistent async save.

> Practical note: an action line like `MoveShips(Red, ...)` is parsed by
> reflection against the `arcs.` package. The bot never constructs action strings
> by hand — it gets concrete `UserAction` objects from `Ask` and calls
> `Serialize.write` on the chosen one.

## 5. The journal server (the existing async backend)

`good-game` (`good-game/GoodGame.scala`) is already a minimal async game backend.
It is *not* game-specific — it's a generic append-only journal service over
HTTP + a SQL DB:

- **Users** (`:17`), **Journals** = games (`:29`), **Entries** = log lines keyed
  by `(journalId, index)` (`:43`), **AccessRights** (`:59`), **Plays** =
  per-player secret join links (`:74`).
- Endpoints: `new-user`, `new-journal`, `new-play`, `grant-read[-append]`,
  `read/<from>` (`:274`), `append/<from>` (`:280`). `append` is
  index-checked — concurrent appends to the same index return `409 Conflict`
  (`:292`), giving optimistic-concurrency for free.

The browser client plays a game by `read`-ing the log, replaying it through the
engine to render, and `append`-ing the chosen action. **A Discord bot is just a
different client of this same pattern** — or it can own the journal directly in
its own DB (recommended for a single integrated service; see
`docs/ARCHITECTURE.md`).

## 6. Running headless (the proof it works without a browser)

`BaseHost` (`host.scala:16`) and `arcs/host.scala` run a complete Arcs game on
the JVM with zero UI:

```
var continue = StartContinue
var a : Action = start                     // the StartGameAction
while (!game.isOver && !a.isGameOver) {
    continue = game.performContinue(Some(continue), a, false).continue
    a = askFaction(game, continue)         // <-- the only "external" step
}
```

`askFaction` (`host.scala:31`) auto-resolves every `Continue` case *except*
multi-option `Ask`, which it hands to a bot AI (`askBot`, `host.scala:111`).

**Our bot replaces `askBot` with "post to Discord and wait for the player."**
That single substitution is the heart of `modules/engine-bridge`.

## 7. What this means for the port

You are not writing Arcs rules. You are writing:

1. A **driver** that owns a `Game`, replays the journal to rebuild it, calls
   `performContinue` in a loop, and stops at `Ask`. (`modules/engine-bridge`)
2. A **translator** between `UserAction` objects and Discord interactions, and
   between Discord interactions and `Serialize.write` lines. (`modules/bot`)
3. A **renderer** that turns `Game` state into a PNG. (`modules/renderer`)

Items 1–2 are small. Item 3 is the real new work.
