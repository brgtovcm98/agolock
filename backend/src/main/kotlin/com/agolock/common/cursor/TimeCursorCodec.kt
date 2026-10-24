@file:Suppress("MagicNumber")

package com.agolock.common.cursor

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

data class TimeCursor(
    val updatedAt: OffsetDateTime,
    val id: UUID,
)

object TimeCursorCodec {
    fun encode(
        updatedAt: OffsetDateTime,
        id: UUID,
    ): String {
        val raw = "$updatedAt\u0000$id"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(raw: String?): TimeCursor? {
        if (raw.isNullOrBlank()) return null
        return try {
            val decoded = String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8)
            val separator = decoded.lastIndexOf('\u0000')
            require(separator > 0) { "cursor separator" }
            TimeCursor(
                OffsetDateTime.parse(decoded.substring(0, separator)),
                UUID.fromString(decoded.substring(separator + 1)),
            )
        } catch (exception: DateTimeException) {
            throw invalidCursor(exception)
        } catch (exception: IllegalArgumentException) {
            throw invalidCursor(exception)
        }
    }

    private fun invalidCursor(exception: Exception) =
        ApiException("INVALID_CURSOR", HttpStatus.BAD_REQUEST, "커서가 올바르지 않습니다.", cause = exception)

    fun limit(raw: Int?): Int = (raw ?: 20).coerceIn(1, 100)
}
