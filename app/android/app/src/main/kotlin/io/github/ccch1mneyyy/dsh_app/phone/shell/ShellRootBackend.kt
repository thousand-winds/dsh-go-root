package io.github.ccch1mneyyy.dsh_app.phone.shell

import android.content.Context
import io.github.ccch1mneyyy.dsh_app.phone.background.Bundles
import io.github.ccch1mneyyy.dsh_app.phone.background.RootCommands
import io.github.ccch1mneyyy.dsh_app.phone.background.RootStarter
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

/**
 * Explicit `su -c` shell host. Independent of the virtual-display root channel.
 * [connect] is the only path that starts su. [attached] reuses a live host and never relaunches it.
 */
internal class ShellRootBackend(
    private val context: Context,
    private val onChanged: () -> Unit = {},
    private val spawn: (List<String>) -> Process = { ProcessBuilder(it).start() },
) {
    private val guard = Any()
    private var generation = 0L
    private var owner = 0L
    private var busy = false
    @Volatile private var process: Process? = null
    @Volatile private var channel: ShellLink? = null

    fun status(): Map<String, Any?> = mapOf("connected" to (channel?.alive() == true))

    /** A host left by [connect]. Does not start su. */
    fun attached(wanted: () -> Boolean): ShellConnection {
        val link = channel?.takeIf { it.alive() && wanted() }
        return if (link != null) ShellConnection(link) else ShellConnection(errorCode = "notConnected")
    }

    fun connect(wanted: () -> Boolean): ShellConnection {
        val ticket = synchronized(guard) {
            channel?.takeIf { it.alive() && wanted() }?.let { return ShellConnection(it) }
            if (!wanted()) return ShellConnection(errorCode = "cancelled")
            if (busy) return ShellConnection(errorCode = "busy")
            busy = true
            owner = ++generation
            owner
        }
        var started: Process? = null
        var candidate: ShellLink? = null
        var published = false
        fun current(): Boolean = wanted() && synchronized(guard) { generation == ticket }
        return try {
            if (!current()) return ShellConnection(errorCode = "cancelled")
            val info = context.applicationInfo
            val command = RootCommands.shellHost(info.sourceDir, info.splitSourceDirs?.toList().orEmpty())
            val child = try {
                spawn(command)
            } catch (_: Exception) {
                return ShellConnection(errorCode = "connectFailed")
            }
            started = child
            synchronized(guard) {
                if (!current()) return ShellConnection(errorCode = "cancelled")
                process = child
            }
            drain(child)
            awaitMagic(child, ::current)?.let { return ShellConnection(errorCode = it) }
            if (!current()) return ShellConnection(errorCode = "cancelled")
            val link = linkFor(child)
            candidate = link
            val probed = try {
                link.probe()
            } catch (_: Exception) {
                return ShellConnection(errorCode = "connectFailed")
            }
            // The host must actually be UID 0. A shell-uid su is not this backend.
            if (!probed || link.uid != 0) return ShellConnection(errorCode = "identityChanged")
            synchronized(guard) {
                if (!current() || process !== child) return ShellConnection(errorCode = "cancelled")
                channel = link
                published = true
                ShellConnection(link)
            }
        } catch (_: Exception) {
            ShellConnection(errorCode = "connectFailed")
        } finally {
            if (!published) {
                candidate?.close()
                val drop = synchronized(guard) {
                    if (process === started) process = null
                    started
                }
                destroy(drop)
            }
            synchronized(guard) { if (owner == ticket) { busy = false; owner = 0 } }
            onChanged()
        }
    }

    fun disconnect() {
        val closing = synchronized(guard) {
            ++generation
            val current = process
            process = null
            val link = channel
            channel = null
            current to link
        }
        closing.second?.close()
        destroy(closing.first)
        onChanged()
    }

    private fun linkFor(child: Process): ShellLink = ShellLink(
        "root",
        { request ->
            ShellWire.write(child.outputStream, Bundles.marshal(request))
            Bundles.unmarshal(ShellWire.read(child.inputStream) ?: throw IOException("transportLost"))
        },
        { child.isAlive },
        {
            destroy(child)
            synchronized(guard) {
                if (process === child) {
                    process = null
                    channel = null
                }
            }
        },
    )

    /** KernelSU's grant UI can block before the host prints its magic. */
    private fun awaitMagic(child: Process, current: () -> Boolean): String? {
        val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "dsh-shell-root-sync").apply { isDaemon = true }
        }
        val task = worker.submit<Boolean> {
            ShellWire.sync(child.inputStream)
            true
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RootStarter.START_TIMEOUT_MS)
        try {
            while (true) {
                if (!current()) return "cancelled"
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return "timeout"
                try {
                    task.get(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(200)), TimeUnit.NANOSECONDS)
                    return null
                } catch (_: TimeoutException) {
                }
            }
        } catch (_: Exception) {
            return if (current()) "connectFailed" else "cancelled"
        } finally {
            task.cancel(true)
            worker.shutdownNow()
        }
    }

    private fun drain(child: Process) {
        thread(isDaemon = true, name = "dsh-shell-root-stderr") {
            runCatching {
                val buffer = ByteArray(4096)
                while (child.errorStream.read(buffer) >= 0) { }
            }
        }
    }

    private fun destroy(child: Process?) {
        if (child == null) return
        thread(isDaemon = true, name = "dsh-shell-root-close") {
            runCatching { child.destroyForcibly() }
            runCatching { child.outputStream.close() }
            runCatching { child.inputStream.close() }
            runCatching { child.errorStream.close() }
        }
    }
}
