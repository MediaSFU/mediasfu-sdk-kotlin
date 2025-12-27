package com.mediasfu.sample

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import java.net.URI
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.MediaSfuEngine
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcFactory
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import com.mediasfu.sdk.methods.utils.CreateJoinRoomError
import com.mediasfu.sdk.methods.utils.CreateJoinRoomResponse
import com.mediasfu.sdk.methods.utils.CreateJoinRoomResult
import com.mediasfu.sdk.methods.utils.CreateMediaSFUOptions
import com.mediasfu.sdk.methods.utils.CreateMediaSFURoomOptions
import com.mediasfu.sdk.methods.utils.JoinMediaSFUOptions
import com.mediasfu.sdk.methods.utils.JoinMediaSFURoomOptions
import com.mediasfu.sdk.methods.utils.createRoomOnMediaSfu
import com.mediasfu.sdk.methods.utils.joinRoomOnMediaSfu
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.socket.CreateJoinLocalRoomResponse
import com.mediasfu.sdk.socket.CreateLocalRoomParameters
import com.mediasfu.sdk.socket.JoinEventRoomParameters
import com.mediasfu.sdk.socket.ResponseLocalConnectionData
import com.mediasfu.sdk.socket.defaultMeetingRoomParams
import com.mediasfu.sdk.socket.defaultRecordingParams
import com.mediasfu.sdk.model.Credentials
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import com.mediasfu.sdk.ui.mediasfu.PreJoinPage
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlin.random.Random
import androidx.core.content.ContextCompat

private const val DEFAULT_CLOUD_SOCKET_BASE = "https://mediasfu.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Pre-create notification channel for screen capture (makes screen share start faster)
        ScreenCaptureService.ensureChannelExists(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MediaSfuSampleScreen()
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaSfuSampleScreen() {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val engine = remember(context) {
        MediaSfuEngine(deviceProvider = {
            runCatching { WebRtcFactory.createDevice(context) }
                .onSuccess { println("MediaSFU - initializeDevice: device provided during remember") }
                .onFailure { error ->
                    println("MediaSFU - initializeDevice: provider failed -> ${error.message}")
                }
                .getOrNull()
        })
    }
    val engineGreeting = remember(engine) { engine.greet() }
    val engineParameters = remember(engine) { engine.getParameters() }

    var pendingAudioPermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var pendingCameraPermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var pendingScreenCapturePermission by remember { mutableStateOf<CompletableDeferred<Map<String, Any?>?>?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        engineParameters.hasAudioPermission = granted
        pendingAudioPermission?.complete(granted)
        pendingAudioPermission = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        engineParameters.hasCameraPermission = granted
        pendingCameraPermission?.complete(granted)
        pendingCameraPermission = null
    }

    // Screen capture permission launcher for MediaProjection
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val permissionData = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            mapOf<String, Any?>(
                "resultCode" to result.resultCode,
                "data" to result.data
            )
        } else {
            null
        }
        pendingScreenCapturePermission?.complete(permissionData)
        pendingScreenCapturePermission = null
    }

    LaunchedEffect(context) {
        // Ensure WebRTC device initializes with the Android context so media capture works on device
        engine.initializeDevice(context)

        engineParameters.hasAudioPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        engineParameters.hasCameraPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        engine.initializeDevice(context)

        engineParameters.requestPermissionAudio = suspend {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                engineParameters.hasAudioPermission = true
                true
            } else {
                val deferred = CompletableDeferred<Boolean>()
                pendingAudioPermission?.cancel()
                pendingAudioPermission = deferred
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                deferred.await()
            }
        }

        engineParameters.requestPermissionCamera = suspend {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                engineParameters.hasCameraPermission = true
                true
            } else {
                val deferred = CompletableDeferred<Boolean>()
                pendingCameraPermission?.cancel()
                pendingCameraPermission = deferred
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                deferred.await()
            }
        }
        
        // Screen capture permission callback for MediaProjection
        engineParameters.requestScreenCapturePermission = suspend {
            // Start foreground service BEFORE requesting permission
            // This prevents the app from being killed while the permission dialog is shown
            ScreenCaptureService.start(context)
            
            val deferred = CompletableDeferred<Map<String, Any?>?>()
            pendingScreenCapturePermission?.cancel()
            pendingScreenCapturePermission = deferred
            
            // Get MediaProjectionManager and launch the permission request
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
            
            val result = deferred.await()
            
            // If permission was denied, stop the foreground service
            if (result == null) {
                ScreenCaptureService.stop(context)
            }
            
            result
        }
        
        // Callback to stop screen capture service when screen share stops
        engineParameters.stopScreenCaptureService = {
            ScreenCaptureService.stop(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingAudioPermission?.cancel()
            pendingCameraPermission?.cancel()
            pendingScreenCapturePermission?.cancel()
            engineParameters.requestPermissionAudio = null
            engineParameters.requestPermissionCamera = null
            engineParameters.requestScreenCapturePermission = null
        }
    }

    var apiUserName by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable {
        mutableStateOf("")
    }
    var localLink by rememberSaveable { mutableStateOf("") }
    var connectMediaSfu by rememberSaveable { mutableStateOf(true) }
    var actionChoice by rememberSaveable { mutableStateOf(ActionMode.CREATE) }
    var userName by rememberSaveable { mutableStateOf("") }
    var roomName by rememberSaveable { mutableStateOf("mediasfu-demo") }
    var meetingId by rememberSaveable { mutableStateOf("") }
    var durationMinutes by rememberSaveable { mutableStateOf("5") }
    var capacity by rememberSaveable { mutableStateOf("4") }
    var adminPasscode by rememberSaveable { mutableStateOf("") }
    var isLevel by rememberSaveable { mutableStateOf("0") }
    var eventType by remember { mutableStateOf(EventType.CONFERENCE) }

    var callState by remember { mutableStateOf<CallState>(CallState.Idle) }
    var mediasfuOptions by remember {
        mutableStateOf<MediasfuGenericOptions?>(null)
    }
    var activeSocketBaseUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // If we already have enough connection info, start on the SDK PreJoin flow.
        // This mirrors the React/Flutter behavior where PreJoin is the default entry UI.
        val trimmedApiUser = apiUserName.trim()
        val trimmedApiKey = apiKey.trim()
        val trimmedLocalLink = localLink.trim()
        val currentIsLevel = isLevel.trim().ifEmpty { "0" }

        val canAutoLaunch = when {
            connectMediaSfu -> trimmedApiUser.isNotEmpty() && trimmedApiKey.length == 64
            else -> trimmedLocalLink.isNotEmpty()
        }

        if (canAutoLaunch && mediasfuOptions == null) {
            engineParameters.apiUserName = trimmedApiUser
            engineParameters.apiKey = trimmedApiKey
            engineParameters.link = trimmedLocalLink
            engineParameters.localSocket = null
            engineParameters.roomName = ""
            engineParameters.member = ""
            engineParameters.adminPasscode = ""
            engineParameters.islevel = currentIsLevel
            engineParameters.eventType = eventType
            engineParameters.youAreHost = false
            engineParameters.youAreCoHost = false
            engineParameters.validated = false

            val resolvedCredentials = if (connectMediaSfu) {
                Credentials(apiUserName = trimmedApiUser, apiKey = trimmedApiKey)
            } else {
                null
            }

            mediasfuOptions = MediasfuGenericOptions(
                preJoinPageWidget = { sdkState -> PreJoinPage(sdkState) },
                localLink = trimmedLocalLink,
                connectMediaSFU = connectMediaSfu,
                credentials = resolvedCredentials,
                sourceParameters = engineParameters
            )
            callState = CallState.Idle
        }
    }

    suspend fun ensureCloudSocketConnected(socketBaseUrl: String): Boolean {
        val normalizedBase = socketBaseUrl.ifBlank { DEFAULT_CLOUD_SOCKET_BASE }
        val alreadyConnected = engine.isConnected() &&
            activeSocketBaseUrl?.equals(normalizedBase, ignoreCase = true) == true

        if (!alreadyConnected && engine.isConnected()) {
            try {
                engine.disconnect()
            } catch (error: Throwable) {
                println("MediaSFU - ensureCloudSocketConnected: disconnect failed -> ${error.message}")
            }
            activeSocketBaseUrl = null
        }

        if (!alreadyConnected) {
            try {
                engine.connect(normalizedBase)
            } catch (error: Throwable) {
                val reason = error.message ?: "Unknown error"
                callState = CallState.Failure("Unable to connect to MediaSFU Cloud ($normalizedBase): $reason")
                activeSocketBaseUrl = null
                return false
            }
        }

        engineParameters.socket = engine.socketManager()
        engineParameters.localSocket = null
        activeSocketBaseUrl = normalizedBase
        return true
    }

    suspend fun launchMediaSfuCloudSession(
        response: CreateJoinRoomResponse,
        requestedRoomName: String,
        memberName: String,
        socketSecret: String,
        adminSecureCode: String,
        isHost: Boolean,
        isLevelValue: String,
        apiUser: String,
        apiKeyValue: String,
        currentEventType: EventType,
        linkFallback: String
    ): Boolean {
        if (!connectMediaSfu) {
            callState = CallState.Failure("Enable MediaSFU Cloud to launch this room")
            return false
        }

        val resolvedLink = response.link.ifBlank { response.publicURL }.ifBlank { linkFallback }
        val resolvedRoomName = response.roomName.ifBlank { requestedRoomName }.trim()
        println(
            "MediaSFU - launchMediaSfuCloudSession: response.roomName='${response.roomName}' requestedRoomName='$requestedRoomName' resolvedRoomName='$resolvedRoomName' response.link='${response.link}'"
        )
        if (resolvedLink.isEmpty()) {
            callState = CallState.Failure("MediaSFU Cloud did not return a join link")
            return false
        }

        if (resolvedRoomName.isBlank()) {
            callState = CallState.Failure("MediaSFU Cloud did not return a room name")
            return false
        }

        val socketBaseUrl = resolveSocketBaseUrl(resolvedLink)
        if (!ensureCloudSocketConnected(socketBaseUrl)) {
            return false
        }

        val socketUserName = resolvedRoomName
        val socketCredential = when {
            socketSecret.isNotBlank() -> socketSecret
            apiKeyValue.isNotBlank() -> apiKeyValue
            else -> ""
        }

        engineParameters.apiUserName = socketUserName
        engineParameters.apiKey = socketCredential
        engineParameters.link = resolvedLink
        engineParameters.localSocket = null
        engineParameters.roomName = resolvedRoomName
        engineParameters.member = memberName
        engineParameters.adminPasscode = adminSecureCode
        engineParameters.islevel = isLevelValue
        engineParameters.eventType = currentEventType
        engineParameters.youAreHost = isHost
        engineParameters.youAreCoHost = !isHost && isLevelValue == "1"
        engineParameters.validated = true

        mediasfuOptions = MediasfuGenericOptions(
            localLink = resolvedLink,
            connectMediaSFU = true,
            credentials = Credentials(
                apiUserName = socketUserName,
                apiKey = socketCredential
            ),
            sourceParameters = engineParameters
        )

        callState = CallState.Idle
        return true
    }


    if (mediasfuOptions != null) {
        BackHandler {
            coroutineScope.launch {
                try {
                    engine.disconnect()
                } catch (error: Throwable) {
                    println("MediaSFU - BackHandler disconnect failed -> ${error.message}")
                }
            }
            engineParameters.validated = false
            mediasfuOptions = null
            activeSocketBaseUrl = null
            callState = CallState.Idle
        }

        MediasfuGeneric(
            options = mediasfuOptions!!,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "MediaSFU Kotlin Sample") }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            Text(
                text = engineGreeting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            SectionTitle(text = "Credentials")
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiUserName,
                onValueChange = { apiUserName = it },
                label = { Text("API Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key (64 chars)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = localLink,
                onValueChange = { localLink = it },
                label = { Text("Community Edition Server (optional)") },
                placeholder = { Text("http://localhost:3000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Use MediaSFU Cloud",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = connectMediaSfu,
                    onCheckedChange = { connectMediaSfu = it }
                )
            }

            HorizontalDivider()

            SectionTitle(text = "Session Setup")
            ActionSelector(
                current = actionChoice,
                onActionSelected = { actionChoice = it }
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Display Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            if (actionChoice == ActionMode.CREATE) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = capacity,
                    onValueChange = { capacity = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Capacity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
            } else {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = meetingId,
                    onValueChange = { meetingId = it },
                    label = { Text("Meeting ID") },
                    placeholder = { Text("Enter existing room ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = adminPasscode,
                onValueChange = { adminPasscode = it },
                label = { Text("Admin Passcode") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = isLevel,
                onValueChange = { isLevel = it.filter { ch -> ch.isDigit() } },
                label = { Text("Access Level (islevel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )

            EventTypeSelector(
                selected = eventType,
                onSelected = { eventType = it }
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = callState !is CallState.Loading,
                onClick = {
                    coroutineScope.launch {
                        callState = CallState.Loading
                        val trimmedApiUser = apiUserName.trim()
                        val trimmedApiKey = apiKey.trim()
                        val trimmedLocalLink = localLink.trim()
                        val trimmedUserName = userName.trim()
                        val trimmedPasscode = adminPasscode.trim()
                        val currentIsLevel = isLevel.trim().ifEmpty { "0" }

                        val validationError = when {
                            connectMediaSfu && trimmedApiUser.isEmpty() -> "API username is required when using MediaSFU Cloud"
                            connectMediaSfu && trimmedApiKey.length != 64 -> "API key must be exactly 64 characters when using MediaSFU Cloud"
                            !connectMediaSfu && trimmedLocalLink.isEmpty() -> "Provide your MediaSFU Community Edition link or enable MediaSFU Cloud"
                            trimmedUserName.isEmpty() -> "Display name is required"
                            // Room/Meeting fields are handled by the SDK PreJoinPage.
                            else -> null
                        }

                        if (validationError != null) {
                            callState = CallState.Failure(validationError)
                            return@launch
                        }

                        // Mirror the React examples (PrejoinPage={PreJoinPage}):
                        // - Mount MediasfuGeneric with credentials/localLink
                        // - Keep validated=false so the SDK shows the PreJoinPage
                        // - Let the SDK handle create/join flows
                        engineParameters.apiUserName = trimmedApiUser
                        engineParameters.apiKey = trimmedApiKey
                        engineParameters.link = trimmedLocalLink
                        engineParameters.localSocket = null
                        engineParameters.roomName = ""
                        engineParameters.member = ""
                        engineParameters.adminPasscode = ""
                        engineParameters.islevel = currentIsLevel
                        engineParameters.eventType = eventType
                        engineParameters.youAreHost = false
                        engineParameters.youAreCoHost = false
                        engineParameters.validated = false

                        val resolvedCredentials = if (connectMediaSfu) {
                            Credentials(apiUserName = trimmedApiUser, apiKey = trimmedApiKey)
                        } else {
                            null
                        }

                        mediasfuOptions = MediasfuGenericOptions(
                            preJoinPageWidget = { sdkState -> PreJoinPage(sdkState) },
                            localLink = trimmedLocalLink,
                            connectMediaSFU = connectMediaSfu,
                            credentials = resolvedCredentials,
                            sourceParameters = engineParameters
                        )
                        callState = CallState.Idle
                        return@launch

                        try {
                            if (actionChoice == ActionMode.CREATE) {
                                // When creating a room, the user is always the host (islevel = "2")
                                val hostIsLevel = "2"
                                
                                val duration = durationMinutes.toIntOrNull() ?: run {
                                    callState = CallState.Failure("Duration must be a number")
                                    return@launch
                                }
                                val maxCapacity = capacity.toIntOrNull() ?: run {
                                    callState = CallState.Failure("Capacity must be a number")
                                    return@launch
                                }

                                if (trimmedLocalLink.isNotEmpty() && !trimmedLocalLink.contains("mediasfu.com")) {
                                    val connectResult = engine.connectLocal(trimmedLocalLink)
                                    if (connectResult.isFailure) {
                                        val message = connectResult.exceptionOrNull()?.message ?: "Unable to connect to local server"
                                        callState = CallState.Failure(message)
                                        return@launch
                                    }

                                    val connectionData = connectResult.getOrNull()?.data?.takeUnless {
                                        it == ResponseLocalConnectionData.Empty
                                    } ?: engine.latestLocalConnectionData()

                                    val baseMeetingParams = connectionData?.eventRoomParams ?: defaultMeetingRoomParams()
                                    // Set the eventType in meetingParams - matches React reference behavior
                                    val meetingParams = baseMeetingParams.copy(type = eventType.name.lowercase())
                                    
                                    val recordingParams = connectionData?.recordingParams ?: defaultRecordingParams()
                                    val waitRoomPreference = connectionData?.mode?.equals("wait", ignoreCase = true) ?: false
                                    val resolvedLocalLink = connectionData?.mediasfuURL?.takeIf { it.isNotBlank() } ?: trimmedLocalLink
                                    activeSocketBaseUrl = resolveSocketBaseUrl(resolvedLocalLink)

                                    val eventId = generateEventId()
                                    val secureCode = generateSecureCode()
                                    val localParams = CreateLocalRoomParameters(
                                        eventId = eventId,
                                        duration = duration,
                                        capacity = maxCapacity,
                                        userName = trimmedUserName,
                                        scheduledDateIso = Instant.now().toString(),
                                        secureCode = secureCode,
                                        waitRoom = waitRoomPreference,
                                        recordingParams = recordingParams,
                                        eventRoomParams = meetingParams,
                                        videoPreference = null,
                                        audioPreference = null,
                                        audioOutputPreference = null,
                                        mediasfuURL = resolvedLocalLink
                                    )

                                    val createResult = engine.createLocalRoom(localParams)
                                    createResult.fold(
                                        onSuccess = { response ->
                                            val baseSecret = response.secret.takeIf { it.isNotEmpty() } ?: secureCode
                                            val joinResult = engine.joinEventRoom(
                                                JoinEventRoomParameters(
                                                    eventId = eventId,
                                                    userName = trimmedUserName,
                                                    secureCode = baseSecret,
                                                    videoPreference = null,
                                                    audioPreference = null,
                                                    audioOutputPreference = null
                                                )
                                            )

                                            joinResult.fold(
                                                onSuccess = { joinResponse ->
                                                    val resolvedSecret = joinResponse.secret.takeIf { it.isNotEmpty() } ?: baseSecret
                                                    engineParameters.apiUserName = trimmedApiUser
                                                    engineParameters.apiKey = trimmedApiKey
                                                    engineParameters.link = resolvedLocalLink
                                                    engineParameters.roomName = eventId
                                                    engineParameters.member = trimmedUserName
                                                    engineParameters.adminPasscode = resolvedSecret
                                                    engineParameters.islevel = hostIsLevel  // Always "2" for room creator
                                                    engineParameters.eventType = eventType
                                                    engineParameters.youAreHost = true
                                                    engineParameters.youAreCoHost = false
                                                    engineParameters.validated = true

                                                    mediasfuOptions = MediasfuGenericOptions(
                                                        localLink = resolvedLocalLink,
                                                        connectMediaSFU = false,
                                                        credentials = Credentials(
                                                            apiUserName = engineParameters.apiUserName,
                                                            apiKey = engineParameters.apiKey
                                                        ),
                                                        sourceParameters = engineParameters
                                                    )
                                                    callState = CallState.Idle
                                                },
                                                onFailure = { joinError ->
                                                    callState = CallState.Failure(joinError.message ?: "Unable to join newly created room")
                                                }
                                            )
                                        },
                                        onFailure = { error ->
                                            callState = CallState.Failure(error.message ?: "Unable to create local room")
                                        }
                                    )
                                    return@launch
                                }

                                val options = CreateMediaSFUOptions(
                                    payload = CreateMediaSFURoomOptions(
                                        action = "create",
                                        duration = duration,
                                        capacity = maxCapacity,
                                        userName = trimmedUserName,
                                        secureCode = trimmedPasscode.ifEmpty { "admin123" },
                                        eventType = eventType.name.lowercase(),
                                        roomName = roomName.trim(),
                                        adminPasscode = trimmedPasscode.ifEmpty { "admin123" },
                                        islevel = hostIsLevel
                                    ),
                                    apiUserName = trimmedApiUser,
                                    apiKey = trimmedApiKey,
                                    localLink = trimmedLocalLink
                                )

                                val result = createRoomOnMediaSfu(options)
                                val response = result.data as? CreateJoinRoomResponse
                                val enteredRoom = if (result.success && response != null) {
                                    val adminSecureCode = response.secureCode?.takeIf { it.isNotBlank() }
                                        ?: trimmedPasscode.ifEmpty { "admin123" }
                                    val socketSecret = response.secret.takeIf { it.isNotBlank() }
                                        ?: adminSecureCode

                                    launchMediaSfuCloudSession(
                                        response = response,
                                        requestedRoomName = roomName.trim(),
                                        memberName = trimmedUserName,
                                        socketSecret = socketSecret,
                                        adminSecureCode = adminSecureCode,
                                        isHost = true,
                                        isLevelValue = hostIsLevel,
                                        apiUser = trimmedApiUser,
                                        apiKeyValue = trimmedApiKey,
                                        currentEventType = eventType,
                                        linkFallback = trimmedLocalLink
                                    )
                                } else {
                                    false
                                }

                                if (!enteredRoom && callState !is CallState.Failure) {
                                    callState = result.toCallState(ActionMode.CREATE)
                                }
                            } else {
                                if (trimmedLocalLink.isNotEmpty() && !trimmedLocalLink.contains("mediasfu.com")) {
                                    val connectResult = engine.connectLocal(trimmedLocalLink)
                                    if (connectResult.isFailure) {
                                        val message = connectResult.exceptionOrNull()?.message ?: "Unable to connect to local server"
                                        callState = CallState.Failure(message)
                                        return@launch
                                    }

                                    val connectionData = connectResult.getOrNull()?.data?.takeUnless {
                                        it == ResponseLocalConnectionData.Empty
                                    } ?: engine.latestLocalConnectionData()
                                    val resolvedLocalLink = connectionData?.mediasfuURL?.takeIf { it.isNotBlank() } ?: trimmedLocalLink
                                    activeSocketBaseUrl = resolveSocketBaseUrl(resolvedLocalLink)
                                    val secureCodeForJoin = trimmedPasscode.ifEmpty {
                                        connectionData?.raw?.get("secureCode") as? String ?: ""
                                    }

                                    val targetMeetingId = meetingId.trim()
                                    val joinParams = JoinEventRoomParameters(
                                        eventId = targetMeetingId,
                                        userName = trimmedUserName,
                                        secureCode = secureCodeForJoin,
                                        videoPreference = null,
                                        audioPreference = null,
                                        audioOutputPreference = null
                                    )

                                    val joinResult = engine.joinEventRoom(joinParams)
                                    joinResult.fold(
                                        onSuccess = { response ->
                                            val resolvedSecret = response.secret.takeIf { it.isNotEmpty() } ?: secureCodeForJoin
                                            engineParameters.apiUserName = trimmedApiUser
                                            engineParameters.apiKey = trimmedApiKey
                                            engineParameters.link = resolvedLocalLink
                                            engineParameters.roomName = targetMeetingId
                                            engineParameters.member = trimmedUserName
                                            engineParameters.adminPasscode = resolvedSecret
                                            engineParameters.islevel = currentIsLevel
                                            engineParameters.eventType = eventType
                                            engineParameters.youAreHost = false
                                            engineParameters.youAreCoHost = false
                                            engineParameters.validated = true

                                            mediasfuOptions = MediasfuGenericOptions(
                                                localLink = resolvedLocalLink,
                                                connectMediaSFU = false,
                                                credentials = Credentials(
                                                    apiUserName = engineParameters.apiUserName,
                                                    apiKey = engineParameters.apiKey
                                                ),
                                                sourceParameters = engineParameters
                                            )
                                            callState = CallState.Idle
                                        },
                                        onFailure = { error ->
                                            callState = CallState.Failure(error.message ?: "Unable to join local room")
                                        }
                                    )
                                    return@launch
                                }

                                val options = JoinMediaSFUOptions(
                                    payload = JoinMediaSFURoomOptions(
                                        action = "join",
                                        meetingID = meetingId.trim(),
                                        userName = trimmedUserName,
                                        adminPasscode = trimmedPasscode.ifEmpty { null },
                                        islevel = currentIsLevel
                                    ),
                                    apiUserName = trimmedApiUser,
                                    apiKey = trimmedApiKey,
                                    localLink = trimmedLocalLink
                                )

                                val result = joinRoomOnMediaSfu(options)
                                val response = result.data as? CreateJoinRoomResponse
                                val enteredRoom = if (result.success && response != null) {
                                    val adminSecureCode = response.secureCode?.takeIf { it.isNotBlank() }
                                        ?: trimmedPasscode
                                    val socketSecret = response.secret.takeIf { it.isNotBlank() }
                                        ?: adminSecureCode

                                    launchMediaSfuCloudSession(
                                        response = response,
                                        requestedRoomName = meetingId.trim(),
                                        memberName = trimmedUserName,
                                        socketSecret = socketSecret,
                                        adminSecureCode = adminSecureCode,
                                        isHost = false,
                                        isLevelValue = currentIsLevel,
                                        apiUser = trimmedApiUser,
                                        apiKeyValue = trimmedApiKey,
                                        currentEventType = eventType,
                                        linkFallback = trimmedLocalLink
                                    )
                                } else {
                                    false
                                }

                                if (!enteredRoom && callState !is CallState.Failure) {
                                    callState = result.toCallState(ActionMode.JOIN)
                                }
                            }
                        } catch (error: Throwable) {
                            callState = CallState.Failure(error.message ?: "Unexpected error")
                        }
                    }
                }
            ) {
                Text(
                    text = if (actionChoice == ActionMode.CREATE) "Create Room" else "Join Room"
                )
            }

            when (val currentState = callState) {
                is CallState.Idle -> Unit
                is CallState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is CallState.Success -> {
                    SuccessCard(state = currentState)
                }

                is CallState.Failure -> {
                    ErrorCard(message = currentState.message)
                }
            }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun ActionSelector(current: ActionMode, onActionSelected: (ActionMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionMode.values().forEach { action ->
            FilterChip(
                selected = current == action,
                onClick = { onActionSelected(action) },
                label = { Text(action.label) }
            )
        }
    }
}

@Composable
private fun EventTypeSelector(selected: EventType, onSelected: (EventType) -> Unit) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EventType.values()
            .filter { it != EventType.NONE }
            .forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelected(type) },
                    label = { Text(type.label) }
                )
            }
    }
}

@Composable
private fun SuccessCard(state: CallState.Success) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (state.action == ActionMode.CREATE) "Room created successfully" else "Joined room successfully",
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Message: ${state.response.message}")
            Text(text = "Room: ${state.response.roomName}")
            state.response.secureCode?.let {
                Text(text = "Secure Code: $it")
            }
            Text(text = "Join Link: ${state.response.link}")
            Text(text = "Public URL: ${state.response.publicURL}")
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private enum class ActionMode(val label: String) {
    CREATE("Create Room"),
    JOIN("Join Room")
}

private sealed interface CallState {
    data object Idle : CallState
    data object Loading : CallState
    data class Success(val action: ActionMode, val response: CreateJoinRoomResponse) : CallState
    data class Failure(val message: String) : CallState
}

private fun CreateJoinRoomResult.toCallState(action: ActionMode): CallState {
    return if (success) {
        val response = data as? CreateJoinRoomResponse
        if (response != null) {
            CallState.Success(action, response)
        } else {
            CallState.Failure("Unexpected response payload")
        }
    } else {
        val error = data as? CreateJoinRoomError
        CallState.Failure(error?.error ?: "Request failed")
    }
}

private val EventType.label: String
    get() = name.lowercase().replaceFirstChar { it.titlecase() }

private fun CreateJoinLocalRoomResponse.toCreateJoinRoomResponse(
    action: ActionMode,
    eventId: String,
    link: String
): CreateJoinRoomResponse {
    val resolvedLink = url ?: link
    val resolvedSecureCode = secret.takeIf { it.isNotEmpty() }
    val message = if (action == ActionMode.CREATE) {
        "Local room created successfully"
    } else {
        "Joined local room successfully"
    }

    return CreateJoinRoomResponse(
        message = message,
        roomName = eventId,
        secureCode = resolvedSecureCode,
        publicURL = resolvedLink,
        link = resolvedLink,
        secret = secret,
        success = success
    )
}

private fun resolveSocketBaseUrl(link: String): String {
    val fallback = DEFAULT_CLOUD_SOCKET_BASE
    val trimmed = link.trim()
    if (trimmed.isEmpty()) {
        return fallback
    }

    return runCatching {
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return fallback
        val defaultPort = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        val portPart = if (uri.port != -1 && uri.port != defaultPort) {
            ":${uri.port}"
        } else {
            ""
        }
        "$scheme://$host$portPart"
    }.getOrElse {
        fallback
    }
}

private fun generateSecureCode(length: Int = 24): String {
    val characters = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString(length) {
        repeat(length) {
            append(characters[Random.nextInt(characters.length)])
        }
    }
}

private fun generateEventId(): String {
    val timePart = java.lang.Long.toString(System.currentTimeMillis(), 30)
    val nanoPart = java.lang.Long.toString(System.nanoTime() and 0xFFFFFFFFL, 30)
    val randomSuffix = Random.nextInt(100, 1000).toString()
    return "m$timePart$nanoPart$randomSuffix"
}
