@file:Suppress("LongParameterList", "MaxLineLength")

package com.agolock.messaging.outbox

import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
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

    @Select(
        """
        SELECT id, space_id, aggregate_type, aggregate_id, event_type, payload::text AS payload, attempts
        FROM outbox_event
        WHERE status = 'PENDING' AND available_at <= #{now}
        ORDER BY available_at ASC
        LIMIT #{limit}
        """,
    )
    fun findPending(
        @Param("now") now: OffsetDateTime,
        @Param("limit") limit: Int,
    ): List<OutboxEvent>

    @Update("UPDATE outbox_event SET status = 'PUBLISHED', published_at = #{now} WHERE id = #{id}")
    fun markPublished(
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE outbox_event
        SET attempts = attempts + 1,
            last_error = #{error},
            status = CASE WHEN attempts + 1 >= 5 THEN 'FAILED' ELSE 'PENDING' END,
            available_at = #{availableAt}
        WHERE id = #{id}
        """,
    )
    fun markFailed(
        @Param("id") id: UUID,
        @Param("error") error: String,
        @Param("availableAt") availableAt: OffsetDateTime,
    ): Int
}
