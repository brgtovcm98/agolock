@file:Suppress("LongParameterList")

package com.agolock.common.audit

import com.agolock.common.id.UuidV7
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AuditService(
    private val mapper: AuditLogMapper,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun record(
        spaceId: UUID,
        actorUserId: UUID,
        action: String,
        resourceType: String,
        resourceId: UUID?,
        metadata: Map<String, Any?>,
    ) {
        mapper.insert(
            id = UuidV7.generate(clock),
            spaceId = spaceId,
            actorUserId = actorUserId,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            metadata = objectMapper.writeValueAsString(metadata),
            createdAt = OffsetDateTime.now(clock),
        )
    }
}
