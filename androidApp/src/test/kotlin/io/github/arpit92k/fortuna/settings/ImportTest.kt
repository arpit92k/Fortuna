package io.github.arpit92k.fortuna.settings

import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.FakeDocumentFiles
import io.github.arpit92k.fortuna.TestPhone
import io.github.arpit92k.fortuna.core.importing.IMPORT_TEMPLATE
import io.github.arpit92k.fortuna.core.importing.ImportProblem
import io.github.arpit92k.fortuna.core.money.Currency
import io.github.arpit92k.fortuna.core.rates.Rate
import io.github.arpit92k.fortuna.core.rates.RateParseResult
import io.github.arpit92k.fortuna.dashboard.DashboardViewModel
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Step 31 (UF-15): importing history from the template, through the real core. */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportTest {
    private val dispatcher = StandardTestDispatcher()
    private val phone = TestPhone()
    private val files = FakeDocumentFiles()
    private lateinit var core: Core
    private lateinit var importer: ImportViewModel
    private lateinit var dashboard: DashboardViewModel

    @Before
    fun start() {
        Dispatchers.setMain(dispatcher)
        core = phone.unlocked(dispatcher)
        importer = ImportViewModel(core, files, io = dispatcher)
        dashboard = DashboardViewModel(core)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    @Test
    fun theTemplateIsSavedWhereTheUserChose() = runTest(dispatcher) {
        importer.saveTemplate("content://template")
        advanceUntilIdle()
        assertEquals(IMPORT_TEMPLATE, files.saved.getValue("content://template").decodeToString())
        assertEquals(ImportStep.Start(ImportMessage.TEMPLATE_SAVED), importer.step.value)
    }

    @Test
    fun aFilledInFileIsPreviewedThenImportedOnConfirming() = runTest(dispatcher) {
        files.saved["content://file"] = (IMPORT_TEMPLATE + "ISA,Investments,2026-01-31,1000,,\nBrokerage,Investments,2026-01-31,500,,USD\n").encodeToByteArray()
        importer.fileChosen("content://file")
        advanceUntilIdle()
        val preview = assertIs<ImportStep.Preview>(importer.step.value)
        assertEquals(setOf("Brokerage", "ISA"), preview.plan.newAccounts.map { it.name }.toSet())
        assertFalse(preview.ready, "the dollar needs a rate first")
        assertTrue(dashboard.ui.value!!.sources.isEmpty(), "nothing written before confirming")

        val dollars = Currency.forCode("USD")!!
        val rate = assertIs<RateParseResult.Success>(Rate.parse("0.8", dollars, Currency.forCode("GBP")!!)).rate
        importer.rate(dollars, rate)
        importer.confirm()
        advanceUntilIdle()
        assertEquals(ImportStep.Done(values = 2, accounts = 2), importer.step.value)
        assertEquals(setOf("ISA", "Brokerage"), dashboard.ui.value!!.sources.map { it.name }.toSet())
    }

    @Test
    fun aFileThatIsNotTheTemplateIsRefusedWithItsProblems() = runTest(dispatcher) {
        files.saved["content://photo"] = "not a spreadsheet".encodeToByteArray()
        importer.fileChosen("content://photo")
        advanceUntilIdle()
        assertEquals(ImportStep.Problems(listOf(ImportProblem.NotTheTemplate())), importer.step.value)
        importer.fileChosen("content://gone")
        advanceUntilIdle()
        assertEquals(ImportStep.Start(ImportMessage.CANNOT_READ), importer.step.value)
    }
}
