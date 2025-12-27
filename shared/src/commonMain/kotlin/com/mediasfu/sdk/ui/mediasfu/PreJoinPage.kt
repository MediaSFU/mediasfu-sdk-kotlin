package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasfu.sdk.methods.utils.*
import androidx.compose.runtime.mutableStateMapOf
import com.mediasfu.sdk.socket.ConnectLocalSocketOptions
import com.mediasfu.sdk.socket.CreateJoinLocalRoomResponse
import com.mediasfu.sdk.socket.CreateLocalRoomOptions
import com.mediasfu.sdk.socket.CreateLocalRoomParameters
import com.mediasfu.sdk.socket.JoinEventRoomOptions
import com.mediasfu.sdk.socket.JoinEventRoomParameters
import com.mediasfu.sdk.socket.JoinRoomOptions
import com.mediasfu.sdk.socket.ResponseLocalConnectionData
import com.mediasfu.sdk.socket.SocketEmitException
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.socket.connectLocalSocket
import com.mediasfu.sdk.socket.createLocalRoom
import com.mediasfu.sdk.socket.createSocketManager
import com.mediasfu.sdk.socket.defaultMeetingRoomParams
import com.mediasfu.sdk.socket.joinEventRoom
import com.mediasfu.sdk.socket.joinRoom
import com.mediasfu.sdk.producer_client.UpdateRoomParametersClientOptions
import com.mediasfu.sdk.producer_client.updateRoomParametersClient
import kotlinx.coroutines.launch
import com.mediasfu.sdk.EngineParameterAdapters
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreJoinPage(state: MediasfuGenericState) {
    val options = state.options
    val parameters = state.parameters
    val scope = rememberCoroutineScope()

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
                        checkLimitsAndMakeRequest(
                            CheckLimitsAndMakeRequestOptions(
                                apiUserName = data.roomName,
                                apiToken = data.secret,
                                link = data.link,
                                userName = userNameValue,
                                parameters = EngineParameterAdapters.checkLimitsAndMakeRequestParameters(
                                    parameters,
                                    connectSocket = { user, token, link, _ ->
                                        state.openSocket(link, user, token, localData?.apiKey)
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
                    checkLimitsAndMakeRequest(
                        CheckLimitsAndMakeRequestOptions(
                            apiUserName = data.roomName,
                            apiToken = data.secret,
                            link = data.link,
                            userName = userNameValue,
                            parameters = EngineParameterAdapters.checkLimitsAndMakeRequestParameters(
                                parameters,
                                connectSocket = { user, token, link, _ ->
                                    state.openSocket(link, user, token, options.credentials?.apiKey)
                                }
                            )
                        )
                    )
                    
                    val socket = parameters.socket
                    if (socket != null) {
                        // Emit joinRoom to complete the join
                        val joinResult = joinRoom(
                            JoinRoomOptions(
                                socket = socket,
                                roomName = data.roomName,
                                islevel = "2", // Host level
                                member = userNameValue,
                                sec = data.secret,
                                apiUserName = data.roomName
                            )
                        )
                        
                        joinResult.onSuccess { joinResponse ->
                            state.room.updateApiUserName(options.credentials?.apiUserName ?: "")
                            state.room.updateApiToken(data.secret)
                            state.room.updateLink(data.link)
                            state.room.updateRoomName(data.roomName)
                            state.room.updateMember(userNameValue)
                            state.room.updateIslevel("2")
                            state.room.updateAdminPasscode(data.secureCode?.takeIf { it.isNotBlank() } ?: data.secret)
                            state.room.updateRoomData(joinResponse)
                            state.connectivity.updateSocket(socket)
                            state.connectivity.updateRoomResponse(joinResponse)
                            
                            updateRoomParametersClient(
                                UpdateRoomParametersClientOptions(
                                    parameters = state.createUpdateRoomParametersBridge()
                                )
                            )
                            
                            state.updateValidated(true)
                        }.onFailure { e ->
                            error = "Failed to create room: ${e.message}"
                        }
                    } else {
                        error = "Socket connection failed"
                    }
                } else if (!response.success && response.data is CreateJoinRoomError) {
                    val err = response.data as CreateJoinRoomError
                    error = "Unable to create room. ${err.error}"
                } else {
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
                    checkLimitsAndMakeRequest(
                        CheckLimitsAndMakeRequestOptions(
                            apiUserName = data.roomName,
                            apiToken = data.secret,
                            link = data.link,
                            userName = nameValue,
                            parameters = EngineParameterAdapters.checkLimitsAndMakeRequestParameters(
                                parameters,
                                connectSocket = { user, token, link, _ ->
                                    state.openSocket(link, user, token, options.credentials?.apiKey)
                                }
                            )
                        )
                    )
                    
                    val socket = parameters.socket
                    if (socket != null) {
                        // Emit joinRoom to complete the join
                        val joinResult = joinRoom(
                            JoinRoomOptions(
                                socket = socket,
                                roomName = data.roomName,
                                islevel = "0", // Participant level
                                member = nameValue,
                                sec = data.secret,
                                apiUserName = data.roomName
                            )
                        )
                        
                        joinResult.onSuccess { joinResponse ->
                            state.room.updateApiUserName(options.credentials?.apiUserName ?: "")
                            state.room.updateApiToken(data.secret)
                            state.room.updateLink(data.link)
                            state.room.updateRoomName(data.roomName)
                            state.room.updateMember(nameValue)
                            state.room.updateRoomData(joinResponse)
                            state.connectivity.updateSocket(socket)
                            state.connectivity.updateRoomResponse(joinResponse)
                            
                            updateRoomParametersClient(
                                UpdateRoomParametersClientOptions(
                                    parameters = state.createUpdateRoomParametersBridge()
                                )
                            )
                            
                            state.updateValidated(true)
                        }.onFailure { e ->
                            error = "Failed to join room: ${e.message}"
                        }
                    } else {
                        error = "Socket connection failed"
                    }
                } else if (!response.success && response.data is CreateJoinRoomError) {
                    val err = response.data as CreateJoinRoomError
                    error = "Unable to join room. ${err.error}"
                } else {
                    error = "Unexpected error occurred."
                }
            }
        } catch (e: SocketEmitException) {
            error = "Unable to join room. ${e.message ?: "Unknown error"}"
        } catch (e: Exception) {
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

    Scaffold(
        containerColor = Color(0xFF53C6E0)
    ) { paddingValues ->
        if (!options.returnUI) {
            return@Scaffold
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .width(320.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Logo
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = "https://mediasfu.com/images/logo192.png",
                            contentDescription = "MediaSFU Logo",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Inputs
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isCreateMode) {
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            label = { Text("Duration (minutes)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                label = { Text("Event Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
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

                        OutlinedTextField(
                            value = capacity,
                            onValueChange = { capacity = it },
                            label = { Text("Room Capacity") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = eventID,
                            onValueChange = { eventID = it },
                            label = { Text("Event ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (error.isNotEmpty()) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                if (isCreateMode) handleCreateRoom() else handleJoinRoom()
                            }
                        },
                        enabled = !pending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (pending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isCreateMode) "Create Room" else "Join Room")
                        }
                    }

                    Text("OR", fontWeight = FontWeight.Bold)

                    OutlinedButton(
                        onClick = { isCreateMode = !isCreateMode; error = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCreateMode) "Switch to Join Mode" else "Switch to Create Mode")
                    }
                }
            }
        }
    }
}
