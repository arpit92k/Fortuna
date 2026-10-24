package io.github.arpit92k.fortuna.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun greetingComesFromTheCore() {
        assertEquals("Hello from the Fortuna core", greeting())
    }
}
