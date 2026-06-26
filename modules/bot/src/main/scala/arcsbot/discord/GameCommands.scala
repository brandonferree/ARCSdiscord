package arcsbot.discord

import arcsbot.engine._

import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.{ButtonInteractionEvent, StringSelectInteractionEvent}
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.build.{Commands, SubcommandData, OptionData}
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.entities.channel.concrete.{TextChannel, PrivateChannel}
import net.dv8tion.jda.api.entities.{Message, Role, Member}

import scala.jdk.CollectionConverters._

/* =============================================================================
 * GameCommands — the JDA adapter for the M4 vertical slice.
 *
 * Translates Discord interactions into GameStore / TurnDriver calls, then
 * executes the resulting BotEffects as JDA actions (board uploads, move buttons,
 * pings). All Arcs knowledge stays behind `arcsbot.engine`; this file only knows
 * channels, users, buttons, and `BotEffect`s.
 *
 * Slice scope: a game lives in the channel where `/arcs new` was run (no
 * dedicated channel/role auto-creation yet — pings target users directly). The
 * board and a turn ping are posted publicly in the table channel; the active
 * player's move controls (whose option text reveals their hand / legal plays)
 * are DMed to them so hidden info isn't leaked, with a public fallback if the
 * player's DMs are closed (docs/DISCORD-UX.md).
 * ===========================================================================*/
final class GameCommands(store: GameStore, driver: TurnDriver) extends ListenerAdapter {

  /** Encodes the option index into a component id, scoped to a game. */
  private def moveId(gameId: String, idx: Int) = s"m|$gameId|$idx"
  private val MaxButtons = 5
  private val MaxSelect  = 25

  // -- slash commands --------------------------------------------------------

  override def onSlashCommandInteraction(event: SlashCommandInteractionEvent): Unit = {
    if (event.getName != "arcs") return
    val channelId = event.getChannelId
    val userId    = event.getUser.getId

    event.getSubcommandName match {
      case "new" =>
        val name = Option(event.getOption("name")).map(_.getAsString).getOrElse("arcs")
        store.createTable(channelId, name) match {
          case Left(err) => replyEphemeral(event, err)
          case Right(t)  => event.reply(
            s"Created **${t.name}** (`${t.gameId}`). Players: `/arcs join <faction>` " +
            s"(${store.validFactions.mkString("/")}), then `/arcs start`.").queue()
        }

      case "join" =>
        val faction = event.getOption("faction").getAsString
        withGame(event, channelId) { t =>
          store.join(t.gameId, userId, faction) match {
            case Left(err) => replyEphemeral(event, err)
            case Right(_)  =>
              val seated = t.seats.map { case (f, u) => s"$f: <@$u>" }.mkString(", ")
              event.reply(s"<@$userId> takes **${properCase(faction)}**. Seated — $seated").queue()
          }
        }

      case "start" =>
        withGame(event, channelId) { t =>
          event.deferReply().queue()
          store.start(t.gameId) match {
            case Left(err) => event.getHook.sendMessage(err).queue()
            case Right(_)  =>
              ensureRole(event, t)
              event.getHook.sendMessage(s"**${t.name}** begins! Seating: ${t.factionIds.mkString(" → ")}").queue()
              execute(event, t.gameId, driver.advance(t.gameId))
          }
        }

      case "board" =>
        withStarted(event, channelId) { t =>
          event.deferReply().queue()
          val r = driver.renderCurrent(t.gameId)
          channelOf(event, t.gameId).foreach(_.sendFiles(FileUpload.fromData(r.png, r.filename)).queue())
          event.getHook.sendMessage("Current board posted.").setEphemeral(true).queue()
        }

      case "moves" =>
        withStarted(event, channelId) { t =>
          store.sessionOf(t.gameId).pending() match {
            case Outcome.Next(turn) if store.seats.seatForUser(t.gameId, userId).contains(turn.seat) =>
              replyEphemeral(event, "Your options:\n" + numbered(turn.prompt, turn.options))
            case Outcome.Next(turn) =>
              replyEphemeral(event, s"It's **${turn.seat.factionId}**'s move, not yours.")
            case Outcome.GameOver(_) => replyEphemeral(event, "The game is over.")
            case Outcome.Rejected(r) => replyEphemeral(event, s"Engine: $r")
          }
        }

      case "do" =>
        withStarted(event, channelId) { t =>
          val idx = event.getOption("n").getAsInt
          event.deferReply(true).queue()
          execute(event, t.gameId, driver.choose(t.gameId, userId, idx))
          event.getHook.sendMessage("Done.").setEphemeral(true).queue()
        }

      case "undo" =>
        withStarted(event, channelId) { t =>
          event.deferReply(true).queue()
          execute(event, t.gameId, driver.undo(t.gameId, userId))
          event.getHook.sendMessage("Undo processed.").setEphemeral(true).queue()
        }

      case "log" =>
        withStarted(event, channelId) { t =>
          val lines = store.sessionOf(t.gameId).log
          if (lines.isEmpty) replyEphemeral(event, "No log entries yet.")
          else replyEphemeral(event, recentLog(lines))
        }

      case other => replyEphemeral(event, s"Unknown subcommand: $other")
    }
  }

  // -- component interactions ------------------------------------------------

  override def onButtonInteraction(event: ButtonInteractionEvent): Unit =
    parseMove(event.getComponentId).foreach { case (gameId, idx) =>
      event.deferEdit().queue()
      event.getHook.editOriginalComponents().queue() // consume the clicked controls
      execute(event, gameId, driver.choose(gameId, event.getUser.getId, idx))
    }

  override def onStringSelectInteraction(event: StringSelectInteractionEvent): Unit = {
    val gameId = event.getComponentId.stripPrefix("sel|")
    event.getValues.asScala.headOption.flatMap(v => scala.util.Try(v.toInt).toOption).foreach { idx =>
      event.deferEdit().queue()
      event.getHook.editOriginalComponents().queue()
      execute(event, gameId, driver.choose(gameId, event.getUser.getId, idx))
    }
  }

  private def parseMove(id: String): Option[(String, Int)] = id.split('|') match {
    case Array("m", gameId, n) => scala.util.Try(n.toInt).toOption.map(gameId -> _)
    case _                     => None
  }

  // -- effect execution ------------------------------------------------------

  private def execute(event: IReplyCallback, gameId: String, effects: Vector[BotEffect]): Unit =
    effects.foreach {
      case BotEffect.PostBoard(_, render) =>
        channelOf(event, gameId).foreach(_.sendFiles(FileUpload.fromData(render.png, render.filename)).queue())

      case BotEffect.PresentMoves(_, seat, userId, prompt, options) =>
        val rows   = controls(gameId, options)
        val header = moveHeader(seat, userId, prompt)
        userId match {
          // Seated human: ping publicly (no options shown) and DM the controls so
          // the player's hand / legal plays stay private; fall back to a public
          // post if the DM can't be delivered.
          case Some(uid) =>
            channelOf(event, gameId).foreach(_.sendMessage(
              s"<@$uid>, you're up (**${seat.factionId}**) — your move options are in your DMs.").queue())
            sendMovesDM(event, gameId, uid, header, rows)
          // No seated user (AI / headless drive): nothing private to protect.
          case None =>
            postMoves(event, gameId, header, rows)
        }

      case BotEffect.PingActive(_, seat, userId) =>
        channelOf(event, gameId).foreach { ch =>
          ch.sendMessage(userId.map(u => s"<@$u>, you're up (${seat.factionId}).").getOrElse(s"${seat.factionId} is up.")).queue()
        }

      case BotEffect.AnnounceWinners(_, winners) =>
        channelOf(event, gameId).foreach { ch =>
          val w    = if (winners.isEmpty) "Humanity" else winners.map(_.factionId).mkString(", ")
          val ping = roleMention(gameId)
          ch.sendMessage(s"$ping🏁 **Game over!** Winner(s): $w").queue()
        }

      case BotEffect.Notice(_, message) =>
        channelOf(event, gameId).foreach(_.sendMessage(message).queue())

      case BotEffect.Ephemeral(_, message) =>
        if (event.isAcknowledged) event.getHook.sendMessage(message).setEphemeral(true).queue()
        else event.reply(message).setEphemeral(true).queue()

      case BotEffect.Error(_, message) =>
        channelOf(event, gameId).foreach(_.sendMessage(s"⚠️ $message").queue())
    }

  // -- move presentation -----------------------------------------------------

  /** Header line for a move prompt: who is acting + the flattened question. */
  private def moveHeader(seat: Seat, userId: Option[String], prompt: String): String = {
    val who = userId.map(u => s"<@$u>").getOrElse(s"**${seat.factionId}**")
    s"$who — your move" + (if (prompt.nonEmpty) s": $prompt" else "")
  }

  /** Post move controls to the public table channel (AI seats, or DM fallback). */
  private def postMoves(event: IReplyCallback, gameId: String, header: String, rows: Seq[ActionRow]): Unit =
    channelOf(event, gameId).foreach { ch =>
      if (rows.isEmpty) ch.sendMessage(s"$header\n(no actionable options)").queue()
      else ch.sendMessage(header).setComponents(rows.asJava).queue()
    }

  /** DM the active player their move controls, keeping the option text (which
    * leaks their hand / legal plays) off the public channel. If the DM can't be
    * delivered (player disabled DMs, or shares no guild with the bot), note it
    * publicly and fall back to posting the controls in the table channel so the
    * game never stalls. Button/select interactions route back to this listener
    * regardless of whether they were clicked in a DM or the channel. */
  private def sendMovesDM(event: IReplyCallback, gameId: String, userId: String,
                          header: String, rows: Seq[ActionRow]): Unit = {
    def fallback(reason: String): Unit = {
      Console.err.println(s"[dm] could not DM $userId for game $gameId: $reason — posting publicly.")
      channelOf(event, gameId).foreach(_.sendMessage(
        s"⚠️ <@$userId>, I couldn't DM you, so your options are below. Enable " +
        "*Direct Messages* from server members to keep your hand private.").queue())
      postMoves(event, gameId, header, rows)
    }
    event.getJDA.openPrivateChannelById(userId).queue(
      (ch: PrivateChannel) => {
        val msg =
          if (rows.isEmpty) ch.sendMessage(s"$header\n(no actionable options)")
          else ch.sendMessage(header).setComponents(rows.asJava)
        msg.queue((_: Message) => (), (err: Throwable) => fallback(String.valueOf(err.getMessage)))
      },
      (err: Throwable) => fallback(String.valueOf(err.getMessage))
    )
  }

  /** Build move controls: ≤5 → buttons; ≤25 → a select menu; more → first 25 in a
    * select menu (use `/arcs moves` for the full list). */
  private def controls(gameId: String, options: Seq[MoveOption]): Seq[ActionRow] = {
    val pickable = options.filter(_.kind != MoveOption.Info)
    if (pickable.isEmpty) Seq.empty
    else if (pickable.size <= MaxButtons)
      Seq(ActionRow.of(pickable.map(o => button(gameId, o)).asJava))
    else {
      val menu = StringSelectMenu.create(s"sel|$gameId")
        .setPlaceholder("Choose your move")
      pickable.take(MaxSelect).foreach(o => menu.addOption(label(o.text), o.index.toString))
      Seq(ActionRow.of(menu.build()))
    }
  }

  private def button(gameId: String, o: MoveOption): Button = {
    val b = Button.primary(moveId(gameId, o.index), label(o.text))
    o.kind match {
      case MoveOption.Back | MoveOption.Cancel => b.withStyle(net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle.SECONDARY)
      case _                                   => b
    }
  }

  private def label(s: String): String = {
    val t = if (s == null || s.isEmpty) "(option)" else s
    if (t.length > 80) t.take(77) + "…" else t
  }

  private def numbered(prompt: String, options: Seq[MoveOption]): String =
    (if (prompt.nonEmpty) prompt + "\n" else "") +
      options.filter(_.kind != MoveOption.Info)
        .map(o => s"`${o.index}` — ${o.text}").mkString("\n")

  /** Format the tail of the game log for an ephemeral reply, numbered by their
    * true position and kept under Discord's 2000-char message limit. */
  private def recentLog(lines: Seq[String]): String = {
    val MaxLen = 1900
    val shown  = lines.takeRight(40)
    val skipped = lines.length - shown.length
    val numbered = shown.zipWithIndex.map { case (l, i) => s"`${skipped + i + 1}.` $l" }
    val header = "**Game log**" + (if (skipped > 0) s" (last ${shown.length} of ${lines.length})" else "")
    // Drop from the top until it fits, so the most recent entries always survive.
    var body = numbered
    while (body.nonEmpty && (header + "\n" + body.mkString("\n")).length > MaxLen) body = body.tail
    header + "\n" + body.mkString("\n")
  }

  private def properCase(s: String) = store.validFactions.find(_.equalsIgnoreCase(s)).getOrElse(s)

  // -- roles -----------------------------------------------------------------

  /** Create a mentionable `@arcs-<name>` role for the table and assign it to all
    * seated players, so game-wide events (start, game over, future intermissions)
    * can ping everyone at once. Best-effort and async: if the bot lacks the
    * *Manage Roles* permission (or its role sits below the new role in the
    * hierarchy) the failures are logged and the game proceeds without a role —
    * pings just fall back to no mention. No-op if a role already exists (re-runs)
    * or the command came from outside a guild. */
  private def ensureRole(event: SlashCommandInteractionEvent, t: GameStore#Table): Unit = {
    if (t.roleId.isDefined) return
    val guild = event.getGuild
    if (guild == null) return
    val members = t.seats.values.toVector.distinct
    guild.createRole().setName(s"arcs-${t.name}").setMentionable(true).queue(
      (role: Role) => {
        store.setRole(t.gameId, role.getId)
        members.foreach { uid =>
          guild.retrieveMemberById(uid).queue(
            (m: Member) => guild.addRoleToMember(m, role).queue(
              (_: Void) => (),
              (e: Throwable) => Console.err.println(s"[role] assign $uid in ${t.gameId}: ${e.getMessage}")),
            (e: Throwable) => Console.err.println(s"[role] retrieve $uid in ${t.gameId}: ${e.getMessage}"))
        }
      },
      (e: Throwable) =>
        Console.err.println(s"[role] create for ${t.gameId} failed (bot needs Manage Roles): ${e.getMessage}")
    )
  }

  /** `<@&roleId> ` if this game has a role, else empty — prefix for game-wide pings. */
  private def roleMention(gameId: String): String =
    store.table(gameId).flatMap(_.roleId).map(r => s"<@&$r> ").getOrElse("")

  // -- helpers ---------------------------------------------------------------

  private def channelOf(event: IReplyCallback, gameId: String): Option[TextChannel] =
    store.table(gameId).flatMap(t => Option(event.getJDA.getTextChannelById(t.channelId)))

  private def withGame(event: SlashCommandInteractionEvent, channelId: String)(f: GameStore#Table => Unit): Unit =
    store.tableForChannel(channelId) match {
      case Some(t) => f(t)
      case None    => replyEphemeral(event, "No game in this channel. Start one with `/arcs new`.")
    }

  private def withStarted(event: SlashCommandInteractionEvent, channelId: String)(f: GameStore#Table => Unit): Unit =
    withGame(event, channelId) { t =>
      if (t.started) f(t) else replyEphemeral(event, "This game hasn't started yet (`/arcs start`).")
    }

  private def replyEphemeral(event: IReplyCallback, msg: String): Unit =
    event.reply(msg).setEphemeral(true).queue()
}

object GameCommands {
  /** The `/arcs` command tree (registered in Bot.main). */
  def commandData: SlashCommandData =
    Commands.slash("arcs", "Play Arcs: The Blighted Reach")
      .addSubcommands(
        new SubcommandData("new", "Create a game in this channel")
          .addOptions(new OptionData(OptionType.STRING, "name", "Table name", false)),
        new SubcommandData("join", "Claim a faction seat")
          .addOptions(new OptionData(OptionType.STRING, "faction", "Red/Yellow/Blue/White", true)),
        new SubcommandData("start", "Start the game once players have joined"),
        new SubcommandData("board", "Re-post the current board"),
        new SubcommandData("moves", "Privately show your current legal options"),
        new SubcommandData("do", "Choose option number n")
          .addOptions(new OptionData(OptionType.INTEGER, "n", "Option index", true)),
        new SubcommandData("undo", "Roll back your most recent committed move"),
        new SubcommandData("log", "Privately show the recent game log")
      )
}
