package io.github.arpit92k.fortuna.core.ports

/**
 * The App lifecycle port: tells the core when the app leaves the foreground
 * and comes back. On Android it is `ProcessLifecycleOwner` (step 14).
 *
 * Closing the app or restarting the phone needs no message: the data key
 * lives only in memory, so the app is locked when it starts again.
 */
interface AppLifecycle {
    /** Starts telling [listener] about the app's comings and goings. */
    fun observe(listener: LifecycleListener)
}

interface LifecycleListener {
    fun onLeftForeground()

    fun onReturnedToForeground()

    /**
     * Locks the app if it has been in the background past its grace period.
     * The platform calls this from a timer while the app is away, so the data
     * key leaves memory without waiting for the user to come back.
     */
    fun checkAutoLock()
}
