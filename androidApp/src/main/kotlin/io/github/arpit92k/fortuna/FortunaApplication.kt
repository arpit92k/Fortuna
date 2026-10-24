package io.github.arpit92k.fortuna

import android.app.Application
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.session.Session
import io.github.arpit92k.fortuna.platform.AndroidClock
import io.github.arpit92k.fortuna.platform.KeystoreDeviceKey
import io.github.arpit92k.fortuna.platform.ProcessAppLifecycle
import io.github.arpit92k.fortuna.platform.androidPrivateFiles

/** Creates the core once, with the Android adapters, and hands it to the interface. */
class FortunaApplication : Application() {
    lateinit var session: Session
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(
            files = androidPrivateFiles(this),
            crypto = PlatformCrypto,
            deviceKey = KeystoreDeviceKey(),
            clock = AndroidClock(this),
        )
        session.start(
            ProcessAppLifecycle(
                checkAfter = listOf(Session.gracePeriod(enteringValue = false), Session.gracePeriod(enteringValue = true)),
            ),
        )
    }
}
