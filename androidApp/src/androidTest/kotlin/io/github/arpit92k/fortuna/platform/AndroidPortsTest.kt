package io.github.arpit92k.fortuna.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.arpit92k.fortuna.FortunaApplication
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.keys.calibratePinIterations
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.ports.finishInterruptedReplace
import io.github.arpit92k.fortuna.core.ports.replaceSafely
import io.github.arpit92k.fortuna.core.session.Session
import io.github.arpit92k.fortuna.core.session.SessionState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidPortsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // A folder and a key of the tests' own, beside the app's real ones.
    private val folder = "fortuna-test"
    private val alias = "fortuna-test-session-key"

    @Before
    @After
    fun removeTestData() {
        File(context.noBackupFilesDir, folder).deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
    }

    // ---- Private files ----

    // From the plan: a file replaced through the adapter is complete after a restart.
    @Test
    fun aReplacedFileIsCompleteAfterARestart() {
        val old = Random.nextBytes(1_000)
        val new = Random.nextBytes(1_000_000)
        androidPrivateFiles(context, folder).replaceSafely("store", old)
        androidPrivateFiles(context, folder).replaceSafely("store", new)
        assertContentEquals(new, androidPrivateFiles(context, folder).read("store"))
    }

    @Test
    fun anInterruptedReplaceIsFinishedOnStart() {
        val files = androidPrivateFiles(context, folder)
        val new = Random.nextBytes(10_000)
        files.write("store", Random.nextBytes(10_000))
        files.write("store.new", new)
        files.write("store.partial", new.copyOf(3_000))

        val afterRestart = androidPrivateFiles(context, folder)
        afterRestart.finishInterruptedReplace("store")
        assertContentEquals(new, afterRestart.read("store"))
        assertFalse(afterRestart.exists("store.partial"))
        assertFalse(afterRestart.exists("store.new"))
    }

    @Test
    fun filesLiveInTheNoBackupFolder() {
        androidPrivateFiles(context, folder).write("store", byteArrayOf(1))
        assertTrue(File(context.noBackupFilesDir, "$folder/store").isFile)
    }

    // ---- Clock ----

    @Test
    fun theClockReportsTheDateAndTheStartUp() {
        val clock = AndroidClock(context)
        val today = java.time.LocalDate.now()
        assertEquals(today.toString(), clock.today().toString())
        assertTrue(clock.startUp() >= 0, "the phone keeps a boot count")
        val before = clock.sinceStartMillis()
        Thread.sleep(20)
        assertTrue(clock.sinceStartMillis() > before)
    }

    // ---- The core on the phone's adapters ----

    @Test
    fun theCoreWorksOnThePhonesAdapters() {
        fun session() = Session(
            androidPrivateFiles(context, folder),
            PlatformCrypto,
            KeystoreDeviceKey(alias),
            AndroidClock(context),
            pinIterations = { 1_000 },
            phraseIterations = 1_000,
        ).also { it.start() }

        val first = session()
        assertEquals(SessionState.NoData, first.state.value)
        assertIs<SetupResult.Ready>(first.beginSetup("246810"))
        first.finishSetup(Currency.forCode("GBP")!!)
        assertEquals(SessionState.Unlocked, first.state.value)

        // The app is closed and opened again: locked, and the device key still opens it.
        val again = session()
        assertIs<SessionState.Locked>(again.state.value)
        assertIs<UnlockResult.WrongPin>(again.unlock("111111"))
        assertIs<UnlockResult.Success>(again.unlock("246810"))
        assertEquals("GBP", again.dataset.value!!.baseCurrency!!.code)
    }

    @Test
    fun pinRoundsAreMeasuredOnThePhone() {
        val rounds = calibratePinIterations(PlatformCrypto, AndroidClock(context))
        assertTrue(rounds in 100_000..10_000_000, "$rounds rounds")
    }

    // From the plan: the app starts and creates the core. On a phone without
    // Fortuna data, as in the Device tests workflow, it reports no data.
    @Test
    fun theAppCreatesTheCoreOnStart() {
        val app = context.applicationContext as FortunaApplication
        val state = app.core.state.value
        assertTrue(state == SessionState.NoData || state is SessionState.Locked, "the core started: $state")
    }
}
