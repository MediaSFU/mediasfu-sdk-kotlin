package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertNotNull

class ModernMediasfuGenericHeadTest {
    @Test
    fun headRendererIsAStateDrivenComposable() {
        val renderer: @Composable (MediasfuGenericState, Modifier, Boolean?) -> Unit =
            ::ModernMediasfuGenericHead

        assertNotNull(renderer)
    }
}
