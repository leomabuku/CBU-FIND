package com.campus.lostandfound.data

import com.campus.lostandfound.data.remote.ApiFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFailureTest {
    @Test fun `user message always includes stable reference`() {
        val failure = ApiFailure("RATE_LIMITED", "Wait a moment and retry.", true, "ref-123")
        assertEquals("RATE_LIMITED", failure.code)
        assertTrue(failure.retryable)
        assertEquals("Wait a moment and retry. Reference: ref-123", failure.userMessage())
    }
}
