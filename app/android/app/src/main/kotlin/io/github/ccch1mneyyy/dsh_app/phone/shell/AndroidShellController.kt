package io.github.ccch1mneyyy.dsh_app.phone.shell

import android.content.Context
import io.github.ccch1mneyyy.dsh_app.phone.Decision
import io.github.ccch1mneyyy.dsh_app.phone.PhoneAndroidShell
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in shell capability, independent of screen-control configuration and confirmation modes. */
class AndroidShellController(context: Context, private val onChanged: () -> Unit = {}) {
    private data class Config(val enabled: Boolean, val backend: String, val rootSkip: Boolean, val allowConcurrent: Boolean)
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("android-shell-v1", Context.MODE_PRIVATE)
    private val guard = Any()
    private var enabled = preferences.getBoolean("enabled", false)
    private var backend = preferences.getString("backend", "shizuku").takeIf { it == "adb" || it == "shizuku" || it == "root" } ?: "shizuku"
    private var rootSkip = preferences.getBoolean("rootSkipConfirmation", false)
    private var concurrentCommands = preferences.getBoolean("allowConcurrent", false)
    private var generation = 0L
    private var setupBusy = false
    private var active: Lease? = null
    private val runs = ShellRuns()
    private val isolated = mutableSetOf<Lease>()
    /** Test seam. Production commands open a new su/adb/shizuku host per parallel run. */
    internal var hostOpener: ((String, () -> Boolean) -> FreshHost)? = null
    /** Test seam for the shared serial host. Production uses the fields below. */
    internal var sharedConnector: ((String, Int?, () -> Boolean, Boolean) -> ShellConnection)? = null
    @Volatile private var readyLink: ShellLink? = null
    @Volatile private var rootFailure: String? = null
    private val adb by lazy { ShellAdbBackend(context, onChanged) }
    private val shizuku by lazy { ShellShizukuBackend(context, onChanged) }
    private val root by lazy { ShellRootBackend(context, onChanged) }

    private class Lease(
        val ownerId: Long,
        val generation: Long,
        val backend: String,
        val slot: ShellRuns.Slot,
    ) {
        val id = UUID.randomUUID().toString()
        val isolated: Boolean get() = !slot.serial
        @Volatile var cancelled = false
        @Volatile var link: ShellLink? = null
        var sent = false
        var closeHost: (() -> Unit)? = null
    }

    /** One parallel command's own host. Closing it does not touch any other command. */
    internal class FreshHost(val connection: ShellConnection, val close: () -> Unit)

    fun status(): Map<String, Any?> {
        discardDeadRoot()
        val config = synchronized(guard) { Config(enabled, backend, rootSkip, concurrentCommands) }
        val shizukuStatus = shizuku.status()
        val adbStatus = adb.status()
        val failure = rootFailure
            val link = readyLink?.takeIf { it.backend == config.backend && it.alive() &&
            (config.backend != "shizuku" || shizukuStatus["granted"] == true) &&
            (config.backend != "root" || it.uid == 0) }
        val reason = when {
            !config.enabled -> "disabled"
            link != null -> null
            config.backend == "root" -> failure ?: "notConnected"
            config.backend == "shizuku" && shizukuStatus["installed"] != true -> "shizukuMissing"
            config.backend == "shizuku" && shizukuStatus["running"] != true -> "shizukuStopped"
            config.backend == "shizuku" && shizukuStatus["granted"] != true -> "permissionRequired"
            config.backend == "adb" && adbStatus["paired"] != true -> "adbNotPaired"
            else -> "notConnected"
        }
        return mapOf(
            "v" to 1, "enabled" to config.enabled, "backend" to config.backend,
            "rootSkipConfirmation" to config.rootSkip,
            "allowConcurrent" to config.allowConcurrent,
            "ready" to (config.enabled && link != null), "uid" to link?.uid,
            "reasonCode" to reason, "shizuku" to shizukuStatus, "adb" to adbStatus, "root" to root.status(),
            "limits" to mapOf("defaultTimeoutMs" to ShellProtocol.DEFAULT_TIMEOUT_MS,
                "maxTimeoutMs" to ShellProtocol.MAX_TIMEOUT_MS, "maxOutputBytes" to ShellProtocol.MAX_OUTPUT_BYTES,
                "maxCommandBytes" to ShellProtocol.MAX_COMMAND_BYTES),
        )
    }

    fun configure(enabled: Boolean, backend: String): Map<String, Any?> {
        if (backend != "shizuku" && backend != "adb" && backend != "root") return mapOf("ok" to false, "errorCode" to "invalidArguments")
        val dropped = mutableListOf<Lease>()
        val changed = synchronized(guard) {
            if (this.enabled == enabled && this.backend == backend) false else {
                this.enabled = enabled; this.backend = backend; ++generation
                active?.cancelled = true
                isolated.forEach { it.cancelled = true }
                dropped += isolated
                readyLink = null
                preferences.edit().putBoolean("enabled", enabled).putString("backend", backend).apply()
                true
            }
        }
        if (changed) {
            dropped.forEach { it.closeHost?.invoke() }
            adb.disconnect(); shizuku.disconnect(); root.disconnect(); onChanged()
        }
        return status() + mapOf("ok" to true, "errorCode" to null)
    }

    /** Persisted Root-only waiver. Read on each command; it does not start su or touch ADB/Shizuku. */
    fun rootSkipConfirmation(): Boolean = synchronized(guard) { rootSkip }

    fun setRootSkipConfirmation(enabled: Boolean): Map<String, Any?> {
        val changed = synchronized(guard) {
            if (rootSkip == enabled) false else {
                rootSkip = enabled
                preferences.edit().putBoolean("rootSkipConfirmation", enabled).apply()
                true
            }
        }
        if (changed) onChanged()
        return status() + mapOf("ok" to true, "errorCode" to null)
    }

    /** Persisted. Turning this off does not stop commands that are already running. */
    fun allowConcurrent(): Boolean = synchronized(guard) { concurrentCommands }

    fun setAllowConcurrent(enabled: Boolean): Map<String, Any?> {
        val changed = synchronized(guard) {
            if (concurrentCommands == enabled) false else {
                concurrentCommands = enabled
                preferences.edit().putBoolean("allowConcurrent", enabled).apply()
                true
            }
        }
        if (changed) onChanged()
        return mapOf("ok" to true, "errorCode" to null, "allowConcurrent" to enabled)
    }

    /**
     * Starts the Root host only when Android Shell is already enabled on Root and the host is gone.
     * A fresh install (enabled off, or another backend) does not call su.
     */
    fun ensureRoot(): Map<String, Any?> {
        discardDeadRoot()
        val optedIn = synchronized(guard) {
            enabled && backend == "root" && readyLink?.alive() != true && !setupBusy && active == null
        }
        if (optedIn && RootReconnect.should(enabled = true, backend = "root", hostAlive = false)) connect()
        return status()
    }

    fun requestShizukuPermission(): Boolean = shizuku.requestPermission()
    fun startAdbPairing(): Map<String, Any?> = adb.startPairing()

    fun pairAdb(code: String, port: Int?): Map<String, Any?> {
        val ticket = synchronized(guard) {
            if (setupBusy || active != null) return mapOf("ok" to false, "errorCode" to "busy")
            setupBusy = true; generation
        }
        return try { adb.pair(code, port) { synchronized(guard) { generation == ticket } } }
        finally { synchronized(guard) { setupBusy = false }; onChanged() }
    }

    /** Explicit setup only. status/configure never start a privileged process. */
    fun connect(port: Int? = null): Map<String, Any?> {
        val selection = synchronized(guard) {
            if (!enabled) return mapOf("ok" to false, "errorCode" to "disabled")
            if (setupBusy || active != null) return mapOf("ok" to false, "errorCode" to "busy")
            setupBusy = true; generation to backend
        }
        val wanted = { synchronized(guard) { enabled && generation == selection.first && backend == selection.second } }
        return try {
            val connection = connection(selection.second, port, wanted, startRoot = true)
            val link = connection.link
            if (link == null || !wanted()) {
                if (selection.second == "root") rootFailure = connection.errorCode ?: "suUnavailable"
                link?.close()
                return mapOf("ok" to false, "errorCode" to (connection.errorCode ?: "suUnavailable"))
            }
            if (selection.second == "root") rootFailure = null
            readyLink = link
            status() + mapOf("ok" to true, "errorCode" to null)
        } finally { synchronized(guard) { setupBusy = false }; onChanged() }
    }

    fun execute(
        ownerId: Long,
        command: String,
        timeoutMs: Long,
        stillWanted: () -> Boolean,
        confirm: (backend: String, uid: Int) -> Decision,
    ): Map<String, Any?> {
        ShellProtocol.commandError(command)?.let { return ShellProtocol.failure(it) }
        ShellProtocol.timeoutError(timeoutMs)?.let { return ShellProtocol.failure(it) }
        val lease = synchronized(guard) {
            if (!enabled) return ShellProtocol.failure("disabled")
            val slot = runs.tryBegin(ownerId, concurrent = concurrentCommands) ?: return ShellProtocol.failure("busy")
            if (!slot.serial) {
                Lease(ownerId, generation, backend, slot).also { isolated += it }
            } else if (setupBusy || active != null) {
                runs.end(slot)
                return ShellProtocol.failure("busy")
            } else {
                Lease(ownerId, generation, backend, slot).also { active = it }
            }
        }
        fun wanted(): Boolean = runCatching { stillWanted() }.getOrDefault(false) && synchronized(guard) {
            !lease.cancelled && enabled && generation == lease.generation && backend == lease.backend &&
                if (lease.isolated) lease in isolated else active === lease
        }
        var restoreInterrupt = false
        try {
            if (!wanted()) return result(lease, ShellProtocol.failure("cancelled"))
            val fresh = if (lease.isolated) openFresh(lease.backend, ::wanted) else null
            if (fresh != null) lease.closeHost = fresh.close
            if (!wanted()) return result(lease, ShellProtocol.failure("cancelled"))
            val connection = fresh?.connection ?: connection(lease.backend, null, ::wanted, startRoot = lease.backend == "root")
            val link = connection.link ?: run {
                if (lease.backend == "root" && !lease.isolated) rootFailure = connection.errorCode ?: "suUnavailable"
                return result(lease, ShellProtocol.failure(connection.errorCode ?: "suUnavailable"))
            }
            if (lease.backend == "root") rootFailure = null
            lease.link = link
            if (!wanted()) return result(lease, ShellProtocol.failure("cancelled"))
            // Read real UID on this very channel before every confirmation, not from provider status.
            if (!link.probe()) { link.close(); return result(lease, ShellProtocol.failure("identityChanged")) }
            if (!lease.isolated) readyLink = link
            val decision = confirm(lease.backend, link.uid)
            if (!wanted()) return result(lease, ShellProtocol.failure("cancelled"))
            PhoneAndroidShell.confirmationError(decision)?.let { return result(lease, ShellProtocol.failure(it)) }
            var response = link.call(mapOf("op" to "start", "jobId" to lease.id,
                "command" to command, "timeoutMs" to timeoutMs, "uid" to link.uid)) {
                wanted() && synchronized(guard) {
                    val held = if (lease.isolated) lease in isolated else active === lease
                    if (!held || lease.cancelled || generation != lease.generation || !enabled) false
                    else { lease.sent = true; true }
                }
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs + 2_000)
            var stopSent = false
            while (response["jobId"] == lease.id && response["done"] != true) {
                if (!wanted() && !stopSent) {
                    link.call(mapOf("op" to "cancel", "jobId" to lease.id))
                    stopSent = true
                }
                if (System.nanoTime() > deadline) {
                    runCatching { link.call(mapOf("op" to "cancel", "jobId" to lease.id)) }
                    link.close()
                    return result(lease, ShellProtocol.failure("cleanupFailed", started = true))
                }
                try { Thread.sleep(50) } catch (_: InterruptedException) {
                    restoreInterrupt = true; lease.cancelled = true
                }
                response = link.call(mapOf("op" to "poll", "jobId" to lease.id))
            }
            if (response["errorCode"] == "outputIncomplete") link.close()
            return result(lease, response)
        } catch (_: Exception) {
            val mayHaveStarted = synchronized(guard) { lease.cancelled = true; lease.sent }
            lease.link?.close()
            return result(lease, ShellProtocol.failure("transportLost", started = mayHaveStarted))
        } finally {
            synchronized(guard) {
                runs.end(lease.slot)
                if (lease.isolated) isolated -= lease else if (active === lease) active = null
            }
            if (lease.isolated) lease.closeHost?.invoke()
            if (restoreInterrupt) Thread.currentThread().interrupt()
            onChanged()
        }
    }

    private fun openFresh(backend: String, wanted: () -> Boolean): FreshHost {
        hostOpener?.let { return it(backend, wanted) }
        val host: Any = when (backend) {
            "adb" -> ShellAdbBackend(context) {}
            "shizuku" -> ShellShizukuBackend(context) {}
            "root" -> ShellRootBackend(context, {})
            else -> return FreshHost(ShellConnection(errorCode = "invalidArguments")) {}
        }
        val connection = when (host) {
            is ShellAdbBackend -> host.connect(null, wanted)
            is ShellShizukuBackend -> host.connect(wanted)
            is ShellRootBackend -> host.connect(wanted)
            else -> ShellConnection(errorCode = "invalidArguments")
        }
        val close = {
            when (host) {
                is ShellAdbBackend -> host.disconnect()
                is ShellShizukuBackend -> host.disconnect()
                is ShellRootBackend -> host.disconnect()
            }
        }
        return FreshHost(connection, close)
    }

    private fun result(lease: Lease, values: Map<String, Any?>): Map<String, Any?> =
        values + mapOf("backend" to lease.backend, "uid" to lease.link?.uid?.takeIf { it >= 0 })

    private fun connection(backend: String, port: Int?, wanted: () -> Boolean, startRoot: Boolean): ShellConnection =
        sharedConnector?.let { return it(backend, port, wanted, startRoot) } ?: when (backend) {
            // Each backend stays on its own transport. Root never falls through to ADB or Shizuku.
            "adb" -> adb.connect(port, wanted)
            "shizuku" -> shizuku.connect(wanted)
            "root" -> if (startRoot) root.connect(wanted) else root.attached(wanted)
            else -> ShellConnection(errorCode = "invalidArguments")
        }

    fun cancel(ownerId: Long) {
        val doomed = synchronized(guard) {
            buildList {
                active?.takeIf { it.ownerId == ownerId }?.also { it.cancelled = true; add(it) }
                isolated.filter { it.ownerId == ownerId }.forEach { it.cancelled = true; add(it) }
            }
        }
        doomed.forEach(::stopLease)
    }

    fun cancelAll() {
        val doomed = synchronized(guard) {
            ++generation
            readyLink = null
            buildList {
                active?.also { it.cancelled = true; add(it) }
                isolated.forEach { it.cancelled = true; add(it) }
            }
        }
        doomed.forEach(::stopLease)
        adb.disconnect(); shizuku.disconnect(); root.disconnect(); onChanged()
    }

    private fun stopLease(lease: Lease) {
        if (lease.link != null) cancelRemote(lease)
        else if (lease.isolated) lease.closeHost?.invoke()
        else when (lease.backend) {
            "adb" -> adb.disconnect()
            "shizuku" -> shizuku.disconnect()
            "root" -> root.disconnect()
        }
    }

    /** A dead su process is not a connection. Drop it before any status or command reads it. */
    private fun discardDeadRoot() {
        val dead = synchronized(guard) {
            readyLink?.takeIf { it.backend == "root" && !it.alive() }?.also { readyLink = null }
        }
        dead?.close()
    }

    private fun cancelRemote(lease: Lease) {
        val link = lease.link ?: return
        Thread({
            runCatching { link.call(mapOf("op" to "cancel", "jobId" to lease.id)) }
                .onFailure { link.close() }
        }, "dsh-shell-cancel").apply { isDaemon = true; start() }
    }
}
