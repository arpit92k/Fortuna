package io.github.arpit92k.fortuna.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Step 24: what a bug report holds, crash records, and sending through the email app. */
@OptIn(ExperimentalCoroutinesApi::class)
class BugReportTest {
    private val dispatcher = StandardTestDispatcher()
    private val device = DeviceInfo("0.1.0 (1)", "16 (API 36)", "Google Pixel 9")
    private lateinit var directory: File

    // What a crash's message might hold: a figure, an account's name, a PIN, a phrase word.
    private val secrets = listOf("£1,234.56", "Joint savings", "246810", "abandon")
    private fun crash(): Throwable =
        IllegalStateException("Could not save ${secrets.joinToString()}", IllegalArgumentException("Bad ${secrets[0]}"))

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        directory = createTempDirectory("crashes").toFile()
    }

    @After
    fun stop() {
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    @Test
    fun aCrashRecordKeepsCodeLocationsAndDropsMessages() {
        val record = crashRecordOf(crash(), "0.1.0 (1)")
        assertTrue(record.startsWith("Fortuna 0.1.0 (1) crashed"))
        assertTrue("java.lang.IllegalStateException" in record)
        assertTrue("Caused by: java.lang.IllegalArgumentException" in record)
        assertTrue("at io.github.arpit92k.fortuna.report.BugReportTest.crash(BugReportTest.kt:" in record, record)
        for (secret in secrets) assertFalse(secret in record, "the record holds \"$secret\"")
    }

    @Test
    fun aLongStackIsCut() {
        fun deep(n: Int): Throwable = if (n == 0) RuntimeException("x") else deep(n - 1)
        val record = crashRecordOf(deep(60), "0.1.0")
        assertEquals(FRAMES_KEPT, record.lines().count { it.trimStart().startsWith("at ") })
        assertTrue(record.lines().last().trim().matches(Regex("… \\d+ more")))
    }

    @Test
    fun onlyTheLatestCrashesAreKept() {
        val store = CrashStore(directory, keep = 3)
        (1..5).forEach { store.save("crash $it") }
        assertEquals(listOf("crash 3", "crash 4", "crash 5"), store.records())
        store.clear()
        assertEquals(emptyList(), store.records())
    }

    @Test
    fun theReportHoldsTheDescriptionAndThePhoneAndNothingElse() {
        val record = crashRecordOf(crash(), device.appVersion)
        val draft = emailDraftOf("  The chart is blank.  ", device, listOf(record))
        assertEquals(SUPPORT_EMAIL, draft.to)
        assertEquals("Fortuna crash report (0.1.0 (1))", draft.subject)
        assertTrue(draft.body.startsWith("The chart is blank.\n\nApp version: 0.1.0 (1)\nAndroid: 16 (API 36)\nPhone: Google Pixel 9"))
        assertTrue(record in draft.body, "the whole crash record, as the user saw it")
        for (secret in secrets) assertFalse(secret in draft.body, "the report holds \"$secret\"")
        assertEquals("Fortuna bug report (0.1.0 (1))", emailDraftOf("", device, emptyList()).subject)
    }

    @Test
    fun aSavedCrashIsOfferedAndGoesOnceSent() = runTest(dispatcher) {
        val store = CrashStore(directory).apply { save(crashRecordOf(crash(), device.appVersion)) }
        val report = BugReportViewModel(store, device, io = dispatcher)
        advanceUntilIdle()
        assertTrue(report.ui.value.offerCrash)
        report.open()
        report.description("It closed when I tapped the eye")
        var sent: EmailDraft? = null
        report.send { sent = it; true }
        advanceUntilIdle()
        assertTrue(sent!!.body.startsWith("It closed when I tapped the eye"))
        assertFalse(report.ui.value.open)
        assertEquals(emptyList(), store.records(), "not offered again")
    }

    @Test
    fun withNoEmailAppTheReportStaysOpen() = runTest(dispatcher) {
        val report = BugReportViewModel(CrashStore(directory), device, io = dispatcher)
        advanceUntilIdle()
        assertFalse(report.ui.value.offerCrash, "nothing to offer without a crash")
        report.open()
        report.send { false }
        assertTrue(report.ui.value.open)
        assertTrue(report.ui.value.noEmailApp)
    }

    @Test
    fun aCrashCanBeDeletedInstead() = runTest(dispatcher) {
        val store = CrashStore(directory).apply { save("crash") }
        val report = BugReportViewModel(store, device, io = dispatcher)
        advanceUntilIdle()
        report.deleteCrashes()
        advanceUntilIdle()
        assertFalse(report.ui.value.offerCrash)
        assertEquals(emptyList(), store.records())
    }
}
