package com.agolock.messaging.outbox

import com.agolock.common.id.UuidV7
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OutboxService(
    private val mapper: OutboxEventMapper,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun enqueueImageUploaded(
        spaceId: UUID,
        imageId: UUID,
        payload: Map<String, Any?>,
    ) {
        val now = OffsetDateTime.now(clock)
        mapper.insert(
            id = UuidV7.generate(clock),
            spaceId = spaceId,
            aggregateType = "IMAGE",
            aggregateId = imageId,
            eventType = "image.uploaded",
            payload = objectMapper.writeValueAsString(payload),
            availableAt = now,
            createdAt = now,
        )
    }

    fun enqueueInvitation(
        spaceId: UUID,
        invitationId: UUID,
        payload: Map<String, Any?>,
    ) {
        val now = OffsetDateTime.now(clock)
        mapper.insert(
            id = UuidV7.generate(clock),
            spaceId = spaceId,
            aggregateType = "SHARE_INVITATION",
            aggregateId = invitationId,
            eventType = "notification.requested",
            payload = objectMapper.writeValueAsString(payload),
            availableAt = now,
            createdAt = now,
        )
    }
}
