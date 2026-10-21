package com.agolock.identity.domain

import java.time.OffsetDateTime
import java.util.UUID

data class EmailVerificationToken(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: OffsetDateTime,
    val usedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)
