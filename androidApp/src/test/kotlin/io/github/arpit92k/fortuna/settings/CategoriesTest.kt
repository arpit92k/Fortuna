package io.github.arpit92k.fortuna.settings

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.SourceId
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.money.Money
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addSource
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
import io.github.arpit92k.fortuna.source.SourcePageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Step 29 (UF-08): managing categories, and moving a source between them, through the real core. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val pounds = Currency.forCode("GBP")!!
    private val january = LocalDate(2026, 1, 31)
    private val cash = CategoryId(1)
    private lateinit var core: Core
    private lateinit var categories: CategoriesViewModel
    private lateinit var page: SourcePageViewModel
    private lateinit var dashboard: DashboardViewModel

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        core = phone.unlocked(dispatcher)
        categories = CategoriesViewModel(core)
        page = SourcePageViewModel(core)
        dashboard = DashboardViewModel(core)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private suspend fun savings(): SourceId {
        val result = core.run { record { d, today -> d.addSource("Savings", cash, january, Money(100_000, pounds), today) } }
        return assertIs<RecordingResult.Success>(result).dataset.sources.last().id
    }

    @Test
    fun theStarterCategoriesAreListedAsAssetsAndDebts() = runTest(dispatcher) {
        advanceUntilIdle()
        val ui = categories.ui.value!!
        assertEquals(listOf("Cash", "Investments", "Property", "Pension", "Debt"), ui.active.map { it.name })
        assertEquals(CategoryKind.LIABILITY, ui.active.last().kind)
        assertTrue(ui.archived.isEmpty())
    }

    @Test
    fun aCategoryIsAddedRenamedAndArchived() = runTest(dispatcher) {
        var done = false
        categories.add("Crypto", CategoryKind.ASSET) { done = true }
        advanceUntilIdle()
        assertTrue(done)
        val crypto = categories.ui.value!!.active.single { it.name == "Crypto" }

        categories.edit(crypto.id, "Coins", CategoryKind.LIABILITY)
        advanceUntilIdle()
        val coins = categories.ui.value!!.active.single { it.id == crypto.id }
        assertEquals("Coins", coins.name)
        assertEquals(CategoryKind.LIABILITY, coins.kind, "no sources yet, so its kind can change")

        categories.archive(crypto.id)
        advanceUntilIdle()
        assertEquals(listOf("Coins"), categories.ui.value!!.archived.map { it.name })
        categories.restore(crypto.id)
        advanceUntilIdle()
        assertTrue(categories.ui.value!!.archived.isEmpty())
    }

    @Test
    fun aNameInUseAndACategoryWithOpenSourcesAreRefused() = runTest(dispatcher) {
        savings()
        advanceUntilIdle()
        var done = false
        categories.add("debt", CategoryKind.ASSET) { done = true }
        advanceUntilIdle()
        assertEquals(CategoryProblem.NameInUse("debt"), categories.ui.value!!.problem)
        categories.archive(cash) { done = true }
        advanceUntilIdle()
        assertEquals(CategoryProblem.HasOpenSources(1), categories.ui.value!!.problem)
        assertEquals(false, done)
        categories.clearProblem()
        assertNull(categories.ui.value!!.problem)
    }

    @Test
    fun aSourceMovesToAnotherCategoryOfTheSameKind() = runTest(dispatcher) {
        val id = savings()
        page.show(id)
        advanceUntilIdle()
        val targets = page.ui.value!!.moveTargets
        assertEquals(listOf("Investments", "Property", "Pension"), targets.map { it.name }, "assets only, not its own")
        page.move(id, targets.first().id)
        advanceUntilIdle()
        assertEquals("Investments", page.ui.value!!.category)
        assertEquals("Investments", dashboard.ui.value!!.sources.single().category)
        assertEquals(1, categories.ui.value!!.active.single { it.name == "Investments" }.openSources)
    }
}
