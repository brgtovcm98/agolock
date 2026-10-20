package com.agolock.identity.domain

import java.time.OffsetDateTime
import java.util.UUID

enum class UserStatus {
    ACTIVE,
    LOCKED,
    DELETED,
}

data class AppUser(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val status: UserStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime? = null,
)
