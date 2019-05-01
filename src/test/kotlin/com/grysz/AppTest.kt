package com.grysz

import arrow.core.Option
import kotlin.test.Test
import kotlin.test.assertEquals

class AppTest {
    @Test
    fun testAppHasAGreeting() {
        assertEquals(Option.just("Hello world."), App().greeting)
    }
}
