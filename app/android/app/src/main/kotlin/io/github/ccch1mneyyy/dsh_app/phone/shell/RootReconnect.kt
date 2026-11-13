package io.github.ccch1mneyyy.dsh_app.phone.shell

/**
 * Whether a missing Root host may be started again. Fresh installs stay off:
 * Android Shell must already be enabled on the Root backend.
 */
internal object RootReconnect {
    fun should(enabled: Boolean, backend: String, hostAlive: Boolean): Boolean =
        enabled && backend == "root" && !hostAlive

    /** A dead host is never "connected". [failure] is the last su start error, if any. */
    fun reason(enabled: Boolean, backend: String, hostAlive: Boolean, failure: String?): String? = when {
        !enabled -> "disabled"
        hostAlive -> null
        backend == "root" -> failure ?: "notConnected"
        else -> "notConnected"
    }
}
