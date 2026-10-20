package com.agolock.common.idempotency

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.OffsetDateTime
import java.util.HexFormat
import java.util.UUID

data class IdempotentResult<T>(
    val status: Int,
    val body: T,
    val location: String?,
)

@Service
class IdempotencyService(
    private val mapper: IdempotencyRecordMapper,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    @Suppress("LongParameterList", "ReturnCount", "ComplexCondition")
    fun <T> execute(
        userId: UUID,
        key: String?,
        method: String,
        path: String,
        request: Any,
        responseType: Class<T>,
        locationOf: (T) -> String?,
        action: () -> T,
    ): IdempotentResult<T> {
        if (key.isNullOrBlank()) {
            val body = action()
            return IdempotentResult(HttpStatus.CREATED.value(), body, locationOf(body))
        }

        val now = OffsetDateTime.now(clock)
        mapper.deleteExpired(now)
        val requestHash = hash(method, path, request)
        val existing = mapper.findByUserAndKey(userId, key)
        if (existing != null) {
            val sameRequest =
                existing.requestHash == requestHash &&
                    existing.requestMethod == method &&
                    existing.requestPath == path
            if (existing.expiresAt.isAfter(now)) {
                if (sameRequest) {
                    val body = objectMapper.readValue(existing.responseBody, responseType)
                    return IdempotentResult(existing.responseStatus, body, locationOf(body))
                }
                throw ApiException(
                    code = "IDEMPOTENCY_KEY_REUSED",
                    status = HttpStatus.CONFLICT,
                    message = "같은 Idempotency-Key로 다른 요청을 보냈습니다.",
                )
            }
            mapper.deleteById(existing.id)
        }

        val body = action()
        val status = HttpStatus.CREATED.value()
        val record =
            IdempotencyRecord(
                id = UuidV7.generate(clock),
                userId = userId,
                idempotencyKey = key,
                requestMethod = method,
                requestPath = path,
                requestHash = requestHash,
                responseStatus = status,
                responseBody = objectMapper.writeValueAsString(body),
                createdAt = now,
                expiresAt = now.plusHours(IDEMPOTENCY_TTL_HOURS),
            )
        try {
            mapper.insert(record)
        } catch (exception: DuplicateKeyException) {
            // 동시에 같은 키가 들어온 경우 기존 응답을 재생합니다.
            val replay = mapper.findByUserAndKey(userId, key)
            if (replay != null && replay.requestHash == requestHash) {
                val replayBody = objectMapper.readValue(replay.responseBody, responseType)
                return IdempotentResult(replay.responseStatus, replayBody, locationOf(replayBody))
            }
            throw ApiException(
                code = "IDEMPOTENCY_KEY_REUSED",
                status = HttpStatus.CONFLICT,
                message = "같은 Idempotency-Key로 다른 요청을 보냈습니다.",
                cause = exception,
            )
        }
        return IdempotentResult(status, body, locationOf(body))
    }

    private companion object {
        const val IDEMPOTENCY_TTL_HOURS = 24L
    }

    private fun hash(
        method: String,
        path: String,
        request: Any,
    ): String {
        val payload = "$method\n$path\n${objectMapper.writeValueAsString(request)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
