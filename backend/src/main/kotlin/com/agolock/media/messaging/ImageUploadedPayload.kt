package com.agolock.media.messaging

import java.util.UUID

data class ImageUploadedPayload(
    val imageId: UUID,
    val targetType: String,
    val targetId: UUID,
    val objectKey: String,
    val declaredSizeBytes: Long,
)
