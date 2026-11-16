package io.github.ccch1mneyyy.dsh_app.phone.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellRunsTest {
    private val runs = ShellRuns()

    @Test fun defaultSerialSecondCommandIsBusyUntilTheFirstEnds() {
        val first = checkNotNull(runs.tryBegin(1, concurrent = false))
        assertNull(runs.tryBegin(2, concurrent = false))
        runs.end(first)
        assertNotNull(runs.tryBegin(2, concurrent = false))
    }

    @Test fun concurrentCommandsEachGetASlotAndDoNotBlockEachOther() {
        val first = checkNotNull(runs.tryBegin(1, concurrent = true))
        val second = checkNotNull(runs.tryBegin(2, concurrent = true))
        assertEquals(setOf(first, second), runs.snapshot().toSet())
        assertTrue(runs.snapshot().none { it.serial })
    }

    @Test fun cancelListsOnlyThatOwnersSlots() {
        val a1 = checkNotNull(runs.tryBegin(1, concurrent = true))
        val a2 = checkNotNull(runs.tryBegin(1, concurrent = true))
        val b = checkNotNull(runs.tryBegin(2, concurrent = true))
        assertEquals(setOf(a1, a2), runs.of(1).toSet())
        runs.end(a1)
        runs.end(a2)
        assertEquals(listOf(b), runs.snapshot())
    }

    @Test fun failedStartReleasesTheSerialSlot() {
        val slot = checkNotNull(runs.tryBegin(1, concurrent = false))
        runs.end(slot)
        assertNotNull(runs.tryBegin(2, concurrent = false))
    }

    @Test fun turningConcurrencyOffLeavesRunningSlotsAndTheNextCommandIsSerial() {
        val running = checkNotNull(runs.tryBegin(1, concurrent = true))
        val next = checkNotNull(runs.tryBegin(2, concurrent = false))
        assertTrue(next.serial)
        assertNull("the serial slot is now taken", runs.tryBegin(3, concurrent = false))
        assertEquals(setOf(running, next), runs.snapshot().toSet())
    }
}
