package com.agolock.inventory

import com.agolock.common.cursor.TimeCursorCodec
import com.agolock.common.error.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class InventoryCursorTest {
    @Test
    fun `updatedAt과 id를 왕복한다`() {
        val updatedAt = OffsetDateTime.parse("2026-10-21T12:00:00.123456Z")
        val id = UUID.randomUUID()
        val cursor = TimeCursorCodec.decode(TimeCursorCodec.encode(updatedAt, id))!!
        assertThat(cursor.updatedAt).isEqualTo(updatedAt)
        assertThat(cursor.id).isEqualTo(id)
    }

    @Test
    fun `잘못된 cursor는 400 예외다`() {
        assertThatThrownBy { TimeCursorCodec.decode("not-a-cursor") }
            .isInstanceOf(ApiException::class.java)
            .hasMessageContaining("커서")
    }

    @Test
    fun `limit은 1에서 100으로 제한한다`() {
        assertThat(TimeCursorCodec.limit(0)).isEqualTo(1)
        assertThat(TimeCursorCodec.limit(101)).isEqualTo(100)
        assertThat(TimeCursorCodec.limit(null)).isEqualTo(20)
    }
}
