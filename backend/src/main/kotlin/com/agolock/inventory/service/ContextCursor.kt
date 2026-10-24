package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID

/** Binds each continuation to its actor, resource, filters and ordering. */
object ContextCursor {
    fun encode(
        context: String,
        values: List<String>,
    ): String {
        val parts = listOf(fingerprint(context)) + values
        return parts.joinToString(".") {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
        }
    }

    fun decode(
        raw: String?,
        context: String,
        size: Int,
    ): List<String>? {
        if (raw == null) return null
        return try {
            val parts = raw.split('.').map { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
            require(parts.size == size + 1 && parts.first() == fingerprint(context))
            parts.drop(1)
        } catch (exception: IllegalArgumentException) {
            throw ApiException("INVALID_CURSOR", HttpStatus.BAD_REQUEST, "커서가 올바르지 않습니다.", cause = exception)
        }
    }

    fun uuid(value: String): UUID = parsed { UUID.fromString(value) }

    fun time(value: String): OffsetDateTime = parsed { OffsetDateTime.parse(value) }

    fun number(value: String): Int = parsed { value.toInt() }

    private fun <T> parsed(parse: () -> T): T =
        try {
            parse()
        } catch (exception: IllegalArgumentException) {
            throw ApiException("INVALID_CURSOR", HttpStatus.BAD_REQUEST, "커서가 올바르지 않습니다.", cause = exception)
        } catch (exception: DateTimeParseException) {
            throw ApiException("INVALID_CURSOR", HttpStatus.BAD_REQUEST, "커서가 올바르지 않습니다.", cause = exception)
        }

    private fun fingerprint(context: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(context.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
