package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.TranslationVoiceCatalogue
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.socket.fetchTranslationVoices

internal class TranslationVoicePickerState(language: String) {
    var catalogue by mutableStateOf(TranslationVoiceCatalogue(language = language))
        private set
    var loading by mutableStateOf(false)
        private set
    var selectedId by mutableStateOf<String?>(null)
        private set

    fun begin() { loading = true; selectedId = null }
    fun receive(value: TranslationVoiceCatalogue) { catalogue = value; loading = false; selectedId = null }
    fun select(id: String?) { selectedId = id?.takeIf { catalogue.selectionConfig(it) != null && !loading } }
    fun selectionConfig(): Map<String, Any?>? = if (loading) null else catalogue.selectionConfig(selectedId)
}

@Composable
internal fun rememberTranslationVoicePicker(
    socket: SocketManager?,
    roomName: String,
    language: String,
    configuration: Any?,
    enabled: Boolean
): TranslationVoicePickerState {
    // A new room, language, connection or provider/model configuration invalidates selection immediately.
    val picker = remember(socket, socket?.id, roomName, language, configuration, enabled) { TranslationVoicePickerState(language) }
    LaunchedEffect(picker, enabled) {
        picker.select(null)
        if (enabled && socket != null) {
            picker.begin()
            picker.receive(fetchTranslationVoices(socket, roomName, language))
        }
    }
    return picker
}

@Composable
internal fun TranslationStockVoicePicker(picker: TranslationVoicePickerState) {
    var expanded by remember(picker) { mutableStateOf(false) }
    val catalogue = picker.catalogue
    val selected = catalogue.voices.firstOrNull { it.id == picker.selectedId }
    Column {
        Text("Stock voice", style = MaterialTheme.typography.labelMedium)
        if (picker.loading) {
            Text("Loading voices…", style = MaterialTheme.typography.bodySmall)
        } else if (catalogue.voices.isEmpty()) {
            Text(if (catalogue.status == "empty") "No stock voices available for this language." else "Voice catalogue unavailable. Try reopening voice settings.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(listOfNotNull(catalogue.provider, catalogue.model).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Box {
                Surface(modifier = Modifier.fillMaxWidth().clickable { expanded = true }) {
                    Text(selected?.let { "${it.name} · ${it.gender}" } ?: "Select a voice", modifier = Modifier.padding(12.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                    catalogue.voices.forEach { voice ->
                        DropdownMenuItem(text = { Text("${voice.name} · ${voice.gender}") }, onClick = { picker.select(voice.id); expanded = false })
                    }
                }
            }
            if (catalogue.truncated) Text("The provider returned a limited voice list.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
