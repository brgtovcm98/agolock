package com.agolock.messaging.outbox

import java.util.UUID

data class ClaimedOutboxEvent(
    val id: UUID,
    val spaceId: UUID?,
    val aggregateType: String,
    val aggregateId: UUID,
    val eventType: String,
    val payload: String,
    val attempts: Int,
    val claimToken: UUID,
)
