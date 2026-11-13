package io.github.ccch1mneyyy.dsh_app.phone.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootReconnectTest {
    @Test fun freshInstallDoesNotStartSu() {
        assertFalse(RootReconnect.should(enabled = false, backend = "shizuku", hostAlive = false))
        assertFalse(RootReconnect.should(enabled = true, backend = "shizuku", hostAlive = false))
        assertFalse(RootReconnect.should(enabled = true, backend = "adb", hostAlive = false))
        assertFalse(RootReconnect.should(enabled = false, backend = "root", hostAlive = false))
    }

    @Test fun optedInRootReconnectsOnlyWhenTheHostIsGone() {
        assertTrue(RootReconnect.should(enabled = true, backend = "root", hostAlive = false))
        assertFalse(RootReconnect.should(enabled = true, backend = "root", hostAlive = true))
    }

    @Test fun aDeadHostIsNotReportedConnected() {
        assertEquals("notConnected", RootReconnect.reason(true, "root", hostAlive = false, failure = null))
        assertEquals("suUnavailable", RootReconnect.reason(true, "root", hostAlive = false, failure = "suUnavailable"))
        assertNull(RootReconnect.reason(true, "root", hostAlive = true, failure = "suUnavailable"))
        assertEquals("disabled", RootReconnect.reason(false, "root", hostAlive = false, failure = null))
    }
}
