package io.github.arpit92k.fortuna.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MoneyTest {
    private val pounds = Currency.forCode("GBP")!!
    private val dollars = Currency.forCode("USD")!!
    private val yen = Currency.forCode("JPY")!!
    private val dinars = Currency.forCode("BHD")!!

    private fun parsed(text: String, currency: Currency): Money =
        assertIs<MoneyParseResult.Success>(Money.parse(text, currency), "parsing \"$text\"").money

    private fun problem(text: String, currency: Currency): AmountProblem =
        assertIs<MoneyParseResult.Failure>(Money.parse(text, currency), "parsing \"$text\"").problem

    // From the plan: "10000.00" in pounds is 1,000,000 pence and writes back unchanged.
    @Test
    fun tenThousandPoundsIsOneMillionPence() {
        val amount = parsed("10000.00", pounds)
        assertEquals(1_000_000L, amount.minorUnits)
        assertEquals(pounds, amount.currency)
        assertEquals("10000.00", amount.toDecimalText())
    }

    // From the plan: a currency with no decimal places, such as yen.
    @Test
    fun yenHasNoDecimalPart() {
        val amount = parsed("1500", yen)
        assertEquals(1500L, amount.minorUnits)
        assertEquals("1500", amount.toDecimalText())
        assertEquals(AmountProblem.TOO_MANY_DECIMAL_PLACES, problem("1500.5", yen))
    }

    // From the plan: too many decimal places is rejected.
    @Test
    fun tooManyDecimalPlacesIsRejectedNotRounded() {
        assertEquals(AmountProblem.TOO_MANY_DECIMAL_PLACES, problem("10.001", pounds))
        assertEquals(AmountProblem.TOO_MANY_DECIMAL_PLACES, problem("10.000", pounds))
    }

    @Test
    fun threeDecimalPlacesForDinars() {
        val amount = parsed("1.234", dinars)
        assertEquals(1234L, amount.minorUnits)
        assertEquals("1.234", amount.toDecimalText())
    }

    @Test
    fun fewerDecimalPlacesAreFilledWithZeros() {
        assertEquals(1050L, parsed("10.5", pounds).minorUnits)
        assertEquals(1000L, parsed("10", pounds).minorUnits)
        assertEquals("10.50", parsed("10.5", pounds).toDecimalText())
    }

    @Test
    fun negativeAmounts() {
        val amount = parsed("-0.50", pounds)
        assertEquals(-50L, amount.minorUnits)
        assertEquals("-0.50", amount.toDecimalText())
        assertEquals("-1500", parsed("-1500", yen).toDecimalText())
    }

    @Test
    fun zeroWritesWithItsDecimalPlaces() {
        assertEquals("0.00", Money(0, pounds).toDecimalText())
        assertEquals("0.00", parsed("-0", pounds).toDecimalText())
        assertEquals("0.05", Money(5, pounds).toDecimalText())
        assertEquals("0.005", Money(5, dinars).toDecimalText())
    }

    @Test
    fun textThatIsNotAPlainNumberIsRejected() {
        assertEquals(AmountProblem.EMPTY, problem("", pounds))
        for (text in listOf("-", "abc", "1,000.00", "1 000", " 5", "5 ", "+5", "1e3", ".5", "5.", "1.2.3", "--5", "١٢")) {
            assertEquals(AmountProblem.NOT_A_NUMBER, problem(text, pounds), "parsing \"$text\"")
        }
    }

    @Test
    fun amountsTooLargeToHoldExactlyAreRejected() {
        assertEquals(Long.MAX_VALUE, parsed("92233720368547758.07", pounds).minorUnits)
        assertEquals(AmountProblem.OUT_OF_RANGE, problem("92233720368547758.08", pounds))
        assertEquals(AmountProblem.OUT_OF_RANGE, problem("100000000000000000000", yen))
    }

    @Test
    fun everyAmountWritesBackToTheSameValue() {
        for (units in listOf(0L, 1L, -1L, 99L, 100L, -12_345L, Long.MAX_VALUE, -Long.MAX_VALUE)) {
            for (currency in listOf(pounds, yen, dinars)) {
                val amount = Money(units, currency)
                assertEquals(amount, parsed(amount.toDecimalText(), currency))
            }
        }
    }

    @Test
    fun addSubtractAndNegate() {
        val a = Money(1_050, pounds)
        val b = Money(250, pounds)
        assertEquals(Money(1_300, pounds), a + b)
        assertEquals(Money(800, pounds), a - b)
        assertEquals(Money(-250, pounds), b - Money(500, pounds))
        assertEquals(Money(-1_050, pounds), -a)
        assertEquals(a, -(-a))
    }

    // From the plan: adding two different currencies is rejected.
    @Test
    fun differentCurrenciesCannotBeCombined() {
        val failure = assertFailsWith<CurrencyMismatchException> { Money(100, pounds) + Money(100, dollars) }
        assertEquals(pounds, failure.first)
        assertEquals(dollars, failure.second)
        assertFailsWith<CurrencyMismatchException> { Money(100, pounds) - Money(100, dollars) }
    }

    @Test
    fun arithmeticThatWouldOverflowIsRejected() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, pounds) + Money(1, pounds) }
        assertFailsWith<ArithmeticException> { Money(-Long.MAX_VALUE, pounds) - Money(2, pounds) }
        assertFailsWith<ArithmeticException> { -Money(Long.MIN_VALUE, pounds) }
    }

    @Test
    fun readableDescription() {
        assertEquals("10000.00 GBP", Money(1_000_000, pounds).toString())
    }
}
