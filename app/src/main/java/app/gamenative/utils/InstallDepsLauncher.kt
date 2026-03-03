package app.gamenative.utils

/**
 * Abstraction for the guest launcher used during launch steps and game start.
 */
interface InstallDepsLauncher {
    fun setGuestExecutable(executable: String)
    fun setPreUnpack(block: (() -> Unit)?)
    fun execShellCommand(command: String)
    fun setTerminationCallback(callback: ((Int) -> Unit)?)
    fun start()
}
