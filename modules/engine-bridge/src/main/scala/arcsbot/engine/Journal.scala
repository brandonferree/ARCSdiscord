package arcsbot.engine

/** The append-only action log that IS the game.
  *
  * A game's entire state is the ordered list of serialized HRF action lines.
  * Replay them through the engine -> exact state. This mirrors HRF's own
  * `good-game` journal (GoodGame.scala: Journals/Entries tables) and the
  * `read`/`append` HTTP contract, but owned by this service.
  *
  * Invariants:
  *  - lines are 1:1 with HRF `Serialize.write(action)` output for ExternalActions
  *    (and the OracleActions the bridge resolves for dice/shuffle/random).
  *  - append is index-checked: appending at an already-used index must fail
  *    (optimistic concurrency; see GoodGame.scala:292 returning 409 Conflict).
  *  - truncate(idx) implements undo: drop everything at/after idx, then replay.
  */
trait Journal {

  /** Stable id of this game/table. */
  def gameId: String

  /** Number of action lines currently stored. */
  def size: Long

  /** All action lines in order (idx 0..size-1). */
  def lines: Seq[String]

  /** Append `line` at `expectedIdx`. Returns the new size, or fails the effect
    * if `expectedIdx != size` (someone else appended first). */
  def append(expectedIdx: Long, line: String): Either[Journal.Conflict, Long]

  /** Undo support: drop every line with index >= idx. */
  def truncate(idx: Long): Unit
}

object Journal {
  final case class Conflict(expectedIdx: Long, actualSize: Long)

  /** A trivial in-memory journal for tests / the M2 harness. Production should
    * back this with SQL (see docs/ARCHITECTURE.md -> Persistence). */
  final class InMemory(val gameId: String) extends Journal {
    private val buf = scala.collection.mutable.ArrayBuffer.empty[String]
    def size: Long = buf.length.toLong
    def lines: Seq[String] = buf.toVector
    def append(expectedIdx: Long, line: String): Either[Conflict, Long] =
      synchronized {
        if (expectedIdx != buf.length.toLong) Left(Conflict(expectedIdx, buf.length.toLong))
        else { buf += line; Right(buf.length.toLong) }
      }
    def truncate(idx: Long): Unit = synchronized {
      if (idx >= 0 && idx < buf.length) buf.remove(idx.toInt, buf.length - idx.toInt)
    }
  }
}
