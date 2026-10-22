package com.agolock.search.service

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus
import java.nio.charset.StandardCharsets
import java.util.Base64

object SearchCursorCodec {
    private const val PREFIX = "o:"

    fun encode(offset: Int): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString("$PREFIX$offset".toByteArray(StandardCharsets.UTF_8))

    fun decode(cursor: String?): Int {
        if (cursor.isNullOrBlank()) return 0
        return try {
            val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
            require(decoded.startsWith(PREFIX)) { "cursor prefix" }
            val offset = decoded.removePrefix(PREFIX).toInt()
            require(offset >= 0) { "cursor offset" }
            offset
        } catch (exception: IllegalArgumentException) {
            throw ApiException(
                "INVALID_CURSOR",
                HttpStatus.BAD_REQUEST,
                "커서가 올바르지 않습니다.",
                cause = exception,
            )
        }
    }
}
