package com.agolock.search

import com.agolock.common.error.ApiException
import com.agolock.search.service.SearchCursorCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.nio.charset.StandardCharsets
import java.util.Base64

class SearchCursorCodecTest {
    @Test
    fun `offset을 왕복한다`() {
        assertThat(SearchCursorCodec.decode(SearchCursorCodec.encode(0))).isZero()
        assertThat(SearchCursorCodec.decode(SearchCursorCodec.encode(40))).isEqualTo(40)
    }

    @Test
    fun `blank cursor는 0이다`() {
        assertThat(SearchCursorCodec.decode(null)).isZero()
        assertThat(SearchCursorCodec.decode("")).isZero()
        assertThat(SearchCursorCodec.decode("   ")).isZero()
    }

    @Test
    fun `깨진 base64 커서는 400 예외다`() {
        assertInvalidCursor("%%%not-base64%%%")
    }

    @Test
    fun `숫자가 아닌 payload는 400 예외다`() {
        assertInvalidCursor(rawCursor("o:abc"))
    }

    @Test
    fun `음수 offset은 400 예외다`() {
        assertInvalidCursor(rawCursor("o:-1"))
    }

    @Test
    fun `prefix가 없는 payload는 400 예외다`() {
        assertInvalidCursor(rawCursor("40"))
    }

    private fun assertInvalidCursor(cursor: String) {
        val exception =
            assertThrows(ApiException::class.java) {
                SearchCursorCodec.decode(cursor)
            }
        assertThat(exception.code).isEqualTo("INVALID_CURSOR")
        assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(exception.message).contains("커서")
    }

    private fun rawCursor(raw: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
}
