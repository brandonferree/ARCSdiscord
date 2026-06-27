package arcsbot.discord

import java.sql.{Connection, DriverManager}

/* =============================================================================
 * SessionStore — persistence for "Login with Discord" web sessions (M7 Phase 3).
 *
 * Without this, the OAuth session map (cookie sid -> Discord user id) lives only
 * in memory, so every bot restart logs every web viewer out. This stores it in
 * the same SQLite file the games use (`ARCS_DB`) so sessions survive a restart.
 *
 * It opens its OWN connection to the DB file rather than sharing the GameStore's:
 * the OAuth callback runs on a RenderServer HTTP pool thread while GameStore runs
 * on JDA threads, and a single SQLite Connection isn't safe to use concurrently
 * from multiple threads. Separate connections to one file are fine — SQLite
 * serialises writes with its file lock. All access here is additionally guarded
 * by `lock` so this store's own connection is never touched concurrently.
 *
 * The sid is a bearer credential; storing it in the local DB is the same trust
 * model as the games themselves (local, gitignored). Rows older than the TTL are
 * pruned on startup so the table can't grow without bound.
 * ===========================================================================*/
trait SessionStore {
  /** All persisted (sid, userId) pairs, loaded into memory at startup. */
  def load(): Seq[(String, String)]
  /** Persist (or refresh) a session. */
  def put(sid: String, userId: String): Unit
  /** Forget a session (e.g. an explicit logout — not wired yet). */
  def remove(sid: String): Unit
}

object SessionStore {

  /** No persistence: sessions are in-memory only (a restart logs viewers out).
    * Used when `ARCS_DB` is unset, mirroring the in-memory GameStore. */
  object Ephemeral extends SessionStore {
    def load(): Seq[(String, String)] = Nil
    def put(sid: String, userId: String): Unit = ()
    def remove(sid: String): Unit = ()
  }

  /** Sessions older than this are dropped on startup. */
  private val TtlMillis: Long = 30L * 24 * 60 * 60 * 1000 // 30 days

  /** SQLite-backed store at the given DB path (its own connection). */
  final class Sql(dbPath: String) extends SessionStore {
    Class.forName("org.sqlite.JDBC")
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)
    private val lock = new AnyRef

    locally {
      val st = conn.createStatement()
      try st.executeUpdate(
        """CREATE TABLE IF NOT EXISTS oauth_sessions (
          |  sid        TEXT    PRIMARY KEY,
          |  user_id    TEXT    NOT NULL,
          |  created_at INTEGER NOT NULL
          |)""".stripMargin)
      finally st.close()
      // Prune expired sessions so the table stays bounded.
      val ps = conn.prepareStatement("DELETE FROM oauth_sessions WHERE created_at < ?")
      try { ps.setLong(1, System.currentTimeMillis() - TtlMillis); ps.executeUpdate() } finally ps.close()
    }

    def load(): Seq[(String, String)] = lock.synchronized {
      val ps = conn.prepareStatement("SELECT sid, user_id FROM oauth_sessions")
      try {
        val rs  = ps.executeQuery()
        val buf = Vector.newBuilder[(String, String)]
        while (rs.next()) buf += (rs.getString(1) -> rs.getString(2))
        buf.result()
      } finally ps.close()
    }

    def put(sid: String, userId: String): Unit = lock.synchronized {
      val ps = conn.prepareStatement(
        "INSERT OR REPLACE INTO oauth_sessions (sid, user_id, created_at) VALUES (?, ?, ?)")
      try {
        ps.setString(1, sid); ps.setString(2, userId); ps.setLong(3, System.currentTimeMillis())
        ps.executeUpdate()
      } finally ps.close()
    }

    def remove(sid: String): Unit = lock.synchronized {
      val ps = conn.prepareStatement("DELETE FROM oauth_sessions WHERE sid = ?")
      try { ps.setString(1, sid); ps.executeUpdate() } finally ps.close()
    }
  }
}
