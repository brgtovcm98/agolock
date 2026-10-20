@file:Suppress("LongParameterList")

package com.agolock.messaging.outbox

import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface OutboxEventMapper {
    @Insert(
        """
        INSERT INTO outbox_event (
            id, space_id, aggregate_type, aggregate_id, event_type, payload, status, attempts, available_at, created_at
        ) VALUES (
            #{id}, #{spaceId}, #{aggregateType}, #{aggregateId}, #{eventType}, CAST(#{payload} AS jsonb), 'PENDING', 0, #{availableAt}, #{createdAt}
        )
        """,
    )
    fun insert(
        @Param("id") id: UUID,
        @Param("spaceId") spaceId: UUID?,
        @Param("aggregateType") aggregateType: String,
        @Param("aggregateId") aggregateId: UUID,
        @Param("eventType") eventType: String,
        @Param("payload") payload: String,
        @Param("availableAt") availableAt: OffsetDateTime,
        @Param("createdAt") createdAt: OffsetDateTime,
    )
}
