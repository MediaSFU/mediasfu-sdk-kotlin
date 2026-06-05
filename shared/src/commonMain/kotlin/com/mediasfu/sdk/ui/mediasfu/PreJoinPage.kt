package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasfu.sdk.methods.utils.*
import androidx.compose.runtime.mutableStateMapOf
import com.mediasfu.sdk.socket.ConnectLocalSocketOptions
import com.mediasfu.sdk.socket.CreateJoinLocalRoomResponse
import com.mediasfu.sdk.socket.CreateLocalRoomOptions
import com.mediasfu.sdk.socket.CreateLocalRoomParameters
import com.mediasfu.sdk.socket.JoinEventRoomOptions
import com.mediasfu.sdk.socket.JoinEventRoomParameters
import com.mediasfu.sdk.socket.ResponseLocalConnectionData
import com.mediasfu.sdk.socket.SocketEmitException
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.socket.connectLocalSocket
import com.mediasfu.sdk.socket.createLocalRoom
import com.mediasfu.sdk.socket.createSocketManager
import com.mediasfu.sdk.socket.defaultMeetingRoomParams
import com.mediasfu.sdk.socket.joinEventRoom
import kotlinx.coroutines.launch
import com.mediasfu.sdk.util.MediaSFURuntimeProbe
import com.mediasfu.sdk.EngineParameterAdapters
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlin.coroutines.resume
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreJoinPage(state: MediasfuGenericState) {
    val options = state.options
    val parameters = state.parameters
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isCreateMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var eventID by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("conference") }
    var error by remember { mutableStateOf("") }

    var pending by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // Use a ref to prevent race conditions - state updates are async but ref is immediate
    val hasCheckedProceedRef = remember { mutableStateOf(false) }

    var localConnected by remember { mutableStateOf(false) }
    var localData by remember { mutableStateOf<ResponseLocalConnectionData?>(null) }
    var initSocket by remember { mutableStateOf<SocketManager?>(null) }

    val pendingCache = remember { mutableStateMapOf<String, Long>() }

    val isLocalCE = remember(options.localLink) {
        val result = options.localLink.isNotBlank() && !options.localLink.contains("mediasfu.com", ignoreCase = true)
        result
    }

    val eventTypes = listOf("chat", "broadcast", "webinar", "conference")

    fun normalizeEventType(raw: String?): String = raw?.lowercase()?.takeIf { it.isNotBlank() } ?: "conference"

    fun randomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (0 until length).joinToString("") { chars.random().toString() }
    }

    fun generateSecureCode(): String = randomString(12) + randomString(12)

    fun generateEventId(): String {
        val now = Clock.System.now()
        // Use base-30 encoding like Flutter (produces alphanumeric: 0-9 and a-t)
        val timePart = now.toEpochMilliseconds().toString(30)
        val nanoPart = now.nanosecondsOfSecond.toString(30)
        val randomDigits = Random.nextInt(10, 100)
        return "m$timePart$nanoPart$randomDigits"
    }

    fun dismissPreJoinKeyboard() {
        expanded = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun Map<String, Any?>.string(key: String): String? = when (val value = this[key]) {
        is String -> value
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> null
    }

    fun Map<String, Any?>.int(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.trim().toDoubleOrNull()?.toInt()
        else -> null
    }

    fun Map<String, Any?>.bool(key: String): Boolean? = when (val value = this[key]) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", true) || value == "1"
        else -> null
    }

    fun Map<String, Any?>.long(key: String): Long? = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }

    fun mapToCreateOptions(map: Map<String, Any>?): CreateMediaSFURoomOptions? {
        map ?: return null
        val action = map.string("action") ?: "create"
        val durationValue = map.int("duration") ?: map.int("durationMinutes")
        val capacityValue = map.int("capacity")
        val userNameValue = map.string("userName") ?: map.string("username")
        val eventTypeValue = normalizeEventType(map.string("eventType"))
        return CreateMediaSFURoomOptions(
            action = action,
            duration = durationValue ?: 0,
            capacity = capacityValue ?: 0,
            userName = userNameValue ?: "",
            scheduledDate = map.long("scheduledDate"),
            secureCode = map.string("secureCode"),
            eventType = eventTypeValue,
            recordOnly = map.bool("recordOnly") ?: map.bool("record_only"),
            safeRoom = map.bool("safeRoom"),
            autoStartSafeRoom = map.bool("autoStartSafeRoom"),
            safeRoomAction = map.string("safeRoomAction"),
            dataBuffer = map.bool("dataBuffer"),
            bufferType = map.string("bufferType")
        )
    }

    fun mapToJoinOptions(map: Map<String, Any>?): JoinMediaSFURoomOptions? {
        map ?: return null
        val meeting = map.string("meetingID") ?: map.string("roomName") ?: map.string("eventID")
        val user = map.string("userName") ?: map.string("username")
        return JoinMediaSFURoomOptions(
            action = map.string("action") ?: "join",
            meetingID = meeting ?: "",
            userName = user ?: "",
            adminPasscode = map.string("adminPasscode"),
            islevel = map.string("islevel") ?: "0"
        )
    }

    fun isThrottled(key: String, timeoutMs: Long = 30_000L): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        val last = pendingCache[key]
        if (last != null && now - last < timeoutMs) return true
        pendingCache[key] = now
        scope.launch {
            delay(timeoutMs)
            pendingCache.remove(key)
        }
        return false
    }

    suspend fun connectLocalIfNeeded() {
        if (!isLocalCE || localConnected) return
        val socket = createSocketManager()
        val result = connectLocalSocket(
            ConnectLocalSocketOptions(
                socket = socket,
                link = options.localLink
            )
        )
        result.onSuccess { response ->
            localData = response.data
            initSocket = response.socket
            localConnected = true
            state.connectivity.updateLocalSocket(response.socket)
        }.onFailure { throwable ->
            state.showAlert("Unable to connect to ${options.localLink}. ${throwable.message ?: ""}", "danger", 3000)
        }
    }

    suspend fun createRoomOnLocalServer(createData: CreateLocalRoomParameters, link: String?) {
        val socket = initSocket
        if (socket == null) {
            error = "Local socket is not connected."
            return
        }
        createLocalRoom(CreateLocalRoomOptions(socket = socket, parameters = createData)).onSuccess { res ->
            if (res.success) {
                // Use the secret from createLocalRoom response
                // Don't call joinEventRoom here - createRoom already sets up peers[socket.id]
                // This matches React/Flutter behavior where no joinRoom is emitted after createRoom
                val resolvedSecret = res.secret.takeIf { it.isNotBlank() } ?: createData.secureCode
                state.connectivity.updateSocket(socket)
                state.connectivity.updateLocalSocket(socket)
                state.room.updateApiUserName(localData?.apiUserName ?: "")
                state.room.updateApiToken(resolvedSecret)
                state.room.updateLink(link ?: options.localLink)
                state.room.updateRoomName(createData.eventId)
                // Room creator is always host (islevel="2")
                state.room.updateMember(createData.userName)
                state.room.updateIslevel("2")
                state.room.updateAdminPasscode(resolvedSecret)
                state.updateValidated(true)
            } else {
                error = "Unable to create room. ${res.reason ?: ""}".trim()
            }
        }.onFailure { throwable ->
            error = "Unable to create room. ${throwable.message ?: "Unknown error"}"
        }
    }

    suspend fun connectAndValidateCloudRoom(
        roomName: String,
        socketSecret: String,
        memberName: String,
        islevel: String,
        link: String,
        adminPasscode: String
    ): Boolean {
        // The shared state observer can prefetch sockets whenever credentials change.
        // Pause that path during the cloud room handoff so the explicit join flow owns
        // the first connection attempt with the resolved room credentials.
        state.suspendCredentialSocketPrefetch()
        state.room.updateApiUserName(roomName)
        state.room.updateApiToken(socketSecret)
        state.room.updateLink(link)
        state.room.updateRoomName(roomName)
        state.room.updateMember(memberName)
        state.room.updateIslevel(islevel)
        state.room.updateAdminPasscode(adminPasscode)

        return suspendCancellableCoroutine { continuation ->
            state.connectAndValidate(
                roomName = roomName,
                member = memberName,
                adminPasscode = adminPasscode,
                islevel = islevel,
                apiUserName = roomName,
                apiToken = socketSecret,
                showLoadingModal = false
            ) { success ->
                if (continuation.isActive) {
                    continuation.resume(success)
                }
            }
        }
    }

    suspend fun handleCreateRoom(auto: Boolean = false, createOverride: CreateMediaSFURoomOptions? = null) {
        if (pending) return
        pending = true
        error = ""

        val resolvedOverride = createOverride ?: if (auto && !options.returnUI) mapToCreateOptions(options.noUIPreJoinOptionsCreate) else null

        if (options.returnUI && resolvedOverride == null) {
            if (name.isBlank() || duration.isBlank() || capacity.isBlank() || eventType.isBlank()) {
                error = "Please fill all the fields."
                pending = false
                return
            }
            if (!eventTypes.contains(eventType.lowercase())) {
                error = "Invalid event type. Please select from Chat, Broadcast, Webinar, or Conference."
                pending = false
                return
            }
            val capacityInt = capacity.toIntOrNull()
            val durationInt = duration.toIntOrNull()
            if (capacityInt == null || capacityInt <= 0) {
                error = "Room capacity must be a positive integer."
                pending = false
                return
            }
            if (durationInt == null || durationInt <= 0) {
                error = "Duration must be a positive integer."
                pending = false
                return
            }
            if (name.length !in 2..10) {
                error = "Display Name must be between 2 and 10 characters."
                pending = false
                return
            }
        } else if (!options.returnUI && resolvedOverride == null) {
            error = "No UI PreJoin Options are missing."
            pending = false
            return
        }

        state.showLoader()
        // Yield so Compose can render the LoadingModal before any heavy work begins.
        yield()

        try {
            if (isLocalCE) {
                connectLocalIfNeeded()
                if (!localConnected) {
                    error = "Unable to connect to ${options.localLink}."
                    return
                }

                val durationInt = resolvedOverride?.duration ?: duration.toIntOrNull()
                val capacityInt = resolvedOverride?.capacity ?: capacity.toIntOrNull()
                val userNameValue = resolvedOverride?.userName ?: name
                val eventValue = normalizeEventType(resolvedOverride?.eventType ?: eventType)

                val eventRoomParams = (localData?.eventRoomParams ?: defaultMeetingRoomParams()).copy(type = eventValue)

                val createData = CreateLocalRoomParameters(
                    eventId = generateEventId(),
                    duration = durationInt ?: 0,
                    capacity = capacityInt ?: 0,
                    userName = userNameValue,
                    scheduledDateIso = Clock.System.now().toString(),
                    secureCode = generateSecureCode(),
                    waitRoom = false,
                    recordingParams = localData?.recordingParams,
                    eventRoomParams = eventRoomParams,
                    videoPreference = null,
                    audioPreference = null,
                    audioOutputPreference = null,
                    mediasfuURL = ""
                )

                val shouldCallCloud = options.connectMediaSFU && !localData?.apiUserName.isNullOrBlank() && !localData?.apiKey.isNullOrBlank()

                if (shouldCallCloud) {
                    val roomIdentifier = "local_create_${userNameValue}_${durationInt}_${capacityInt}"
                    val pendingKey = "prejoin_pending_$roomIdentifier"
                    if (isThrottled(pendingKey)) {
                        error = "Room creation already in progress"
                        return
                    }

                    val payload = (resolvedOverride ?: CreateMediaSFURoomOptions(
                        action = "create",
                        duration = durationInt ?: 0,
                        capacity = capacityInt ?: 0,
                        userName = userNameValue,
                        eventType = eventValue,
                        recordOnly = true
                    )).copy(
                        eventType = eventValue,
                        recordOnly = true
                    )

                    val response = options.createMediaSFURoom(
                        CreateMediaSFUOptions(
                            payload = payload,
                            apiUserName = localData?.apiUserName ?: "",
                            apiKey = localData?.apiKey ?: "",
                            localLink = options.localLink
                        )
                    )

                    if (response.success && response.data is CreateJoinRoomResponse) {
                        val data = response.data as CreateJoinRoomResponse
                        state.room.updateApiUserName(data.roomName)
                        state.room.updateApiToken(data.secret)
                        state.room.updateLink(data.link)
                        state.room.updateRoomName(data.roomName)
                        state.room.updateMember(userNameValue)
                        state.room.updateIslevel("2")
                        state.room.updateAdminPasscode(data.secureCode?.takeIf { it.isNotBlank() } ?: data.secret)
                        checkLimitsAndMakeRequest(
                            CheckLimitsAndMakeRequestOptions(
                                apiUserName = data.roomName,
                                apiToken = data.secret,
                                link = data.link,
                                userName = userNameValue,
                                parameters = EngineParameterAdapters.checkLimitsAndMakeRequestParameters(
                                    parameters,
                                    connectSocket = { user, token, link, _ ->
                                        state.openSocket(link, user, token, null)
                                    }
                                ),
                                validate = false
                            )
                        )

                        val enrichedData = createData.copy(
                            eventId = data.roomName,
                            secureCode = data.secureCode ?: createData.secureCode,
                            mediasfuURL = data.publicURL
                        )

                        createRoomOnLocalServer(enrichedData, data.link)
                    } else if (!response.success && response.data is CreateJoinRoomError) {
                        val err = response.data as CreateJoinRoomError
                        error = "Unable to create room on MediaSFU. ${err.error}"
                    } else {
                        error = "Unable to create room on MediaSFU."
                    }
                } else {
                    createRoomOnLocalServer(createData, options.localLink)
                }
            } else {
                val durationInt = resolvedOverride?.duration ?: duration.toIntOrNull()
                val capacityInt = resolvedOverride?.capacity ?: capacity.toIntOrNull()
                val userNameValue = resolvedOverride?.userName ?: name
                val eventValue = normalizeEventType(resolvedOverride?.eventType ?: eventType)

                val payload = (resolvedOverride ?: CreateMediaSFURoomOptions(
                    action = "create",
                    duration = durationInt ?: 0,
                    capacity = capacityInt ?: 0,
                    userName = userNameValue,
                    eventType = eventValue
                )).copy(eventType = eventValue)

                val roomIdentifier = "mediasfu_create_${userNameValue}_${durationInt}_${capacityInt}"
                val pendingKey = "prejoin_pending_$roomIdentifier"
                if (isThrottled(pendingKey)) {
                    error = "Room creation already in progress"
                    return
                }

                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "pre-rest",
                    "",
                    "create-user=${userNameValue},event=${eventValue}"
                )

                val response = options.createMediaSFURoom(
                    CreateMediaSFUOptions(
                        payload = payload,
                        apiUserName = options.credentials?.apiUserName ?: "",
                        apiKey = options.credentials?.apiKey ?: "",
                        localLink = options.localLink
                    )
                )

                if (response.success && response.data is CreateJoinRoomResponse) {
                    val data = response.data as CreateJoinRoomResponse
                    MediaSFURuntimeProbe.recordConsumerSignalStage(
                        "rest-ok",
                        "",
                        "create-room=${data.roomName},link=${data.link.takeLast(20)}"
                    )
                    val adminPasscode = data.secureCode?.takeIf { it.isNotBlank() } ?: data.secret
                    MediaSFURuntimeProbe.recordConsumerSignalStage("handoff", "", "create-cloud-generic")
                    val connected = connectAndValidateCloudRoom(
                        roomName = data.roomName,
                        socketSecret = data.secret,
                        memberName = userNameValue,
                        islevel = "2",
                        link = data.link,
                        adminPasscode = adminPasscode
                    )
                    if (connected) {
                        MediaSFURuntimeProbe.recordConsumerSignalStage("join-ok", "", "create")
                    } else {
                        MediaSFURuntimeProbe.recordConsumerSignalStage("join-fail", "", "create-cloud-generic")
                        error = "Unable to create room. Media connection failed."
                    }
                } else if (!response.success && response.data is CreateJoinRoomError) {
                    val err = response.data as CreateJoinRoomError
                    MediaSFURuntimeProbe.recordConsumerSignalStage("rest-fail", "", err.error.take(50))
                    error = "Unable to create room. ${err.error}"
                } else {
                    MediaSFURuntimeProbe.recordConsumerSignalStage("rest-unexpected", "", "create-success=${response.success}")
                    error = "Unexpected error occurred."
                }
            }
        } catch (e: SocketEmitException) {
            error = "Unable to create room. ${e.message ?: "Unknown error"}"
        } catch (e: Exception) {
            error = "Unable to create room. ${e.message ?: "Unknown error"}"
        } finally {
            pending = false
            state.hideLoader()
        }
    }

    suspend fun handleJoinRoom(auto: Boolean = false, joinOverride: JoinMediaSFURoomOptions? = null) {
        if (pending) return
        pending = true
        error = ""

        val resolvedOverride = joinOverride ?: if (auto && !options.returnUI) mapToJoinOptions(options.noUIPreJoinOptionsJoin) else null

        if (options.returnUI && resolvedOverride == null) {
            if (name.isBlank() || eventID.isBlank()) {
                error = "Please fill all the fields."
                pending = false
                return
            }

            if (name.length !in 2..10) {
                error = "Display Name must be between 2 and 10 characters."
                pending = false
                return
            }
        } else if (!options.returnUI && resolvedOverride == null) {
            error = "No UI PreJoin Options are missing."
            pending = false
            return
        }

        state.showLoader()
        // Yield so Compose can render the LoadingModal before any heavy work begins.
        yield()

        try {
            if (isLocalCE) {
                connectLocalIfNeeded()
                if (!localConnected) {
                    error = "Unable to connect to ${options.localLink}."
                    return
                }

                val nameValue = resolvedOverride?.userName ?: name
                val eventValue = resolvedOverride?.meetingID ?: eventID

                val secureCodeForJoin = resolvedOverride?.adminPasscode?.trim().orEmpty().ifBlank {
                    localData?.raw?.get("secureCode") as? String ?: ""
                }

                val joinParams = JoinEventRoomParameters(
                    eventId = eventValue,
                    userName = nameValue,
                    secureCode = secureCodeForJoin,
                    videoPreference = null,
                    audioPreference = null,
                    audioOutputPreference = null
                )

                val socket = initSocket
                if (socket == null) {
                    error = "Local socket is not connected."
                    return
                }

                joinEventRoom(JoinEventRoomOptions(socket = socket, parameters = joinParams)).onSuccess { res ->
                    if (res.success) {
                        val resolvedSecret = res.secret.takeIf { it.isNotBlank() } ?: secureCodeForJoin
                        state.connectivity.updateSocket(socket)
                        state.connectivity.updateLocalSocket(socket)
                        state.room.updateApiUserName(localData?.apiUserName ?: "")
                        state.room.updateApiToken(resolvedSecret)
                        state.room.updateLink(options.localLink)
                        state.room.updateRoomName(eventValue)
                        state.room.updateMember(nameValue)
                        state.room.updateAdminPasscode(resolvedSecret)
                        state.updateValidated(true)
                    } else {
                        error = "Unable to join room. ${res.reason ?: ""}".trim()
                    }
                }.onFailure { throwable ->
                    error = "Unable to join room. ${throwable.message ?: "Unknown error"}"
                }
            } else {
                val nameValue = resolvedOverride?.userName ?: name
                val payload = (resolvedOverride ?: JoinMediaSFURoomOptions(
                    action = "join",
                    meetingID = eventID,
                    userName = nameValue
                ))

                MediaSFURuntimeProbe.recordConsumerSignalStage("pre-rest", "", "room=${payload.meetingID},user=${nameValue}")

                val response = options.joinMediaSFURoom(
                    JoinMediaSFUOptions(
                        payload = payload,
                        apiUserName = options.credentials?.apiUserName ?: "",
                        apiKey = options.credentials?.apiKey ?: "",
                        localLink = options.localLink
                    )
                )

                if (response.success && response.data is CreateJoinRoomResponse) {
                    val data = response.data as CreateJoinRoomResponse
                    MediaSFURuntimeProbe.recordConsumerSignalStage("rest-ok", "", "room=${data.roomName},link=${data.link.takeLast(20)}")
                    val joinLevel = (resolvedOverride?.islevel ?: "0").ifBlank { "0" }
                    val adminPasscode = resolvedOverride?.adminPasscode?.takeIf { it.isNotBlank() } ?: data.secureCode?.takeIf { it.isNotBlank() } ?: ""
                    MediaSFURuntimeProbe.recordConsumerSignalStage("handoff", "", "join-cloud-generic")
                    val connected = connectAndValidateCloudRoom(
                        roomName = data.roomName,
                        socketSecret = data.secret,
                        memberName = nameValue,
                        islevel = joinLevel,
                        link = data.link,
                        adminPasscode = adminPasscode
                    )
                    if (connected) {
                        MediaSFURuntimeProbe.recordConsumerSignalStage("join-ok", "", "")
                    } else {
                        MediaSFURuntimeProbe.recordConsumerSignalStage("join-fail", "", "join-cloud-generic")
                        error = "Unable to join room. Media connection failed."
                    }
                } else if (!response.success && response.data is CreateJoinRoomError) {
                    val err = response.data as CreateJoinRoomError
                    MediaSFURuntimeProbe.recordConsumerSignalStage("rest-fail", "", err.error.take(50))
                    error = "Unable to join room. ${err.error}"
                } else {
                    MediaSFURuntimeProbe.recordConsumerSignalStage("rest-unexpected", "", "success=${response.success}")
                    error = "Unexpected error occurred."
                }
            }
        } catch (e: SocketEmitException) {
            MediaSFURuntimeProbe.recordDeviceLoadError("socket-exc:${e.message.orEmpty().take(50)}")
            error = "Unable to join room. ${e.message ?: "Unknown error"}"
        } catch (e: Exception) {
            MediaSFURuntimeProbe.recordDeviceLoadError("exc:${e.message.orEmpty().take(50)}")
            error = "Unable to join room. ${e.message ?: "Unknown error"}"
        } finally {
            pending = false
            state.hideLoader()
        }
    }

    suspend fun checkProceed() {
        // Prevent multiple checkProceed calls - guard check
        // In Compose's LaunchedEffect, we're already in single-threaded context
        if (hasCheckedProceedRef.value || pending) {
            return
        }
        hasCheckedProceedRef.value = true
        
        try {
            if (!options.returnUI && (options.noUIPreJoinOptionsCreate != null || options.noUIPreJoinOptionsJoin != null)) {
                val createOpt = mapToCreateOptions(options.noUIPreJoinOptionsCreate)
                val joinOpt = mapToJoinOptions(options.noUIPreJoinOptionsJoin)
                when {
                    createOpt?.action?.lowercase() == "create" -> handleCreateRoom(auto = true, createOverride = createOpt)
                    joinOpt?.action?.lowercase() == "join" -> handleJoinRoom(auto = true, joinOverride = joinOpt)
                    else -> error = "Invalid options provided for creating/joining a room without UI."
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Unexpected error"
        }
    }

    LaunchedEffect(isLocalCE, options.localLink) {
        if (isLocalCE && !localConnected) {
            connectLocalIfNeeded()
        }
    }

    LaunchedEffect(options.returnUI, options.noUIPreJoinOptionsCreate, options.noUIPreJoinOptionsJoin, localConnected, isLocalCE) {
        if (!options.returnUI && (options.noUIPreJoinOptionsCreate != null || options.noUIPreJoinOptionsJoin != null)) {
            if (!hasCheckedProceedRef.value && (!isLocalCE || localConnected)) {
                checkProceed()
            } else if (hasCheckedProceedRef.value) {
            } else {
            }
        }
    }

    // Color palette (matches React/Flutter dark theme)
    val bgGradient = Brush.linearGradient(colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B)))
    val logoRingGradient = Brush.linearGradient(colors = listOf(Color(0xFF818CF8), Color(0xFF60A5FA), Color(0xFF22D3EE)))
    val buttonGradient = Brush.linearGradient(colors = listOf(Color(0xFF818CF8), Color(0xFF60A5FA)))
    val glassBackground = Color(0x0FFFFFFF)
    val glassBorder = Color(0x14FFFFFF)
    val inputBackground = Color(0x14FFFFFF)
    val inputBorder = Color(0x33FFFFFF)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xB3FFFFFF)
    val textMuted = Color(0x80FFFFFF)
    val switchBorder = Color(0x80818CF8)
    val dangerColor = Color(0xFFFC8181)

    val inputTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        focusedLabelColor = textSecondary,
        unfocusedLabelColor = textMuted,
        focusedBorderColor = Color(0xFF818CF8),
        unfocusedBorderColor = inputBorder,
        cursorColor = textPrimary,
        focusedContainerColor = inputBackground,
        unfocusedContainerColor = inputBackground,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgGradient)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                dismissPreJoinKeyboard()
            },
        contentAlignment = Alignment.Center
    ) {
        if (!options.returnUI) return@Box

        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Glassmorphic container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = glassBackground, shape = RoundedCornerShape(24.dp))
                    .border(width = 1.dp, color = glassBorder, shape = RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Logo with gradient ring
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(brush = logoRingGradient, shape = CircleShape)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://mediasfu.com/images/logo192.png",
                            contentDescription = "MediaSFU Logo",
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Title
                    Text(
                        text = if (isCreateMode) "Create a Room" else "Join a Room",
                        color = textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Subtitle
                    Text(
                        text = if (isCreateMode)
                            "Start a new session with your audience."
                        else
                            "Enter the meeting ID to connect.",
                        color = textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Display Name input
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Display Name (2-10 characters)", color = textMuted, fontSize = 14.sp) },
                        singleLine = true,
                        colors = inputTextFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isCreateMode) {
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            placeholder = { Text("Duration (minutes)", color = textMuted, fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = inputTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = capacity,
                            onValueChange = { capacity = it },
                            placeholder = { Text("Capacity (max participants)", color = textMuted, fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = inputTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Event Type Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = eventType.replaceFirstChar { it.uppercase() },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select Event Type", color = textMuted, fontSize = 14.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = inputTextFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                eventTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            eventType = type
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = eventID,
                            onValueChange = { eventID = it },
                            placeholder = { Text("Meeting ID", color = textMuted, fontSize = 14.sp) },
                            singleLine = true,
                            colors = inputTextFieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }



                    if (error.isNotEmpty()) {
                        Text(
                            text = error,
                            color = dangerColor,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Gradient action button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(brush = if (!pending) buttonGradient else Brush.linearGradient(listOf(Color(0xFF818CF8).copy(alpha = 0.5f), Color(0xFF60A5FA).copy(alpha = 0.5f))))
                            .clickable(
                                enabled = !pending,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch {
                                    if (isCreateMode) handleCreateRoom() else handleJoinRoom()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (pending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isCreateMode) "Create Room" else "Join Room",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // OR divider
                    Text(
                        text = "OR",
                        color = textMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Switch mode button (border only, no fill)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(width = 1.dp, color = switchBorder, shape = RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isCreateMode = !isCreateMode; error = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCreateMode) "Switch to Join Mode" else "Switch to Create Mode",
                            color = Color(0xFF818CF8),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
