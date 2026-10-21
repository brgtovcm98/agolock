package com.agolock.media.domain

import java.time.OffsetDateTime
import java.util.UUID

enum class ImageStatus {
    UPLOADING,
    DRAFT,
    PENDING,
    PROCESSING,
    READY,
    FAILED,
    DELETED,
}

data class Image(
    val id: UUID,
    val spaceId: UUID,
    val itemId: UUID?,
    val inventoryId: UUID?,
    val objectKey: String,
    val status: ImageStatus,
    val uploadExpiresAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
)
