package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WelcomePage - Initial landing page for joining a MediaSFU room
 * Extracted from MediasfuGeneric.kt to match React/Flutter component structure
 */
@Composable
internal fun PreJoinOrWelcome(state: MediasfuGenericState) {
    val options = state.options
    val credentials = options.credentials
    val hasCredentials = credentials != null && credentials.apiKey.isNotEmpty()

    // If a custom preJoinPageWidget is provided, use it
    val customPreJoin = options.preJoinPageWidget
    if (customPreJoin != null) {
        customPreJoin.invoke(state)
        return
    }

    // If credentials are provided, show the built-in PreJoinPage
    if (hasCredentials) {
        PreJoinPage(state)
        return
    }

    // If noUI options are provided (CE-only mode with auto-create/join), use PreJoinPage
    // This enables the noUI auto-flow even without cloud credentials
    val hasNoUIOptions = options.noUIPreJoinOptionsCreate != null || options.noUIPreJoinOptionsJoin != null
    if (hasNoUIOptions && options.localLink.isNotBlank()) {
        PreJoinPage(state)
        return
    }

    // Otherwise show the simple WelcomePage
    WelcomePage(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomePage(state: MediasfuGenericState) {
    val room = state.room
    val options = state.options

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to MediaSFU",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = room.roomName,
                onValueChange = room::updateRoomName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Room Name") }
            )

            OutlinedTextField(
                value = room.member,
                onValueChange = room::updateMember,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display Name") }
            )

            OutlinedTextField(
                value = room.adminPasscode,
                onValueChange = room::updateAdminPasscode,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Admin Passcode") }
            )

            OutlinedTextField(
                value = room.islevel,
                onValueChange = room::updateIslevel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("User Level") }
            )

            OutlinedTextField(
                value = room.apiUserName,
                onValueChange = room::updateApiUserName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Username") }
            )

            OutlinedTextField(
                value = room.apiToken,
                onValueChange = room::updateApiToken,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Token") }
            )

            Button(
                onClick = {
                    state.connectAndValidate(
                        roomName = room.roomName,
                        member = room.member,
                        adminPasscode = room.adminPasscode,
                        islevel = room.islevel,
                        apiUserName = room.apiUserName.ifBlank { options.credentials?.apiUserName ?: "" },
                        apiToken = room.apiToken
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Join Room", fontSize = 18.sp)
            }
        }
    }
}
