package io.github.arpit92k.fortuna

import android.app.Application
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.session.Session
import io.github.arpit92k.fortuna.platform.AndroidClock
import io.github.arpit92k.fortuna.platform.KeystoreDeviceKey
import io.github.arpit92k.fortuna.platform.ProcessAppLifecycle
import io.github.arpit92k.fortuna.platform.androidPrivateFiles
import io.github.arpit92k.fortuna.platform.deviceInfo
import io.github.arpit92k.fortuna.report.CrashStore
import io.github.arpit92k.fortuna.report.recordCrashes

/** Creates the core once, with the Android adapters, and hands it to the interface. */
class FortunaApplication : Application() {
    lateinit var core: Core
        private set

    /** Where crashes are recorded, to be offered for reporting the next time the app opens. */
    lateinit var crashes: CrashStore
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so a crash while starting up is recorded too.
        crashes = CrashStore(java.io.File(filesDir, "crashes"))
        recordCrashes(crashes, deviceInfo().appVersion)
        val session = Session(
            files = androidPrivateFiles(this),
            crypto = PlatformCrypto,
            deviceKey = KeystoreDeviceKey(),
            clock = AndroidClock(this),
        )
        core = Core(session)
        session.start(
            ProcessAppLifecycle(
                checkAfter = listOf(Session.gracePeriod(enteringValue = false), Session.gracePeriod(enteringValue = true)),
                // Lifecycle events arrive on the main thread; the session takes them on its own.
                dispatch = { event -> core.post { event() } },
            ),
        )
    }
}
