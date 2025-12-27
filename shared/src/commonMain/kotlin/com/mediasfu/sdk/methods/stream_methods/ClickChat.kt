package com.mediasfu.sdk.methods.stream_methods

import com.mediasfu.sdk.model.ShowAlert

/**
 * Type definition for updating the chat modal's visibility.
 */
typealias UpdateIsMessagesModalVisible = (Boolean) -> Unit

/**
 * Options for the `clickChat` function.
 */
data class ClickChatOptions(
    val isMessagesModalVisible: Boolean,
    val updateIsMessagesModalVisible: UpdateIsMessagesModalVisible,
    val chatSetting: String,
    val islevel: String,
    val showAlert: ShowAlert? = null
)

/**
 * Type definition for ClickChat function.
 */
typealias ClickChatType = (ClickChatOptions) -> Unit

/**
 * Toggles the visibility of the chat modal based on the current state and event settings.
 * 
 * - If the modal is already visible, it will be closed.
 * - If the modal is not visible, it checks whether chat is allowed based on the event settings and participant level.
 * - If chat is not allowed, an alert will be shown.
 * 
 * ### Example Usage:
 * ```kotlin
 * clickChat(
 *     options = ClickChatOptions(
 *         isMessagesModalVisible = false,
 *         updateIsMessagesModalVisible = { isVisible -> setIsMessagesModalVisible(isVisible) },
 *         chatSetting = "allow",
 *         islevel = "1",
 *         showAlert = { message, type, duration -> showAlertFunction(message, type, duration) }
 *     )
 * )
 * ```
 */
fun clickChat(options: ClickChatOptions) {
    val isMessagesModalVisible = options.isMessagesModalVisible
    val updateIsMessagesModalVisible = options.updateIsMessagesModalVisible
    val chatSetting = options.chatSetting
    val islevel = options.islevel
    val showAlert = options.showAlert
    
    if (isMessagesModalVisible) {
        // Close the chat modal if it's currently visible
        updateIsMessagesModalVisible(false)
    } else {
        // Check if chat is allowed based on the chat setting and participant level
        if (chatSetting != "allow" && islevel != "2") {
            updateIsMessagesModalVisible(false)
            showAlert?.invoke(
                message = "Chat is disabled for this event.",
                type = "danger",
                duration = 3000
            )
        } else {
            updateIsMessagesModalVisible(true)
        }
    }
}
