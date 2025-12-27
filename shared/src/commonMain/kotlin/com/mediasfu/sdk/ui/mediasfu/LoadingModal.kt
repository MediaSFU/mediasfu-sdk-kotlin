package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Loading Modal - Displays loading indicator overlay
 *
 * Features:
 * - Full screen semi-transparent backdrop
 * - Centered progress indicator
 * - Blocks user interaction while loading
 */

@Composable
fun LoadingModal(state: MediasfuGenericState) {
    val isLoading = state.isLoading.value
    val modalsVis = state.modals.isLoadingVisible
    val paramsVis = state.parameters.isLoadingModalVisible
    val shouldShow = isLoading || modalsVis || paramsVis

    // Diagnostic: log once per recomposition when visible to identify sticky flag
    if (shouldShow) {
    }

    if (!shouldShow) return

    DefaultLoadingModalContent(state.createLoadingModalProps())
}

@Composable
fun DefaultLoadingModalContent(props: com.mediasfu.sdk.ui.components.display.LoadingModalOptions) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(props.backgroundColor)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color(props.indicatorColor))
            
            props.message?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = Color(props.displayColor),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
