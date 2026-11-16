package io.github.ccch1mneyyy.dsh_app.phone.shell

/**
 * Execution slots for Android Shell. Serial mode keeps one slot and reports busy.
 * Concurrent mode adds a slot per command and never queues or caps them.
 * Turning concurrency off does not drop slots that are already running.
 */
internal class ShellRuns {
    class Slot(val ownerId: Long, val serial: Boolean)

    private val live = LinkedHashSet<Slot>()

    /** Null means the serial slot is taken. Concurrent calls always get a slot. */
    fun tryBegin(ownerId: Long, concurrent: Boolean): Slot? = synchronized(live) {
        if (!concurrent && live.any { it.serial }) return null
        Slot(ownerId, serial = !concurrent).also { live += it }
    }

    fun end(slot: Slot) = synchronized(live) { live.remove(slot) }

    fun of(ownerId: Long): List<Slot> = synchronized(live) { live.filter { it.ownerId == ownerId } }

    fun snapshot(): List<Slot> = synchronized(live) { live.toList() }
}
