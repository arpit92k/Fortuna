package io.github.arpit92k.fortuna.platform

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.arpit92k.fortuna.core.ports.AppLifecycle
import io.github.arpit92k.fortuna.core.ports.LifecycleListener
import kotlin.time.Duration

/**
 * The App lifecycle port on `ProcessLifecycleOwner`, which follows the whole
 * app rather than one screen, so moving between screens is not leaving.
 *
 * While the app is away it asks the core to check for auto-lock after each
 * of [checkAfter], so the data key leaves memory on time. The timer pauses
 * while the phone is in deep sleep and may then run late; the check made on
 * return covers that, since it uses the clock that keeps counting.
 *
 * Call [observe] on the main thread.
 */
class ProcessAppLifecycle(private val checkAfter: List<Duration>) : AppLifecycle {
    private val handler = Handler(Looper.getMainLooper())

    override fun observe(listener: LifecycleListener) {
        val check = Runnable { listener.checkAutoLock() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    listener.onLeftForeground()
                    for (delay in checkAfter) handler.postDelayed(check, delay.inWholeMilliseconds)
                }

                override fun onStart(owner: LifecycleOwner) {
                    handler.removeCallbacks(check)
                    listener.onReturnedToForeground()
                }
            },
        )
    }
}
