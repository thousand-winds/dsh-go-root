package io.github.ccch1mneyyy.dsh_app.phone.shell

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The execution mutex and per-command hosts. No real su, adb, or Shizuku process is started. */
class AndroidShellControllerTest {
    @Test fun serialSecondCommandIsBusyAndDoesNotOpenAnotherHost() {
        val prefs = memory(enabled = true, concurrent = false)
        val opened = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val shell = controller(prefs)
        shell.sharedConnector = { _, _, _, _ ->
            opened.incrementAndGet()
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            ShellConnection(errorCode = "suUnavailable")
        }
        val first = Thread { shell.execute(1, "id", 1_000, { true }) { _, _ -> error("no confirm") } }
        first.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val second = shell.execute(2, "id", 1_000, { true }) { _, _ -> error("no confirm") }
        assertEquals("busy", second["errorCode"])
        assertEquals(1, opened.get())
        release.countDown()
        first.join(5_000)
    }

    @Test fun concurrentCommandsOpenSeparateHostsAndACancelClosesOnlyThatOne() {
        val prefs = memory(enabled = true, concurrent = true)
        val shell = controller(prefs)
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val closed = mutableListOf<Int>()
        val next = AtomicInteger()
        shell.hostOpener = { _, _ ->
            val id = next.incrementAndGet()
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            AndroidShellController.FreshHost(ShellConnection(errorCode = "suUnavailable")) {
                synchronized(closed) { closed += id }
            }
        }
        val results = java.util.concurrent.ConcurrentHashMap<Long, Map<String, Any?>>()
        val a = Thread { results[1] = shell.execute(1, "echo A", 1_000, { true }) { _, _ -> error("no confirm") } }
        val b = Thread { results[2] = shell.execute(2, "echo B", 1_000, { true }) { _, _ -> error("no confirm") } }
        a.start(); b.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        shell.cancel(1)
        release.countDown()
        a.join(5_000); b.join(5_000)
        assertEquals("cancelled", results[1]?.get("errorCode"))
        assertEquals("suUnavailable", results[2]?.get("errorCode"))
        assertEquals(listOf(1, 2), closed.sorted())
        assertEquals(2, next.get())
    }

    @Test fun turningConcurrencyOffDoesNotStopTheRunningCommandAndTheNextOneIsSerial() {
        val prefs = memory(enabled = true, concurrent = true)
        val shell = controller(prefs)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        shell.hostOpener = { _, _ ->
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            AndroidShellController.FreshHost(ShellConnection(errorCode = "suUnavailable")) {}
        }
        val running = Thread { shell.execute(1, "id", 1_000, { true }) { _, _ -> error("no confirm") } }
        running.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertEquals(true, shell.setAllowConcurrent(false)["ok"])
        assertFalse(shell.allowConcurrent())
        release.countDown()
        running.join(5_000)
        assertTrue(running.isAlive.not())
        val restored = controller(prefs)
        assertFalse(restored.allowConcurrent())
    }

    private fun controller(prefs: SharedPreferences) =
        AndroidShellController(PrefsContext(prefs)) { }

    private fun memory(enabled: Boolean, concurrent: Boolean) = MemoryPreferences(
        mapOf("enabled" to enabled, "backend" to "root", "allowConcurrent" to concurrent),
    )

    private class PrefsContext(private val preferences: SharedPreferences) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
    }

    private class MemoryPreferences(initial: Map<String, Any>) : SharedPreferences {
        private var memory = initial.toMutableMap()
        override fun getAll(): MutableMap<String, *> = memory.toMutableMap()
        override fun contains(key: String) = memory.containsKey(key)
        override fun getString(key: String, defValue: String?) = memory[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = memory[key] as Boolean? ?: defValue
        override fun edit() = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val changes = linkedMapOf<String, Any?>()
            private fun put(key: String, value: Any?): SharedPreferences.Editor {
                changes[key] = value
                return this
            }
            override fun putString(key: String, value: String?) = put(key, value)
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = put(key, value)
            override fun remove(key: String) = put(key, null)
            override fun clear() = this
            override fun apply() { commit() }
            override fun commit(): Boolean {
                changes.forEach { (key, value) -> if (value == null) memory.remove(key) else memory[key] = value }
                return true
            }
        }
    }
}
