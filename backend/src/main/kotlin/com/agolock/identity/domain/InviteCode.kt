package com.agolock.identity.domain

import java.time.OffsetDateTime
import java.util.UUID

data class InviteCode(
    val id: UUID,
    val codeHash: String,
    val label: String?,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: OffsetDateTime?,
    val revokedAt: OffsetDateTime?,
    val createdBy: UUID?,
    val createdAt: OffsetDateTime,
)
