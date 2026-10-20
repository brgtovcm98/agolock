package com.agolock.common.idempotency

import java.time.OffsetDateTime
import java.util.UUID

data class IdempotencyRecord(
    val id: UUID,
    val userId: UUID,
    val idempotencyKey: String,
    val requestMethod: String,
    val requestPath: String,
    val requestHash: String,
    val responseStatus: Int,
    val responseBody: String,
    val createdAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
)
