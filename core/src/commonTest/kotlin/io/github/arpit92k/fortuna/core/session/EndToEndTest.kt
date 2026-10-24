package io.github.arpit92k.fortuna.core.session

import io.github.arpit92k.fortuna.core.backup.RestoreResult
import io.github.arpit92k.fortuna.core.crypto.PlatformCrypto
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.insights.SourceHistory
import io.github.arpit92k.fortuna.core.insights.history
import io.github.arpit92k.fortuna.core.insights.netWorthOn
import io.github.arpit92k.fortuna.core.keys.FakeClock
import io.github.arpit92k.fortuna.core.keys.FakeDeviceKey
import io.github.arpit92k.fortuna.core.keys.SetupResult
import io.github.arpit92k.fortuna.core.keys.UnlockResult
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.ports.OkioPrivateFiles
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.UpdateQueueResult
import io.github.arpit92k.fortuna.core.recording.addSnapshot
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.core.recording.editSnapshot
import io.github.arpit92k.fortuna.core.recording.recordRate
import io.github.arpit92k.fortuna.core.recording.updateQueue
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * The plan's end-to-end test for step 13, with no screen: set up; add an
 * asset, a liability and a foreign-currency source; run two periodic updates
 * with a skipped source; lock and unlock; correct a value; back up; restore
 * on a second device; and check net worth and each source's history at every
 * stage. Everything goes through the session, as the interface will.
 */
class EndToEndTest {
    private val crypto = PlatformCrypto
    private val clock = FakeClock().also { it.date = LocalDate(2026, 10, 24) }

    private fun device() = Session(
        OkioPrivateFiles(FakeFileSystem(), "/data".toPath()),
        crypto,
        FakeDeviceKey(),
        clock,
        pinIterations = { 1_000 },
        phraseIterations = 1_000,
    ).also { it.start() }

    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private fun gbp(pence: Long) = Money(pence, pounds)
    private fun usd(cents: Long) = Money(cents, dollars)
    private fun rate(text: String) = assertIs<RateParseResult.Success>(Rate.parse(text, dollars, pounds)).rate

    private val january = LocalDate(2026, 1, 31)
    private val april = LocalDate(2026, 4, 30)
    private val july = LocalDate(2026, 7, 31)

    private val account = SourceId(6)
    private val mortgage = SourceId(7)
    private val brokerage = SourceId(8)

    private fun Session.ok(rule: (Dataset, LocalDate) -> RecordingResult) =
        assertIs<RecordingResult.Success>(record(rule), "a change the app should accept")

    private fun Session.netWorth(date: LocalDate): Money = read { data, _ -> data.netWorthOn(date).total }

    private fun Session.historyOf(id: SourceId): SourceHistory = read { data, _ -> data.history(id) }

    private fun Session.queue(date: LocalDate) =
        read { data, today -> assertIs<UpdateQueueResult.Success>(data.updateQueue(date, today)).queue }

    @Test
    fun theWholeAppWorksWithoutAScreen() {
        val phone = device()
        assertEquals(SessionState.NoData, phone.state.value)

        // ---- Setup ----
        assertIs<SetupResult.Ready>(phone.beginSetup("246810"))
        val phrase = assertIs<SessionState.SettingUp>(phone.state.value).phrase
        assertEquals(true, phone.phraseConfirmed(mapOf(3 to phrase.words[2], 7 to phrase.words[6].uppercase())))
        phone.finishSetup(pounds)
        assertEquals(SessionState.Unlocked, phone.state.value)

        // ---- An asset, a liability and a foreign-currency source, in January ----
        phone.ok { d, today -> d.addSource("Current account", CategoryId(1), january, gbp(250_000), today) }
        phone.ok { d, today -> d.addSource("Mortgage", CategoryId(5), january, gbp(18_000_000), today) }
        phone.ok { d, today -> d.addSource("US brokerage", CategoryId(2), january, usd(1_000_000), today, rate = rate("0.80")) }
        assertEquals(listOf(account, mortgage, brokerage), phone.dataset.value!!.sources.map { it.id })
        assertEquals(gbp(250_000 + 800_000 - 18_000_000), phone.netWorth(january))

        // ---- Periodic update 1, dated 30 April: a new dollar rate; the mortgage is skipped ----
        val first = phone.queue(april)
        assertEquals(listOf(account, mortgage, brokerage), first.sources.map { it.id })
        assertEquals(89, first.currencies.single().ageInDays, "the January rate is 89 days old on 30 April")
        phone.ok { d, today -> d.recordRate(dollars, april, rate("0.75"), today) }
        phone.ok { d, today -> d.addSnapshot(account, april, gbp(310_050), gbp(50_000), today) }
        phone.ok { d, today -> d.addSnapshot(brokerage, april, usd(1_150_000), usd(100_000), today) }
        assertEquals(listOf(mortgage), phone.queue(april).sources.map { it.id }, "starting again offers the skipped source")
        assertEquals(gbp(310_050 + 862_500 - 18_000_000), phone.netWorth(april))
        assertEquals(gbp(250_000 + 800_000 - 18_000_000), phone.netWorth(january), "January is unchanged")

        val brokerageHistory = phone.historyOf(brokerage).entries.last()
        assertEquals(usd(150_000), brokerageHistory.change)
        assertEquals(usd(50_000), brokerageHistory.growth)

        // ---- Periodic update 2, dated 31 July: the mortgage first; the brokerage is skipped ----
        val second = phone.queue(july)
        assertEquals(listOf(mortgage, account, brokerage), second.sources.map { it.id }, "the one updated longest ago first")
        assertEquals(92, second.currencies.single().ageInDays, "the April rate is 92 days old on 31 July")
        phone.ok { d, today -> d.addSnapshot(mortgage, july, gbp(17_895_025), gbp(-150_000), today) }
        phone.ok { d, today -> d.addSnapshot(account, july, gbp(330_050), gbp(0), today) }
        assertEquals(gbp(330_050 + 862_500 - 17_895_025), phone.netWorth(july), "the skipped brokerage keeps its April value")
        assertEquals(gbp(45_025), phone.historyOf(mortgage).entries.last().growth, "£450.25 of interest")

        // ---- Lock and unlock ----
        phone.lock()
        assertIs<SessionState.Locked>(phone.state.value)
        assertFailsWith<IllegalStateException> { phone.netWorth(july) }
        assertIs<UnlockResult.WrongPin>(phone.unlock("111111"))
        assertIs<UnlockResult.Success>(phone.unlock("246810"))
        assertEquals(gbp(330_050 + 862_500 - 17_895_025), phone.netWorth(july), "everything is still there")

        // ---- Correct a value: the April balance was £3,150.50, not £3,100.50 ----
        phone.ok { d, _ -> d.editSnapshot(account, april, gbp(315_050), gbp(50_000)) }
        assertEquals(gbp(315_050 + 862_500 - 18_000_000), phone.netWorth(april))
        val corrected = phone.historyOf(account)
        assertEquals(listOf(null, gbp(15_050), gbp(15_000)), corrected.entries.map { it.growth })
        assertEquals(gbp(50_000), corrected.totalAdded)
        assertEquals(gbp(30_050), corrected.totalGrowth)

        // ---- Back up ----
        val backup = phone.prepareBackup()
        assertEquals(true, phone.confirmBackupSaved(backup))
        val settings = phone.dataset.value!!.settings
        assertEquals(settings.revision, settings.lastBackupRevision)

        // ---- Restore on a second device ----
        val newPhone = device()
        assertEquals(SessionState.NoData, newPhone.state.value)
        assertEquals(RestoreResult.Success, newPhone.restore(backup.file, phrase, "975310"))
        assertEquals(SessionState.Unlocked, newPhone.state.value)
        assertEquals(phone.dataset.value, newPhone.dataset.value)
        for (date in listOf(january, april, july, clock.date)) {
            assertEquals(phone.netWorth(date), newPhone.netWorth(date), "net worth on $date")
        }
        for (id in listOf(account, mortgage, brokerage)) {
            assertEquals(phone.historyOf(id), newPhone.historyOf(id), "the history of source ${id.value}")
        }

        // The new phone has its own PIN.
        newPhone.lock()
        assertIs<UnlockResult.WrongPin>(newPhone.unlock("246810"))
        assertIs<UnlockResult.Success>(newPhone.unlock("975310"))
    }
}
