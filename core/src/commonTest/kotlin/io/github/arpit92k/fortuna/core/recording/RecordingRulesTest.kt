package io.github.arpit92k.fortuna.core.recording

import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.domain.UpdateFrequency
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.core.recording.RecordingError.BaseCurrencyAlreadyChosen
import io.github.arpit92k.fortuna.core.recording.RecordingError.BaseCurrencyHasNoRate
import io.github.arpit92k.fortuna.core.recording.RecordingError.BaseCurrencyNotChosen
import io.github.arpit92k.fortuna.core.recording.RecordingError.BeforeLatestSnapshot
import io.github.arpit92k.fortuna.core.recording.RecordingError.CategoryArchived
import io.github.arpit92k.fortuna.core.recording.RecordingError.DateInFuture
import io.github.arpit92k.fortuna.core.recording.RecordingError.EmptyName
import io.github.arpit92k.fortuna.core.recording.RecordingError.NameInUse
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoRateOnDate
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoSnapshotOnDate
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoSuchCategory
import io.github.arpit92k.fortuna.core.recording.RecordingError.NoSuchSource
import io.github.arpit92k.fortuna.core.recording.RecordingError.OnlySnapshot
import io.github.arpit92k.fortuna.core.recording.RecordingError.OpeningBalanceHasNoAmountAdded
import io.github.arpit92k.fortuna.core.recording.RecordingError.RateNeeded
import io.github.arpit92k.fortuna.core.recording.RecordingError.RateNeededBySnapshot
import io.github.arpit92k.fortuna.core.recording.RecordingError.SourceArchived
import io.github.arpit92k.fortuna.core.recording.RecordingError.SourceHasHistory
import io.github.arpit92k.fortuna.core.recording.RecordingError.WrongCurrency
import io.github.arpit92k.fortuna.core.recording.RecordingError.WrongRateCurrencies
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * One named test for each recording rule in the plan (step 07) and each rule
 * in the architecture that applies to the first version.
 */
class RecordingRulesTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val euros = Currency.forCode("EUR")!!

    private val today = LocalDate(2026, 10, 24)
    private val tomorrow = LocalDate(2026, 10, 25)
    private val january = LocalDate(2026, 1, 31)
    private val february = LocalDate(2026, 2, 28)
    private val april = LocalDate(2026, 4, 30)

    private val cash = CategoryId(1)
    private val investments = CategoryId(2)
    private val debt = CategoryId(5)

    private fun RecordingResult.dataset(): Dataset = assertIs<RecordingResult.Success>(this, "expected success").dataset

    private fun RecordingResult.error(): RecordingError = assertIs<RecordingResult.Failure>(this, "expected a refusal").error

    private fun rate(text: String, unit: Currency, quote: Currency): Rate =
        assertIs<RateParseResult.Success>(Rate.parse(text, unit, quote)).rate

    private fun gbp(pence: Long) = Money(pence, pounds)

    private fun usd(cents: Long) = Money(cents, dollars)

    // Pounds as base; a current account opened in January; a US brokerage opened in January at 0.80.
    private val chosen = Dataset.new().chooseBaseCurrency(pounds).dataset()
    private val withAccount = chosen.addSource("Current account", cash, january, gbp(250_000), today).dataset()
    private val account = withAccount.sources.last().id
    private val withBoth = withAccount
        .addSource("US brokerage", investments, january, usd(1_000_000), today, rate = rate("0.80", dollars, pounds))
        .dataset()
    private val brokerage = withBoth.sources.last().id

    // ---- Base currency ----

    @Test
    fun theBaseCurrencyIsChosenOnce() {
        assertEquals(pounds, chosen.baseCurrency)
        assertEquals(BaseCurrencyAlreadyChosen, chosen.chooseBaseCurrency(dollars).error())
    }

    @Test
    fun nothingIsRecordedBeforeTheBaseCurrencyIsChosen() {
        assertEquals(BaseCurrencyNotChosen, Dataset.new().addSource("A", cash, january, gbp(1), today).error())
        assertEquals(BaseCurrencyNotChosen, Dataset.new().recordRate(dollars, january, rate("0.8", dollars, pounds), today).error())
    }

    // ---- Adding a source ----

    // From the plan: the opening snapshot has no amount added.
    @Test
    fun aNewSourceHasAnOpeningSnapshotWithNothingAdded() {
        val source = withAccount.source(account)!!
        assertEquals(SourceId(6), account)
        assertEquals("Current account", source.name)
        assertEquals(pounds, source.currency)
        assertEquals(UpdateFrequency.MONTHLY, source.updateFrequency, "the default update frequency")
        val opening = source.snapshots.on(january)!!
        assertEquals(gbp(250_000), opening.value)
        assertEquals(gbp(0), opening.amountAdded)
        assertEquals(7, withAccount.nextId)
    }

    @Test
    fun aSourcesNameAndNoteAreTidied() {
        val source = chosen.addSource("  Savings  ", cash, january, gbp(1), today, note = "  ").dataset().sources.last()
        assertEquals("Savings", source.name)
        assertNull(source.note)
    }

    // From the plan and the architecture: names are unique among active sources.
    @Test
    fun namesAreUniqueAmongActiveSources() {
        assertEquals(NameInUse("current ACCOUNT"), withAccount.addSource(" current ACCOUNT ", cash, april, gbp(1), today).error())

        val archived = withAccount.copy(sources = withAccount.sources.map { it.copy(status = Status.ARCHIVED) })
        assertIs<RecordingResult.Success>(archived.addSource("Current account", cash, april, gbp(1), today))
    }

    @Test
    fun aSourceNeedsAName() {
        assertEquals(EmptyName, chosen.addSource("   ", cash, january, gbp(1), today).error())
    }

    @Test
    fun aSourceGoesIntoAnActiveCategoryThatExists() {
        assertEquals(NoSuchCategory(CategoryId(42)), chosen.addSource("A", CategoryId(42), january, gbp(1), today).error())
        val archivedCash = chosen.copy(categories = chosen.categories.map { if (it.id == cash) it.copy(status = Status.ARCHIVED) else it })
        assertEquals(CategoryArchived(cash), archivedCash.addSource("A", cash, january, gbp(1), today).error())
    }

    // From the user flows: the opening date can be in the past. From the architecture: never in the future.
    @Test
    fun theOpeningDateCanBeInThePastButNotInTheFuture() {
        assertIs<RecordingResult.Success>(chosen.addSource("Old", cash, LocalDate(2001, 1, 1), gbp(1), today))
        assertEquals(DateInFuture(tomorrow, today), chosen.addSource("New", cash, tomorrow, gbp(1), today).error())
    }

    // From the plan: a foreign-currency source needs a rate on or before its opening date.
    @Test
    fun aForeignCurrencySourceNeedsARateOnOrBeforeItsOpeningDate() {
        assertEquals(RateNeeded(dollars, january), withAccount.addSource("US", investments, january, usd(1), today).error())

        val rates = withBoth.rates.getValue(dollars)
        assertEquals(rate("0.80", dollars, pounds), rates.on(january), "the rate given was recorded on the opening date")

        assertIs<RecordingResult.Success>(withBoth.addSource("Second US", investments, april, usd(1), today), "an earlier rate covers it")
        assertEquals(
            RateNeeded(dollars, LocalDate(2025, 12, 1)),
            withBoth.addSource("Older US", investments, LocalDate(2025, 12, 1), usd(1), today).error(),
            "a later rate does not",
        )
    }

    @Test
    fun theRateGivenWithASourceMustFitItsCurrency() {
        assertEquals(
            WrongRateCurrencies(dollars, rate("0.9", euros, pounds)),
            withAccount.addSource("US", investments, january, usd(1), today, rate = rate("0.9", euros, pounds)).error(),
        )
        assertEquals(
            BaseCurrencyHasNoRate,
            chosen.addSource("UK", cash, january, gbp(1), today, rate = rate("1.25", pounds, dollars)).error(),
        )
    }

    // ---- Deleting a source ----

    @Test
    fun aSourceWithOnlyItsOpeningSnapshotCanBeDeleted() {
        val deleted = withBoth.deleteSource(account).dataset()
        assertNull(deleted.source(account))
        assertEquals(withBoth.nextId, deleted.nextId, "ids are never reused")
    }

    // From the plan and the architecture: sources with history are not deleted.
    @Test
    fun aSourceWithMoreThanItsOpeningSnapshotCannotBeDeleted() {
        val updated = withBoth.addSnapshot(account, april, gbp(300_000), gbp(0), today).dataset()
        assertEquals(SourceHasHistory(account), updated.deleteSource(account).error())
        assertEquals(NoSuchSource(SourceId(99)), withBoth.deleteSource(SourceId(99)).error())
    }

    // ---- Adding snapshots ----

    @Test
    fun aNewSnapshotIsAddedWithItsAmountAdded() {
        val updated = withBoth.addSnapshot(account, april, gbp(310_050), gbp(50_000), today, note = "Bonus").dataset()
        val latest = updated.source(account)!!.snapshots.latest()!!
        assertEquals(april, latest.date)
        assertEquals(gbp(310_050), latest.value.value)
        assertEquals(gbp(50_000), latest.value.amountAdded)
        assertEquals("Bonus", latest.value.note)
    }

    // From the plan: a new snapshot must be dated on or after the latest (MVP simplification).
    @Test
    fun aNewSnapshotMustBeDatedOnOrAfterTheLatest() {
        val updated = withBoth.addSnapshot(account, april, gbp(300_000), gbp(0), today).dataset()
        assertEquals(BeforeLatestSnapshot(april), updated.addSnapshot(account, february, gbp(1), gbp(0), today).error())
    }

    // From the plan and the architecture: a second entry for the same date replaces the first.
    @Test
    fun aNewSnapshotOnTheSameDateAsTheLatestReplacesIt() {
        val first = withBoth.addSnapshot(account, april, gbp(300_000), gbp(10_000), today).dataset()
        val replaced = first.addSnapshot(account, april, gbp(305_000), gbp(15_000), today).dataset()
        val snapshots = replaced.source(account)!!.snapshots
        assertEquals(2, snapshots.size)
        assertEquals(gbp(305_000), snapshots.on(april)!!.value)
        assertEquals(gbp(15_000), snapshots.on(april)!!.amountAdded)
    }

    // From the plan: a replacement for the opening snapshot has no amount added.
    @Test
    fun aReplacementForTheOpeningSnapshotHasNoAmountAdded() {
        assertEquals(OpeningBalanceHasNoAmountAdded, withBoth.addSnapshot(account, january, gbp(260_000), gbp(1), today).error())
        val replaced = withBoth.addSnapshot(account, january, gbp(260_000), gbp(0), today).dataset()
        assertEquals(gbp(260_000), replaced.source(account)!!.snapshots.on(january)!!.value)
    }

    // From the plan: a snapshot dated in the future is rejected.
    @Test
    fun aSnapshotDatedInTheFutureIsRejected() {
        assertEquals(DateInFuture(tomorrow, today), withBoth.addSnapshot(account, tomorrow, gbp(1), gbp(0), today).error())
    }

    @Test
    fun aSnapshotIsInItsSourcesCurrency() {
        assertEquals(WrongCurrency(pounds, dollars), withBoth.addSnapshot(account, april, usd(1), usd(0), today).error())
        assertEquals(WrongCurrency(pounds, dollars), withBoth.addSnapshot(account, april, gbp(1), usd(0), today).error())
    }

    // From the plan: a negative value is accepted for an asset and for a liability.
    @Test
    fun negativeValuesAreAcceptedForAssetsAndLiabilities() {
        val overdrawn = withBoth.addSnapshot(account, april, gbp(-20_000), gbp(-270_000), today).dataset()
        assertEquals(gbp(-20_000), overdrawn.source(account)!!.snapshots.latest()!!.value.value)

        val card = withBoth.addSource("Credit card", debt, january, gbp(120_000), today).dataset()
        val cardId = card.sources.last().id
        val inCredit = card.addSnapshot(cardId, april, gbp(-2_000), gbp(-122_000), today).dataset()
        assertEquals(gbp(-2_000), inCredit.source(cardId)!!.snapshots.latest()!!.value.value)
    }

    @Test
    fun anArchivedSourceTakesNoNewValues() {
        val archived = withBoth.copy(sources = withBoth.sources.map { if (it.id == account) it.copy(status = Status.ARCHIVED) else it })
        assertEquals(SourceArchived(account), archived.addSnapshot(account, april, gbp(1), gbp(0), today).error())
        assertEquals(NoSuchSource(SourceId(99)), withBoth.addSnapshot(SourceId(99), april, gbp(1), gbp(0), today).error())
    }

    // ---- Editing and deleting snapshots ----

    // From the plan: a snapshot's date cannot be edited. Editing names a date; it never moves one.
    @Test
    fun editingChangesASnapshotsFiguresButNotItsDate() {
        val updated = withBoth.addSnapshot(account, april, gbp(300_000), gbp(10_000), today).dataset()
        val edited = updated.editSnapshot(account, april, gbp(301_000), gbp(11_000), note = "Corrected").dataset()
        val snapshot = edited.source(account)!!.snapshots.on(april)!!
        assertEquals(gbp(301_000), snapshot.value)
        assertEquals(gbp(11_000), snapshot.amountAdded)
        assertEquals("Corrected", snapshot.note)
        assertEquals(listOf(january, april), edited.source(account)!!.snapshots.map { it.date })

        assertEquals(NoSnapshotOnDate(february), updated.editSnapshot(account, february, gbp(1), gbp(0)).error())
    }

    // From the plan: the opening snapshot's amount added stays zero when edited.
    @Test
    fun theOpeningSnapshotsAmountAddedStaysZeroWhenEdited() {
        assertEquals(OpeningBalanceHasNoAmountAdded, withBoth.editSnapshot(account, january, gbp(1), gbp(5)).error())
        assertIs<RecordingResult.Success>(withBoth.editSnapshot(account, january, gbp(1), gbp(0)))
    }

    @Test
    fun theLatestSnapshotCanBeDeleted() {
        val updated = withBoth.addSnapshot(account, april, gbp(300_000), gbp(0), today).dataset()
        val deleted = updated.deleteLatestSnapshot(account).dataset()
        assertEquals(listOf(january), deleted.source(account)!!.snapshots.map { it.date })
    }

    // From the plan: a source's only snapshot cannot be deleted; the source is deleted instead.
    @Test
    fun theOnlySnapshotCannotBeDeleted() {
        assertEquals(OnlySnapshot(account), withBoth.deleteLatestSnapshot(account).error())
    }

    // ---- Exchange rates ----

    @Test
    fun aRateIsRecordedEitherWayRound() {
        val typedFromPounds = rate("1.33", pounds, dollars)
        val updated = withBoth.recordRate(dollars, april, typedFromPounds, today).dataset()
        assertEquals(typedFromPounds, updated.rates.getValue(dollars).on(april))
    }

    // From the plan and the architecture: a second rate for a date replaces the first. This is how a rate is corrected.
    @Test
    fun aSecondRateForADateReplacesTheFirst() {
        val corrected = withBoth.recordRate(dollars, january, rate("0.79", dollars, pounds), today).dataset()
        assertEquals(1, corrected.rates.getValue(dollars).size)
        assertEquals(rate("0.79", dollars, pounds), corrected.rates.getValue(dollars).on(january))
    }

    // From the plan: the base currency's rate is always one, so it has none of its own.
    @Test
    fun theBaseCurrencyHasNoRatesOfItsOwn() {
        assertEquals(BaseCurrencyHasNoRate, withBoth.recordRate(pounds, april, rate("1", pounds, dollars), today).error())
    }

    @Test
    fun aRateRelatesItsCurrencyToTheBase() {
        val euroRate = rate("1.08", euros, dollars)
        assertEquals(WrongRateCurrencies(euros, euroRate), withBoth.recordRate(euros, april, euroRate, today).error())
        val dollarRateFiledAsEuro = rate("0.8", dollars, pounds)
        assertEquals(WrongRateCurrencies(euros, dollarRateFiledAsEuro), withBoth.recordRate(euros, april, dollarRateFiledAsEuro, today).error())
    }

    // From the architecture: no rate is dated in the future.
    @Test
    fun aRateDatedInTheFutureIsRejected() {
        assertEquals(DateInFuture(tomorrow, today), withBoth.recordRate(dollars, tomorrow, rate("0.75", dollars, pounds), today).error())
    }

    @Test
    fun aRateCanBeDeleted() {
        val twoRates = withBoth.recordRate(dollars, april, rate("0.75", dollars, pounds), today).dataset()
        val deleted = twoRates.deleteRate(dollars, april).dataset()
        assertEquals(listOf(january), deleted.rates.getValue(dollars).map { it.date })
        assertEquals(NoRateOnDate(dollars, february), twoRates.deleteRate(dollars, february).error())
    }

    // From the plan: the rate that covers a currency's earliest snapshot cannot be deleted.
    @Test
    fun theRateTheEarliestSnapshotDependsOnCannotBeDeleted() {
        assertEquals(RateNeededBySnapshot(dollars, brokerage, january), withBoth.deleteRate(dollars, january).error())

        val earlierRate = withBoth.recordRate(dollars, LocalDate(2026, 1, 1), rate("0.81", dollars, pounds), today).dataset()
        assertIs<RecordingResult.Success>(earlierRate.deleteRate(dollars, january), "an earlier rate still covers the snapshot")
    }

    @Test
    fun theLastRateOfACurrencyNoSourceUsesCanBeDeleted() {
        val withEuro = withBoth.recordRate(euros, april, rate("0.86", euros, pounds), today).dataset()
        val deleted = withEuro.deleteRate(euros, april).dataset()
        assertTrue(euros !in deleted.rates)
    }

    // ---- The periodic update ----

    private val usedInApril = withBoth
        .addSource("Savings", cash, february, gbp(100_000), today).dataset()
        .let { it.addSnapshot(it.sources.last().id, april, gbp(110_000), gbp(10_000), today).dataset() }
    private val savings = usedInApril.sources.last().id

    private fun UpdateQueueResult.queue(): UpdateQueue = assertIs<UpdateQueueResult.Success>(this).queue

    // From the user flows: the update walks the sources, the one updated longest ago first.
    @Test
    fun theUpdateWalksTheSourcesUpdatedLongestAgoFirst() {
        val queue = usedInApril.updateQueue(today, today).queue()
        assertEquals(listOf(account, brokerage, savings), queue.sources.map { it.id })
        assertEquals(0, queue.leftOut)
    }

    // From the plan: the update queue leaves out a source with a value on or after the update date, and counts it.
    @Test
    fun theUpdateLeavesOutSourcesWithAValueOnOrAfterItsDate() {
        val queue = usedInApril.updateQueue(april, today).queue()
        assertEquals(listOf(account, brokerage), queue.sources.map { it.id })
        assertEquals(1, queue.leftOut, "savings already has a value on 30 April")

        val earlier = usedInApril.updateQueue(LocalDate(2026, 1, 15), today).queue()
        assertTrue(earlier.sources.isEmpty())
        assertEquals(3, earlier.leftOut)
    }

    @Test
    fun startingAgainWithTheSameDateContinuesWithTheSourcesNotYetUpdated() {
        val partWay = usedInApril.addSnapshot(account, today, gbp(320_000), gbp(0), today).dataset()
        assertEquals(listOf(brokerage, savings), partWay.updateQueue(today, today).queue().sources.map { it.id })
    }

    @Test
    fun aSkippedSourceIsOfferedAgain() {
        // Skipping records nothing, so the source is still due when the update starts again.
        assertEquals(usedInApril.updateQueue(today, today), usedInApril.updateQueue(today, today))
    }

    @Test
    fun theUpdateListsEachForeignCurrencyWithItsLastRateAndAge() {
        val queue = usedInApril.updateQueue(today, today).queue()
        assertEquals(listOf(dollars), queue.currencies.map { it.currency })
        val confirm = queue.currencies.single()
        assertEquals(january, confirm.lastRate!!.date)
        assertEquals(january.daysUntilToday(), confirm.ageInDays)
    }

    private fun LocalDate.daysUntilToday(): Int = (today.toEpochDays() - toEpochDays()).toInt()

    @Test
    fun archivedSourcesAreNotInTheUpdate() {
        val archived = usedInApril.copy(sources = usedInApril.sources.map { if (it.id == brokerage) it.copy(status = Status.ARCHIVED) else it })
        val queue = archived.updateQueue(today, today).queue()
        assertEquals(listOf(account, savings), queue.sources.map { it.id })
        assertTrue(queue.currencies.isEmpty(), "no source in the update uses dollars")
    }

    @Test
    fun anUpdateDatedInTheFutureIsRefused() {
        assertEquals(
            UpdateQueueResult.Failure(DateInFuture(tomorrow, today)),
            usedInApril.updateQueue(tomorrow, today),
        )
    }

    // ---- Every change returns a new dataset ----

    @Test
    fun changesLeaveTheOriginalDatasetAsItWas() {
        val before = withBoth
        val copy = withBoth.copy()
        withBoth.addSnapshot(account, april, gbp(1), gbp(0), today).dataset()
        withBoth.recordRate(dollars, april, rate("0.75", dollars, pounds), today).dataset()
        withBoth.deleteSource(account).dataset()
        assertSame(before, withBoth)
        assertEquals(copy, withBoth)
    }
}
