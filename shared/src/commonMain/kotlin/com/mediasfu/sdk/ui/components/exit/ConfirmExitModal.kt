package com.mediasfu.sdk.ui.components.exit

import com.mediasfu.sdk.ui.*

/**
 * # ConfirmExitModal
 *
 * A modal widget that displays an exit confirmation dialog.
 * Useful for confirming an exit action, such as ending an event or allowing a user to leave.
 * For admin users (islevel '2'), displays a warning that the action will end the event for all.
 * Replicates the Flutter SDK's confirm_exit_modal.dart functionality.
 *
 * ## Features
 * - Exit confirmation dialog
 * - Different messages based on user permission level
 * - Admin warning for event termination
 * - Cancel and Confirm buttons
 * - Modal positioning
 * - Customizable background color
 *
 * ## Usage Example
 * ```kotlin
 * val options = ConfirmExitModalOptions(
 *     isVisible = true,
 *     onClose = { /* close modal */ },
 *     member = "user123",
 *     roomName = "eventRoom",
 *     islevel = "2",
 *     exitEventOnConfirm = { confirmExit(it) }
 * )
 *
 * val modal = DefaultConfirmExitModal(options)
 * val rendered = modal.render()
 * ```
 *
 * @property options Configuration options for the confirm exit modal
 */

/**
 * Options for the confirm exit operation
 */
data class ConfirmExitOptions(
    val member: String,
    val ban: Boolean,
    val socket: Any?,  // Socket connection
    val roomName: String
)

/**
 * Configuration options for the ConfirmExitModal component
 *
 * @property isVisible Whether the modal is visible
 * @property onClose Callback to close the modal
 * @property position Modal position on screen (topRight, topLeft, center, etc.)
 * @property backgroundColor Background color for the modal (hex string)
 * @property exitEventOnConfirm Function to execute on confirming the exit
 * @property member Identifier for the exiting user
 * @property ban Whether the user should be banned on exit
 * @property roomName Name of the room or event
 * @property socket Socket connection for sending exit commands
 * @property islevel User's permission level ('2' indicates admin rights)
 */
data class ConfirmExitModalOptions(
    val isVisible: Boolean,
    val onClose: () -> Unit,
    val position: String = "topRight",
    val backgroundColor: String = "#83C0E9",
    val exitEventOnConfirm: (ConfirmExitOptions) -> Unit = {},
    val member: String,
    val ban: Boolean = false,
    val roomName: String,
    val socket: Any? = null,
    val islevel: String
)

/**
 * Interface for the ConfirmExitModal component
 */
interface ConfirmExitModal : MediaSfuUIComponent {
    val options: ConfirmExitModalOptions
    override val id: String get() = "confirm_exit_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets the appropriate message based on user level
     *
     * @return The confirmation message
     */
    fun getConfirmationMessage(): String
    
    /**
     * Gets the appropriate button label based on user level
     *
     * @return The button label
     */
    fun getConfirmButtonLabel(): String
}

/**
 * Default implementation of ConfirmExitModal
 */
class DefaultConfirmExitModal(
    override val options: ConfirmExitModalOptions
) : ConfirmExitModal {
    
    override fun getConfirmationMessage(): String {
        return if (options.islevel == "2") {
            "This will end the event for all. Confirm exit."
        } else {
            "Are you sure you want to exit?"
        }
    }
    
    override fun getConfirmButtonLabel(): String {
        return if (options.islevel == "2") {
            "End Event"
        } else {
            "Exit"
        }
    }
    
    fun render(): Map<String, Any> {
        return mapOf(
            "type" to "confirmExitModal",
            "visible" to options.isVisible,
            "position" to options.position,
            "container" to mapOf(
                "width" to "400px",
                "maxWidth" to "70%",
                "padding" to "20px",
                "margin" to "50px 20px",
                "backgroundColor" to options.backgroundColor,
                "borderRadius" to "10px"
            ),
            "header" to mapOf(
                "display" to "flex",
                "justifyContent" to "space-between",
                "alignItems" to "center",
                "title" to mapOf(
                    "text" to "Confirm Exit",
                    "fontSize" to "18px",
                    "fontWeight" to "bold",
                    "color" to "#000000"
                ),
                "closeButton" to mapOf(
                    "icon" to "close",
                    "color" to "#000000",
                    "onClick" to options.onClose
                )
            ),
            "divider" to mapOf(
                "color" to "#000000",
                "thickness" to "1px",
                "height" to "20px"
            ),
            "message" to mapOf(
                "text" to getConfirmationMessage(),
                "color" to "#000000",
                "fontSize" to "16px",
                "lineHeight" to "1.5",
                "marginBottom" to "20px"
            ),
            "buttons" to mapOf(
                "display" to "flex",
                "justifyContent" to "space-between",
                "gap" to "10px",
                "cancelButton" to mapOf(
                    "label" to "Cancel",
                    "backgroundColor" to "#616161",  // grey[700]
                    "color" to "#FFFFFF",
                    "fontSize" to "14px",
                    "padding" to "10px 15px",
                    "borderRadius" to "5px",
                    "onClick" to options.onClose
                ),
                "confirmButton" to mapOf(
                    "label" to getConfirmButtonLabel(),
                    "backgroundColor" to "#F44336",  // red
                    "color" to "#FFFFFF",
                    "fontSize" to "14px",
                    "padding" to "10px 15px",
                    "borderRadius" to "5px",
                    "onClick" to {
                        val exitOptions = ConfirmExitOptions(
                            member = options.member,
                            ban = options.ban,
                            socket = options.socket,
                            roomName = options.roomName
                        )
                        options.exitEventOnConfirm(exitOptions)
                        options.onClose()
                    }
                )
            )
        )
    }
}
