package com.agolock.search

import com.agolock.common.error.ApiException
import com.agolock.search.service.SearchCursor
import com.agolock.search.service.SearchCursorCodec
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class SearchCursorCodecTest {
    private val id = UUID.randomUUID()
    private val time = "2026-10-23T00:00:00Z"

    @Test
    fun `유효한 정렬 키를 왕복한다`() {
        mapOf(
            "RELEVANCE" to "100.0000",
            "UPDATED_DESC" to time,
            "VIEWED_DESC" to time,
            "STATUS_ASC" to "1",
            "NAME_ASC" to "한글 물건".repeat(20),
        ).forEach { (sort, key) ->
            val value = SearchCursor(s = sort, k = key, i = id, f = "fingerprint", u = time)
            assertThat(SearchCursorCodec.decode(SearchCursorCodec.encode(value), sort, "fingerprint")).isEqualTo(value)
        }
    }

    @Test
    fun `버전 정렬 지문과 키의 타입 범위를 검증한다`() {
        val cursor = SearchCursor(s = "RELEVANCE", k = "100.0000", i = id, f = "fingerprint", u = time)
        listOf(
            cursor.copy(v = 2),
            cursor.copy(f = "other"),
            cursor.copy(s = "NAME_ASC"),
            cursor.copy(k = "NaN"),
            cursor.copy(k = "1e100000"),
            cursor.copy(k = "-1"),
            cursor.copy(u = "+999999999-01-01T00:00:00Z"),
        ).forEach { invalid ->
            assertThatThrownBy {
                SearchCursorCodec.decode(SearchCursorCodec.encode(invalid), "RELEVANCE", "fingerprint")
            }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("code", "INVALID_CURSOR")
        }
    }

    @Test
    fun `PostgreSQL 범위 밖 시간대의 커서를 거부한다`() {
        listOf("UPDATED_DESC", "VIEWED_DESC").forEach { sort ->
            val value = SearchCursor(s = sort, k = "2026-10-23T00:00:00+18:00", i = id, f = "fingerprint", u = time)
            assertThatThrownBy {
                SearchCursorCodec.decode(SearchCursorCodec.encode(value), sort, "fingerprint")
            }.isInstanceOf(ApiException::class.java).hasFieldOrPropertyWithValue("code", "INVALID_CURSOR")
        }
    }
}
