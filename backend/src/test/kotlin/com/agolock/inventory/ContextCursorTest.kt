package com.agolock.inventory

import com.agolock.common.error.ApiException
import com.agolock.inventory.service.ContextCursor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ContextCursorTest {
    @Test
    fun `커서는 한글 구분자를 포함하는 이름과 문맥을 보존한다`() {
        val values = listOf("7", "가방 > 책\u0000상자", "uuid")
        val raw = ContextCursor.encode("actor:item:filter", values)
        assertThat(ContextCursor.decode(raw, "actor:item:filter", 3)).isEqualTo(values)
    }

    @Test
    fun `다른 필터와 잘못된 크기의 커서는 거부한다`() {
        val raw = ContextCursor.encode("actor:item:stored", listOf("1", "id"))
        assertThatThrownBy { ContextCursor.decode(raw, "actor:item:disposed", 2) }
            .isInstanceOf(ApiException::class.java)
        assertThatThrownBy { ContextCursor.decode(raw, "actor:item:stored", 3) }.isInstanceOf(ApiException::class.java)
    }
}
