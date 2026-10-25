package io.github.arpit92k.fortuna

import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.ports.Clock
import io.github.arpit92k.fortuna.core.ports.DeviceKey
import io.github.arpit92k.fortuna.core.ports.DeviceKeyException
import io.github.arpit92k.fortuna.core.ports.OkioPrivateFiles
import io.github.arpit92k.fortuna.core.session.Session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random
import kotlin.time.Duration

// Stand-ins for the phone's parts, so view model tests run the real core on
// the JVM. The core's own stand-ins live in its test source set, out of reach.

/** A device key that only unwraps what it wrapped itself. */
class FakeDeviceKey : DeviceKey {
    private val id = Random.nextBytes(8)

    override fun wrap(secret: ByteArray): ByteArray = id + secret

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (wrapped.size < id.size || !wrapped.copyOf(id.size).contentEquals(id)) {
            throw DeviceKeyException("Not wrapped by this key")
        }
        return wrapped.copyOfRange(id.size, wrapped.size)
    }
}

/** A clock moved by hand. */
class FakeClock : Clock {
    var date = LocalDate(2026, 10, 24)
    var millis = 5_000L
    var startUpCount = 1L

    override fun today() = date

    override fun sinceStartMillis() = millis

    override fun startUp() = startUpCount

    fun advance(by: Duration) {
        millis += by.inWholeMilliseconds
    }
}

/** One stand-in phone: its own disk, key and clock. */
class TestPhone {
    val files = OkioPrivateFiles(FakeFileSystem(), "/data".toPath())
    val deviceKey = FakeDeviceKey()
    val clock = FakeClock()

    /** The app starting: a new session on this phone's parts. */
    fun session(): Session =
        Session(files, PlatformCrypto, deviceKey, clock, pinIterations = { 1_000 }, phraseIterations = 1_000).also { it.start() }

    /** The app starting, as the interface sees it. */
    fun start(dispatcher: CoroutineDispatcher): Core = Core(session(), dispatcher)

    /** Set up with a pounds base and unlocked, as just after setup. */
    fun unlocked(dispatcher: CoroutineDispatcher): Core {
        val session = session()
        val setup = (session.beginSetup("246810") as io.github.arpit92k.fortuna.core.keys.SetupResult.Ready)
        check(setup.setup.phrase.words.size == 12)
        session.finishSetup(io.github.arpit92k.fortuna.core.money.Currency.forCode("GBP")!!)
        return Core(session, dispatcher)
    }
}

/** Files the user picks, held in memory and named by their address. */
class FakeDocumentFiles : io.github.arpit92k.fortuna.platform.DocumentFiles {
    val saved = mutableMapOf<String, ByteArray>()

    /** Addresses that fail, as a place that cannot be written or a file that cannot be read. */
    val failing = mutableSetOf<String>()

    override fun write(location: String, bytes: ByteArray) {
        if (location in failing) throw java.io.IOException("Cannot write $location")
        saved[location] = bytes.copyOf()
    }

    override fun read(location: String, maxBytes: Int): ByteArray {
        if (location in failing) throw java.io.IOException("Cannot read $location")
        val bytes = saved[location] ?: throw java.io.FileNotFoundException(location)
        if (bytes.size > maxBytes) throw io.github.arpit92k.fortuna.platform.FileTooLargeException()
        return bytes.copyOf()
    }
}

/**
 * Sets this phone up with [pin] and a pounds base, leaving it locked as on the
 * next start, and returns the recovery phrase's words.
 */
fun TestPhone.setUpLocked(pin: String = "246810"): List<String> {
    val session = session()
    val setup = session.beginSetup(pin) as io.github.arpit92k.fortuna.core.keys.SetupResult.Ready
    val words = setup.setup.phrase.words
    session.finishSetup(io.github.arpit92k.fortuna.core.money.Currency.forCode("GBP")!!)
    return words
}

/** A phrase that is valid, but not any phone's: the first word eleven times, then "about". */
val someoneElsesPhrase: List<String> = List(11) { "abandon" } + "about"
