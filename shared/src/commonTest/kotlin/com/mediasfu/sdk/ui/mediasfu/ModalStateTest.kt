package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.methods.MediasfuParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModalStateTest {
    @Test
    fun `setSettingsVisibility updates state and parameters`() {
        val parameters = MediasfuParameters()
        var notifications = 0
        val modalState = ModalState(parameters) { notifications += 1 }

        assertFalse(parameters.isSettingsModalVisible)
        assertFalse(modalState.isSettingsVisible)

        modalState.setSettingsVisibility(true)

        assertTrue(parameters.isSettingsModalVisible)
        assertTrue(modalState.isSettingsVisible)
        assertEquals(1, notifications)

        modalState.setSettingsVisibility(false)

        assertFalse(parameters.isSettingsModalVisible)
        assertFalse(modalState.isSettingsVisible)
        assertEquals(2, notifications)
    }

    @Test
    fun `setScreenboardVisibility mirrors backing parameters`() {
        val parameters = MediasfuParameters()
        var notifications = 0
        val modalState = ModalState(parameters) { notifications += 1 }

        modalState.setScreenboardVisibility(true)
        assertTrue(modalState.isScreenboardVisible)
        assertTrue(parameters.isScreenboardModalVisible)

        modalState.setScreenboardVisibility(false)
        assertFalse(modalState.isScreenboardVisible)
        assertFalse(parameters.isScreenboardModalVisible)
        assertEquals(2, notifications)
    }

    @Test
    fun `setConfirmExitVisibility mirrors backing parameters`() {
        val parameters = MediasfuParameters()
        var notifications = 0
        val modalState = ModalState(parameters) { notifications += 1 }

        modalState.setConfirmExitVisibility(true)
        assertTrue(modalState.isConfirmExitVisible)
        assertTrue(parameters.isConfirmExitModalVisible)

        modalState.setConfirmExitVisibility(false)
        assertFalse(modalState.isConfirmExitVisible)
        assertFalse(parameters.isConfirmExitModalVisible)
        assertEquals(2, notifications)
    }

    @Test
    fun `setConfirmHereVisibility mirrors backing parameters`() {
        val parameters = MediasfuParameters()
        var notifications = 0
        val modalState = ModalState(parameters) { notifications += 1 }

        modalState.setConfirmHereVisibility(true)
        assertTrue(modalState.isConfirmHereVisible)
        assertTrue(parameters.isConfirmHereModalVisible)

        modalState.setConfirmHereVisibility(false)
        assertFalse(modalState.isConfirmHereVisible)
        assertFalse(parameters.isConfirmHereModalVisible)
        assertEquals(2, notifications)
    }

    @Test
    fun `setShareEventVisibility mirrors backing parameters`() {
        val parameters = MediasfuParameters()
        var notifications = 0
        val modalState = ModalState(parameters) { notifications += 1 }

        modalState.setShareEventVisibility(true)
        assertTrue(modalState.isShareEventVisible)
        assertTrue(parameters.isShareEventModalVisible)

        modalState.setShareEventVisibility(false)
        assertFalse(modalState.isShareEventVisible)
        assertFalse(parameters.isShareEventModalVisible)
        assertEquals(2, notifications)
    }

    @Test
    fun `closeAll clears modal flags`() {
        val parameters = MediasfuParameters().apply {
            isMenuModalVisible = true
            isRecordingModalVisible = true
            isSettingsModalVisible = true
            isRequestsModalVisible = true
            isWaitingModalVisible = true
            isCoHostModalVisible = true
            isMediaSettingsModalVisible = true
            isDisplaySettingsModalVisible = true
            isParticipantsModalVisible = true
            isMessagesModalVisible = true
            isConfirmExitModalVisible = true
            isConfirmHereModalVisible = true
            isShareEventModalVisible = true
            isLoadingModalVisible = true
            isScreenboardModalVisible = true
        }
        var notifications = 0
        val modalState = ModalState(parameters) { notifications += 1 }.apply {
            isMenuVisible = true
            isRecordingVisible = true
            isSettingsVisible = true
            isRequestsVisible = true
            isWaitingVisible = true
            isCoHostVisible = true
            isMediaSettingsVisible = true
            isDisplaySettingsVisible = true
            isParticipantsVisible = true
            isMessagesVisible = true
            isConfirmExitVisible = true
            isConfirmHereVisible = true
            isShareEventVisible = true
            isLoadingVisible = true
            isScreenboardVisible = true
        }

        modalState.closeAll()

        assertFalse(parameters.isMenuModalVisible)
        assertFalse(parameters.isRecordingModalVisible)
        assertFalse(parameters.isSettingsModalVisible)
        assertFalse(parameters.isRequestsModalVisible)
        assertFalse(parameters.isWaitingModalVisible)
        assertFalse(parameters.isCoHostModalVisible)
        assertFalse(parameters.isMediaSettingsModalVisible)
        assertFalse(parameters.isDisplaySettingsModalVisible)
        assertFalse(parameters.isParticipantsModalVisible)
        assertFalse(parameters.isMessagesModalVisible)
        assertFalse(parameters.isConfirmExitModalVisible)
        assertFalse(parameters.isConfirmHereModalVisible)
        assertFalse(parameters.isShareEventModalVisible)
        assertFalse(parameters.isLoadingModalVisible)
        assertFalse(parameters.isScreenboardModalVisible)

        assertFalse(modalState.isMenuVisible)
        assertFalse(modalState.isRecordingVisible)
        assertFalse(modalState.isSettingsVisible)
        assertFalse(modalState.isRequestsVisible)
        assertFalse(modalState.isWaitingVisible)
        assertFalse(modalState.isCoHostVisible)
        assertFalse(modalState.isMediaSettingsVisible)
        assertFalse(modalState.isDisplaySettingsVisible)
        assertFalse(modalState.isParticipantsVisible)
        assertFalse(modalState.isMessagesVisible)
        assertFalse(modalState.isConfirmExitVisible)
        assertFalse(modalState.isConfirmHereVisible)
        assertFalse(modalState.isShareEventVisible)
        assertFalse(modalState.isLoadingVisible)
        assertFalse(modalState.isScreenboardVisible)

        assertEquals(1, notifications)
    }
}
