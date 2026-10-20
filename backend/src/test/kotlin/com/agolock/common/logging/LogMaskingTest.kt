package com.agolock.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogMaskingTest {
    @Test
    fun `민감한 키와 토큰 값을 마스킹한다`() {
        val raw =
            "password=supersecret token=abc123 session=session-id-1 " +
                "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature " +
                "url=https://storage.example/bucket/key?X-Amz-Signature=deadbeef"

        val masked = LogMasker.mask(raw)

        assertThat(masked).doesNotContain("supersecret", "abc123", "session-id-1", "deadbeef")
        assertThat(masked).contains("password=***", "token=***", "Bearer ***")
    }

    @Test
    fun `일반 로그 문장은 바꾸지 않는다`() {
        val raw = "inventory 018f created in space 018e"

        assertThat(LogMasker.mask(raw)).isEqualTo(raw)
    }
}
