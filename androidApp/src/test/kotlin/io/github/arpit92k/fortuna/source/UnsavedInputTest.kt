package io.github.arpit92k.fortuna.source

import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Leaving a form asks first only when something was entered and not saved. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnsavedInputTest {
    private val dispatcher = StandardTestDispatcher()
    private val pounds = Currency.forCode("GBP")!!

    @Before
    fun start() = Dispatchers.setMain(dispatcher)

    @After
    fun stop() = Dispatchers.resetMain()

    @Test
    fun aNewAccountHasInputOnceAnythingIsEntered() {
        val add = AddSourceViewModel(TestPhone().unlocked(dispatcher), locale = { Locale.UK })
        assertFalse(add.hasInput)
        add.category(CategoryId(1))
        assertTrue(add.hasInput, "choosing a category counts")
        add.discard()
        assertFalse(add.hasInput)
        add.note("Joint")
        assertTrue(add.hasInput)
    }

    @Test
    fun aCorrectionHasChangesOnlyOnceSomethingDiffers() {
        val entry = ValueEntryViewModel(TestPhone().unlocked(dispatcher), locale = { Locale.UK })
        val source = ValueEntrySource(
            id = SourceId(6),
            currency = pounds,
            opening = LocalDate(2026, 1, 31),
            latest = LocalDate(2026, 4, 30),
            liability = false,
            today = LocalDate(2026, 10, 24),
        )
        // Opening a correction fills in the old figures: nothing has changed yet.
        entry.open(source, LocalDate(2026, 4, 30), Money(310_050, pounds) to Money(50_000, pounds), "Bonus")
        assertFalse(entry.hasChanges)
        entry.note("Bonus paid in")
        assertTrue(entry.hasChanges)
        entry.note("Bonus")
        assertFalse(entry.hasChanges, "back as it was")

        entry.discard()
        entry.open(source, null, null, null)
        assertFalse(entry.hasChanges, "a new value starts empty")
        entry.value("3,200")
        assertTrue(entry.hasChanges)
    }
}
