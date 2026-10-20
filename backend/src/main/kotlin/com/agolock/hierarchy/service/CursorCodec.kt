package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class Cursor(
    val name: String,
    val id: UUID,
)

object CursorCodec {
    fun encode(
        name: String,
        id: UUID,
    ): String {
        val raw = "$name\u0000$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(raw: String?): Cursor? {
        if (raw.isNullOrBlank()) return null
        return try {
            val decoded = String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8)
            val separator = decoded.lastIndexOf('\u0000')
            require(separator > 0) { "cursor separator" }
            Cursor(
                name = decoded.substring(0, separator),
                id = UUID.fromString(decoded.substring(separator + 1)),
            )
        } catch (exception: IllegalArgumentException) {
            throw ApiException(
                code = "INVALID_CURSOR",
                status = HttpStatus.BAD_REQUEST,
                message = "커서가 올바르지 않습니다.",
                cause = exception,
            )
        }
    }

    fun limit(raw: Int?): Int = (raw ?: DEFAULT_LIMIT).coerceIn(MIN_LIMIT, MAX_LIMIT)

    private const val DEFAULT_LIMIT = 20
    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 100
}
