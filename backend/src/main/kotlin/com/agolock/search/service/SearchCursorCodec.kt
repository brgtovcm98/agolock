package com.agolock.search.service

import com.agolock.common.error.ApiException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

data class SearchCursor(
    val v: Int = 1,
    val s: String,
    val k: String,
    val i: UUID,
    val f: String,
    val u: String,
)

object SearchCursorCodec {
    private const val MAX_CURSOR_LENGTH = 2048
    private const val FINGERPRINT_LENGTH = 16
    private const val MAX_SCORE_SCALE = 4
    private const val MAX_CURSOR_YEAR = 9999
    private const val MAX_OFFSET_SECONDS = 57599
    private val MAX_SCORE = BigDecimal("999999.9999")
    private val mapper = jacksonObjectMapper()

    fun fingerprint(request: SearchRequest): String {
        val values =
            listOf(
                request.q,
                request.sort,
                request.statuses?.map { it.name }?.sorted(),
                request.roomId,
                request.shelfId,
                request.boxId,
                request.categoryId,
                request.scope,
                request.ownerId,
                request.hasImage,
            )
        return MessageDigest
            .getInstance("SHA-256")
            .digest(mapper.writeValueAsBytes(values))
            .joinToString("") { "%02x".format(it) }
            .take(FINGERPRINT_LENGTH)
    }

    fun encode(cursor: SearchCursor): String {
        val payload = mapper.writeValueAsBytes(cursor)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    fun decode(
        cursor: String?,
        sort: String,
        fingerprint: String,
    ): SearchCursor? {
        if (cursor == null) return null
        try {
            require(cursor.isNotBlank() && cursor.length <= MAX_CURSOR_LENGTH)
            val result = mapper.readValue<SearchCursor>(Base64.getUrlDecoder().decode(cursor))
            require(result.v == 1 && result.s == sort && result.f == fingerprint)
            validateTimestamp(result.u)
            when (sort) {
                "UPDATED_DESC", "VIEWED_DESC" -> validateTimestamp(result.k)
                "RELEVANCE" -> {
                    val key = BigDecimal(result.k)
                    require(key.signum() >= 0 && key <= MAX_SCORE && key.scale() in 0..MAX_SCORE_SCALE)
                }
                "STATUS_ASC" -> require(result.k.toInt() in 0..2)
                "NAME_ASC" -> require(result.k.isNotBlank())
            }
            return result
        } catch (_: RuntimeException) {
            throw invalidCursor()
        } catch (_: java.io.IOException) {
            throw invalidCursor()
        }
    }

    private fun validateTimestamp(value: String) {
        val timestamp = OffsetDateTime.parse(value)
        require(timestamp.year in 1..MAX_CURSOR_YEAR)
        require(kotlin.math.abs(timestamp.offset.totalSeconds) <= MAX_OFFSET_SECONDS)
    }

    private fun invalidCursor() = ApiException("INVALID_CURSOR", HttpStatus.BAD_REQUEST, "커서가 올바르지 않습니다.")
}
