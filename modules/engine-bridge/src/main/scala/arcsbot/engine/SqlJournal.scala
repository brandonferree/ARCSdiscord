package arcsbot.engine

import java.sql.{Connection, SQLException}

/** SQL-backed [[Journal]] (M2 issue #5): an append-only action log keyed by
  * `(game_id, idx)`, mirroring HRF's `good-game` Entries table and the
  * architecture-doc persistence schema.
  *
  * Concurrency is optimistic: `append(expectedIdx, line)` inserts a row at
  * `(game_id, expectedIdx)`; the primary key makes a racing append at the same
  * index fail, which we surface as [[Journal.Conflict]] (the `good-game` 409).
  *
  * The SQL is standard, so the same class works against SQLite (embedded, used
  * in tests) or Postgres — only the [[Connection]] differs. The caller owns the
  * connection lifecycle.
  */
final class SqlJournal private (val gameId: String, conn: Connection) extends Journal {

  def size: Long = {
    val ps = conn.prepareStatement("SELECT COUNT(*) FROM journal WHERE game_id = ?")
    try {
      ps.setString(1, gameId)
      val rs = ps.executeQuery()
      rs.next(); rs.getLong(1)
    } finally ps.close()
  }

  def lines: Seq[String] = {
    val ps = conn.prepareStatement("SELECT action_text FROM journal WHERE game_id = ? ORDER BY idx ASC")
    try {
      ps.setString(1, gameId)
      val rs = ps.executeQuery()
      val buf = Vector.newBuilder[String]
      while (rs.next()) buf += rs.getString(1)
      buf.result()
    } finally ps.close()
  }

  def append(expectedIdx: Long, line: String): Either[Journal.Conflict, Long] = {
    val ps = conn.prepareStatement(
      "INSERT INTO journal (game_id, idx, action_text) VALUES (?, ?, ?)")
    try {
      ps.setString(1, gameId)
      ps.setLong(2, expectedIdx)
      ps.setString(3, line)
      ps.executeUpdate()
      Right(expectedIdx + 1)
    } catch {
      // PK violation: someone already wrote this index.
      case _: SQLException if isConflict(expectedIdx) => Left(Journal.Conflict(expectedIdx, size))
    } finally ps.close()
  }

  def truncate(idx: Long): Unit = {
    val ps = conn.prepareStatement("DELETE FROM journal WHERE game_id = ? AND idx >= ?")
    try {
      ps.setString(1, gameId)
      ps.setLong(2, idx)
      ps.executeUpdate()
    } finally ps.close()
  }

  /** Distinguish a PK conflict (expected) from a genuine error (re-thrown). */
  private def isConflict(idx: Long): Boolean = {
    val ps = conn.prepareStatement("SELECT 1 FROM journal WHERE game_id = ? AND idx = ?")
    try {
      ps.setString(1, gameId); ps.setLong(2, idx)
      ps.executeQuery().next()
    } finally ps.close()
  }
}

object SqlJournal {

  /** Create the `journal` table if it doesn't exist. Idempotent. */
  def initSchema(conn: Connection): Unit = {
    val st = conn.createStatement()
    try {
      st.executeUpdate(
        """CREATE TABLE IF NOT EXISTS journal (
          |  game_id     TEXT    NOT NULL,
          |  idx         INTEGER NOT NULL,
          |  action_text TEXT    NOT NULL,
          |  PRIMARY KEY (game_id, idx)
          |)""".stripMargin)
    } finally st.close()
  }

  /** Open a journal for `gameId` on `conn`, ensuring the schema exists. */
  def apply(gameId: String, conn: Connection): SqlJournal = {
    initSchema(conn)
    new SqlJournal(gameId, conn)
  }
}
