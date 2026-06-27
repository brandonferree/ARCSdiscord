package arcsbot.discord

/* =============================================================================
 * SessionStoreCheck — proves OAuth web sessions persist across a restart.
 *
 * A `SessionStore.Sql` writes sessions to a SQLite file; a SECOND store opened on
 * the same file (standing in for the bot after a restart) must read them back.
 * Uses a throwaway temp DB, so it touches neither `arcs.db` nor the network.
 *
 *   sbt "bot/runMain arcsbot.discord.SessionStoreCheck"
 * ===========================================================================*/
object SessionStoreCheck {
  def main(args: Array[String]): Unit = {
    val tmp = java.io.File.createTempFile("arcs-sess", ".db")
    tmp.deleteOnExit()
    val path = tmp.getAbsolutePath

    var failures = 0
    def check(name: String, ok: Boolean): Unit = {
      println(s"${if (ok) "PASS" else "FAIL"}  $name")
      if (!ok) failures += 1
    }

    // First "run": write some sessions (and overwrite one to exercise upsert).
    val first = new SessionStore.Sql(path)
    first.put("sid-1", "user-A")
    first.put("sid-2", "user-B")
    first.put("sid-1", "user-A2")

    // Simulate a restart: a fresh store on the same file reloads them.
    val afterRestart = new SessionStore.Sql(path).load().toMap
    check("sessions survive a restart", afterRestart.get("sid-2").contains("user-B"))
    check("upsert keeps the latest mapping", afterRestart.get("sid-1").contains("user-A2"))

    // Logout/remove is durable too.
    new SessionStore.Sql(path).remove("sid-2")
    val afterRemove = new SessionStore.Sql(path).load().toMap
    check("removed session stays gone", !afterRemove.contains("sid-2"))
    check("other session unaffected by remove", afterRemove.get("sid-1").contains("user-A2"))

    if (failures == 0) println("\nSessionStoreCheck: sessions persist.")
    else { Console.err.println(s"\nSessionStoreCheck: $failures FAILED."); sys.exit(1) }
  }
}
