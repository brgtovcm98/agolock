package com.agolock.identity.domain

import java.time.OffsetDateTime
import java.util.UUID

data class Space(
    val id: UUID,
    val ownerUserId: UUID,
    val name: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime? = null,
)
