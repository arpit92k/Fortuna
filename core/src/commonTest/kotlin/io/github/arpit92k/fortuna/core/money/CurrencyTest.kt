package io.github.arpit92k.fortuna.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CurrencyTest {
    @Test
    fun poundsHaveTwoDecimalPlaces() {
        val pounds = assertNotNull(Currency.forCode("GBP"))
        assertEquals("Pound Sterling", pounds.name)
        assertEquals(2, pounds.decimalPlaces)
    }

    @Test
    fun yenHasNoDecimalPlaces() {
        assertEquals(0, assertNotNull(Currency.forCode("JPY")).decimalPlaces)
    }

    @Test
    fun kuwaitiDinarHasThreeDecimalPlaces() {
        assertEquals(3, assertNotNull(Currency.forCode("KWD")).decimalPlaces)
    }

    @Test
    fun theSameCodeGivesTheSameCurrency() {
        assertSame(Currency.forCode("EUR"), Currency.forCode("EUR"))
    }

    @Test
    fun codesNotInTheListAreNotFound() {
        assertNull(Currency.forCode("ABC"), "an unknown code")
        assertNull(Currency.forCode("gbp"), "a code in lower case")
        assertNull(Currency.forCode("HRK"), "a withdrawn currency")
        assertNull(Currency.forCode("CLF"), "a fund code")
        assertNull(Currency.forCode("XAU"), "a precious metal")
        assertNull(Currency.forCode("XDR"), "a special code")
    }

    @Test
    fun recentAdditionsToTheListArePresent() {
        assertNotNull(Currency.forCode("ZWG"))
        assertNotNull(Currency.forCode("XCG"))
    }

    @Test
    fun everyCodeIsThreeCapitalLettersAndAppearsOnce() {
        val codes = Currency.all.map { it.code }
        assertTrue(codes.all { code -> code.length == 3 && code.all { it in 'A'..'Z' } })
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(codes.sorted(), codes)
    }

    @Test
    fun decimalPlacesAreZeroTwoOrThree() {
        assertTrue(Currency.all.all { it.decimalPlaces in setOf(0, 2, 3) })
    }
}
