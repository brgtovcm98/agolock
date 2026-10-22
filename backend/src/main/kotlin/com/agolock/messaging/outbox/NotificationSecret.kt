package com.agolock.messaging.outbox

import java.time.OffsetDateTime
import java.util.UUID

data class NotificationSecret(
    val id: UUID,
    val spaceId: UUID,
    val secretType: String,
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val expiresAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
)
