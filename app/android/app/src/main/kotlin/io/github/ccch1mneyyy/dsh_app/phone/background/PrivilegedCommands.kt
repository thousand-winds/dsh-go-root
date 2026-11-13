package io.github.ccch1mneyyy.dsh_app.phone.background

/** The shell commands the ADB and root paths run: fixed text plus the APK paths ApplicationInfo gives. */
object PrivilegedCommands {
    fun appProcess(
        sourceDir: String,
        splitSourceDirs: List<String> = emptyList(),
        mainClass: String = PrivilegedDisplayMain::class.java.name,
    ): String {
        val paths = listOf(sourceDir) + splitSourceDirs
        require(paths.all { it.startsWith('/') && '\u0000' !in it && ':' !in it })
        require(mainClass.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")))
        return "CLASSPATH=${quote(paths.joinToString(":"))} exec /system/bin/app_process /system/bin $mainClass"
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

/** `su -c` takes exactly one shell command on Magisk, KernelSU and APatch. */
internal object RootCommands {
    fun appProcess(sourceDir: String, splitSourceDirs: List<String> = emptyList()): List<String> =
        listOf("su", "-c", PrivilegedCommands.appProcess(sourceDir, splitSourceDirs))

    /** Android Shell host. Not [appProcess], which stays on the virtual-display entry. */
    fun shellHost(sourceDir: String, splitSourceDirs: List<String> = emptyList()): List<String> =
        listOf("su", "-c", PrivilegedCommands.appProcess(
            sourceDir,
            splitSourceDirs,
            io.github.ccch1mneyyy.dsh_app.phone.shell.ShellRootMain::class.java.name,
        ))
}
