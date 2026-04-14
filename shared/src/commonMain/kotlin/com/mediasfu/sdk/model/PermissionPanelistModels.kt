package com.mediasfu.sdk.model

/** Lightweight permission config payload holder. */
data class PermissionConfig(
    val values: Map<String, Any?> = emptyMap()
)

data class PermissionUpdatedData(
    val newLevel: String,
    val message: String? = null
)

data class PermissionConfigUpdatedData(
    val config: PermissionConfig
)

data class PermissionUpdatedOptions(
    val data: PermissionUpdatedData,
    val showAlert: ShowAlert? = null,
    val updateIslevel: ((String) -> Unit)? = null
)

data class PermissionConfigUpdatedOptions(
    val data: PermissionConfigUpdatedData,
    val updatePermissionConfig: ((PermissionConfig) -> Unit)? = null
)

typealias PermissionUpdatedType = suspend (PermissionUpdatedOptions) -> Unit
typealias PermissionConfigUpdatedType = suspend (PermissionConfigUpdatedOptions) -> Unit

data class PanelistData(
    val id: String,
    val name: String
) {
    fun toParticipant(): Participant = Participant(
        id = id,
        name = name,
        audioID = "",
        videoID = ""
    )
}

data class PanelistsUpdatedData(
    val panelists: List<PanelistData>
)

data class PanelistFocusChangedData(
    val focusEnabled: Boolean,
    val panelists: List<PanelistData>,
    val muteOthersMic: Boolean,
    val muteOthersCamera: Boolean
)

data class PanelistControlMediaData(
    val type: String,
    val action: String,
    val reason: String? = null
)

data class AddedAsPanelistData(
    val message: String = "You have been added as a panelist"
)

data class RemovedFromPanelistsData(
    val message: String = "You have been removed from panelists"
)

data class PanelistsUpdatedOptions(
    val data: PanelistsUpdatedData,
    val updatePanelists: ((List<Participant>) -> Unit)? = null
)

data class PanelistFocusChangedOptions(
    val data: PanelistFocusChangedData,
    val updatePanelistsFocused: ((Boolean) -> Unit)? = null,
    val updateMuteOthersMic: ((Boolean) -> Unit)? = null,
    val updateMuteOthersCamera: ((Boolean) -> Unit)? = null,
    val updatePanelists: ((List<Participant>) -> Unit)? = null,
    val currentPanelistsFocused: Boolean? = null,
    val currentPanelists: List<Participant>? = null,
    val onScreenChanges: (suspend () -> Unit)? = null
)

data class PanelistControlMediaOptions(
    val data: PanelistControlMediaData,
    val showAlert: ShowAlert? = null,
    val clickAudio: (() -> Unit)? = null,
    val clickVideo: (() -> Unit)? = null,
    val audioAlreadyOn: Boolean = false,
    val videoAlreadyOn: Boolean = false
)

data class AddedAsPanelistOptions(
    val data: AddedAsPanelistData,
    val showAlert: ShowAlert? = null
)

data class RemovedFromPanelistsOptions(
    val data: RemovedFromPanelistsData,
    val showAlert: ShowAlert? = null
)

typealias PanelistsUpdatedType = suspend (PanelistsUpdatedOptions) -> Unit
typealias PanelistFocusChangedType = suspend (PanelistFocusChangedOptions) -> Unit
typealias PanelistControlMediaType = suspend (PanelistControlMediaOptions) -> Unit
typealias AddedAsPanelistType = suspend (AddedAsPanelistOptions) -> Unit
typealias RemovedFromPanelistsType = suspend (RemovedFromPanelistsOptions) -> Unit
