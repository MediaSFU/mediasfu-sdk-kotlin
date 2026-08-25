package com.mediasfu.sdk.ui.mediasfu

import kotlin.test.Test
import kotlin.test.assertEquals

class ContainerFractionsTest {
    @Test
    fun `first class fractions fill missing legacy style values`() {
        val resolved = ContainerStyleOptions().withContainerFractions(0.6f, 0.7f)

        assertEquals(0.6f, resolved.widthFraction)
        assertEquals(0.7f, resolved.heightFraction)
    }

    @Test
    fun `explicit legacy style fractions take precedence`() {
        val resolved = ContainerStyleOptions(widthFraction = 0.4f, heightFraction = 0.5f)
            .withContainerFractions(0.8f, 0.9f)

        assertEquals(0.4f, resolved.widthFraction)
        assertEquals(0.5f, resolved.heightFraction)
    }

    @Test
    fun `first class fractions are clamped to parent bounds`() {
        val resolved = ContainerStyleOptions().withContainerFractions(2f, -1f)

        assertEquals(1f, resolved.widthFraction)
        assertEquals(0f, resolved.heightFraction)
    }
}
