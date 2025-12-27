package com.mediasfu.sdk.ui.components.cohost

import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import kotlin.collections.buildMap

/**
 * CoHostModal - Modal for managing co-host settings.
 *
 * Displays options to select a co-host and assign/manage responsibilities.
 *
 * @property options Configuration options for the co-host modal
 */
data class CoHostResponsibility(
    val name: String,
    var value: Boolean = false,
    var dedicated: Boolean = false,
)

data class ModifyCoHostSettingsOptions(
    val roomName: String,
    val socket: SocketManager?,
    val showAlert: ShowAlert?,
    val selectedParticipant: String, // The newly selected co-host
    val coHost: String, // The current co-host before change
    val coHostResponsibility: List<CoHostResponsibility>,
    val updateCoHost: (String) -> Unit,
    val updateCoHostResponsibility: (List<CoHostResponsibility>) -> Unit,
    val updateIsCoHostModalVisible: (Boolean) -> Unit,
)

data class CoHostModalOptions(
    val isCoHostModalVisible: Boolean = false,
    val onCoHostClose: () -> Unit,
    val onModifyCoHostSettings: (ModifyCoHostSettingsOptions) -> Unit = { /* default implementation */ },
    val currentCohost: String = "No coHost",
    val participants: List<Participant> = emptyList(),
    val coHostResponsibility: List<CoHostResponsibility> = emptyList(),
    val position: String = "topRight",
    val backgroundColor: Int = 0xFFB3D6ED.toInt(),
    val roomName: String,
    val showAlert: ShowAlert? = null,
    val updateCoHostResponsibility: (List<CoHostResponsibility>) -> Unit,
    val updateCoHost: (String) -> Unit,
    val updateIsCoHostModalVisible: (Boolean) -> Unit,
    val socket: SocketManager?,
)

interface CoHostModal : MediaSfuUIComponent {
    val options: CoHostModalOptions
    override val id: String get() = "cohost_modal"
    override val isVisible: Boolean get() = options.isCoHostModalVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    /**
     * State management for selected co-host
     */
    var selectedCohost: String
    var coHostResponsibilityCopy: MutableList<CoHostResponsibility>
    var responsibilities: MutableMap<String, Boolean>
    
    /**
     * Initializes the responsibilities map based on the list of responsibilities
     */
    fun initializeResponsibilities(responsibilitiesList: List<CoHostResponsibility>): MutableMap<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        for (responsibility in responsibilitiesList) {
            val capitalizedName = responsibility.name.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            }
            map["manage$capitalizedName"] = responsibility.value
            map["dedicateToManage$capitalizedName"] = responsibility.dedicated
        }
        return map
    }
    
    /**
     * Handles toggling of switches for responsibilities
     */
    fun handleToggleSwitch(key: String) {
        responsibilities[key] = !responsibilities[key]!!
        updateResponsibilityList(key)
    }
    
    /**
     * Updates the co-host responsibility list based on the toggled key
     */
    fun updateResponsibilityList(key: String) {
        val responsibilityName: String
        val newValue: Boolean?
        
        when {
            key.startsWith("dedicateToManage") -> {
                responsibilityName = key.replace("dedicateToManage", "").lowercase()
                newValue = responsibilities[key]
                setCoHostResponsibilityValue(responsibilityName, dedicated = newValue)
            }
            key.startsWith("manage") -> {
                responsibilityName = key.replace("manage", "").lowercase()
                newValue = responsibilities[key]
                setCoHostResponsibilityValue(responsibilityName, value = newValue)
                if (newValue == false) {
                    // If manage is turned off, also turn off dedicateToManage
                    val dedicatedKey = "dedicateToManage${responsibilityName.replaceFirstChar { 
                        if (it.isLowerCase()) it.titlecase() else it.toString() 
                    }}"
                    responsibilities[dedicatedKey] = false
                    setCoHostResponsibilityValue(responsibilityName, dedicated = false)
                }
            }
        }
    }
    
    /**
     * Updates the co-host responsibility list based on changes
     */
    fun setCoHostResponsibilityValue(
        name: String, 
        value: Boolean? = null, 
        dedicated: Boolean? = null
    ) {
        val index = coHostResponsibilityCopy.indexOfFirst { it.name == name }
        if (index != -1) {
            if (value != null) coHostResponsibilityCopy[index].value = value
            if (dedicated != null) coHostResponsibilityCopy[index].dedicated = dedicated
        }
    }
    
    /**
     * Handles saving co-host settings
     */
    fun handleSaveCoHost() {
        options.onModifyCoHostSettings(
            ModifyCoHostSettingsOptions(
                roomName = options.roomName,
                socket = options.socket,
                showAlert = options.showAlert,
                selectedParticipant = selectedCohost, // Newly selected co-host
                coHost = options.currentCohost, // Current co-host before change
                coHostResponsibility = coHostResponsibilityCopy,
                updateCoHost = options.updateCoHost,
                updateCoHostResponsibility = options.updateCoHostResponsibility,
                updateIsCoHostModalVisible = options.updateIsCoHostModalVisible
            )
        )
    }
    
    /**
     * Filters participants eligible to be co-host (excludes host)
     */
    fun getEligibleParticipants(): List<Participant> {
        return options.participants.filter { participant ->
            participant.islevel != "2" // Exclude host
        }
    }
}

/**
 * Default implementation of CoHostModal
 */
class DefaultCoHostModal(
    override val options: CoHostModalOptions
) : CoHostModal {
    override var selectedCohost: String = options.currentCohost
    override var coHostResponsibilityCopy: MutableList<CoHostResponsibility> = 
        options.coHostResponsibility.toMutableList()
    override var responsibilities: MutableMap<String, Boolean> = 
        initializeResponsibilities(options.coHostResponsibility)
    
    fun render(): Map<String, Any> {
        return buildMap {
            put("type", "coHostModal")
            put("isVisible", options.isCoHostModalVisible)
            put("onClose", options.onCoHostClose)
            put("selectedCohost", selectedCohost)
            put("coHostResponsibility", coHostResponsibilityCopy)
            put("responsibilities", responsibilities)
            put("eligibleParticipants", getEligibleParticipants())
            put("position", options.position)
            put("backgroundColor", options.backgroundColor)
            put("onToggleSwitch", { key: String -> handleToggleSwitch(key) })
            put("onSave", { handleSaveCoHost() })
            put("onSelectCohost", { cohost: String -> selectedCohost = cohost })
        }
    }
}
