package arcsbot.discord

import java.sql.Connection
import scala.collection.mutable

/* =============================================================================
 * GameRepository — durable metadata for a game that lives OUTSIDE the journal.
 *
 * The journal (SqlJournal) is the game's action log; but seating, options, the
 * Discord channel/role, and whether the game has started are not in it (see
 * STATUS key fact #1 — seating/options live in the Game ctor, not the journal).
 * This stores exactly that so a restarted bot can reload a game: read the record,
 * then EngineSession.load(journal, factionIds, optionIds).
 *
 * Two implementations: InMemory (tests / default) and Sql (SQLite or Postgres —
 * standard SQL, caller owns the Connection).
 * ===========================================================================*/

/** Persisted game metadata. `seats` is faction→user in claim (seating) order. */
final case class GameRecord(
    gameId: String,
    channelId: String,
    name: String,
    roleId: Option[String],
    seats: Seq[(String, String)],
    optionIds: Seq[String],
    started: Boolean
)

trait GameRepository {
  def get(gameId: String): Option[GameRecord]
  def byChannel(channelId: String): Option[GameRecord]
  def all: Seq[GameRecord]
  def upsert(rec: GameRecord): Unit
}

object GameRepository {

  final class InMemory extends GameRepository {
    private val byId = mutable.LinkedHashMap.empty[String, GameRecord]
    def get(gameId: String): Option[GameRecord]       = synchronized(byId.get(gameId))
    def byChannel(channelId: String): Option[GameRecord] =
      synchronized(byId.values.find(_.channelId == channelId))
    def all: Seq[GameRecord]                          = synchronized(byId.values.toVector)
    def upsert(rec: GameRecord): Unit                 = synchronized { byId(rec.gameId) = rec }
  }

  /** SQL-backed metadata. `seats`/`optionIds` are encoded as delimited strings
    * (faction ids and Discord snowflake user ids contain no `;`/`=`). */
  final class Sql private (conn: Connection) extends GameRepository {
    import Sql._

    def get(gameId: String): Option[GameRecord] =
      one("SELECT game_id, channel_id, name, role_id, seats, options, started FROM games WHERE game_id = ?", gameId)

    def byChannel(channelId: String): Option[GameRecord] =
      one("SELECT game_id, channel_id, name, role_id, seats, options, started FROM games WHERE channel_id = ?", channelId)

    def all: Seq[GameRecord] = {
      val ps = conn.prepareStatement("SELECT game_id, channel_id, name, role_id, seats, options, started FROM games ORDER BY rowid ASC")
      try {
        val rs = ps.executeQuery()
        val buf = Vector.newBuilder[GameRecord]
        while (rs.next()) buf += read(rs)
        buf.result()
      } finally ps.close()
    }

    def upsert(rec: GameRecord): Unit = {
      val ps = conn.prepareStatement(
        """INSERT INTO games (game_id, channel_id, name, role_id, seats, options, started)
          |VALUES (?, ?, ?, ?, ?, ?, ?)
          |ON CONFLICT(game_id) DO UPDATE SET
          |  channel_id = excluded.channel_id, name = excluded.name, role_id = excluded.role_id,
          |  seats = excluded.seats, options = excluded.options, started = excluded.started""".stripMargin)
      try {
        ps.setString(1, rec.gameId)
        ps.setString(2, rec.channelId)
        ps.setString(3, rec.name)
        ps.setString(4, rec.roleId.orNull)
        ps.setString(5, encodePairs(rec.seats))
        ps.setString(6, rec.optionIds.mkString(";"))
        ps.setInt(7, if (rec.started) 1 else 0)
        ps.executeUpdate()
      } finally ps.close()
    }

    private def one(sql: String, key: String): Option[GameRecord] = {
      val ps = conn.prepareStatement(sql)
      try { ps.setString(1, key); val rs = ps.executeQuery(); if (rs.next()) Some(read(rs)) else None }
      finally ps.close()
    }

    private def read(rs: java.sql.ResultSet): GameRecord = GameRecord(
      gameId    = rs.getString(1),
      channelId = rs.getString(2),
      name      = rs.getString(3),
      roleId    = Option(rs.getString(4)),
      seats     = decodePairs(rs.getString(5)),
      optionIds = split(rs.getString(6)),
      started   = rs.getInt(7) != 0
    )
  }

  object Sql {
    def initSchema(conn: Connection): Unit = {
      val st = conn.createStatement()
      try {
        st.executeUpdate(
          """CREATE TABLE IF NOT EXISTS games (
            |  game_id    TEXT PRIMARY KEY,
            |  channel_id TEXT NOT NULL,
            |  name       TEXT NOT NULL,
            |  role_id    TEXT,
            |  seats      TEXT NOT NULL,
            |  options    TEXT NOT NULL,
            |  started    INTEGER NOT NULL
            |)""".stripMargin)
        st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS games_channel ON games(channel_id)")
      } finally st.close()
    }

    def apply(conn: Connection): Sql = { initSchema(conn); new Sql(conn) }

    private def encodePairs(ps: Seq[(String, String)]): String = ps.map { case (a, b) => s"$a=$b" }.mkString(";")
    private def decodePairs(s: String): Seq[(String, String)] =
      split(s).flatMap(_.split("=", 2) match { case Array(a, b) => Some(a -> b); case _ => None })
    private def split(s: String): Seq[String] = if (s == null || s.isEmpty) Nil else s.split(";").toVector
  }
}
