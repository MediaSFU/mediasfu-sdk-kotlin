package com.mediasfu.sdk.methods

import com.mediasfu.sdk.methods.utils.CreateJoinRoomError
import com.mediasfu.sdk.methods.utils.CreateJoinRoomResponse
import com.mediasfu.sdk.methods.utils.CreateJoinRoomResult
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun assertSuccess(result: CreateJoinRoomResult) {
    assertTrue(result.success, "Expected operation to succeed, but it failed with: ${(result.data as? CreateJoinRoomError)?.error}")
    assertTrue(result.data is CreateJoinRoomResponse, "Expected response data on success")
}

fun assertFailure(result: CreateJoinRoomResult) {
    assertFalse(result.success, "Expected operation to fail")
    assertTrue(result.data is CreateJoinRoomError, "Expected error data on failure")
}
