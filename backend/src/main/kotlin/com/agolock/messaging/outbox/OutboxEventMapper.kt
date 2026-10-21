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

    @Select(
        """
        WITH candidate AS (
            SELECT id
            FROM outbox_event
            WHERE status = 'PENDING'
              AND available_at <= #{now}
              AND (locked_until IS NULL OR locked_until <= #{now})
              AND attempts < #{maxAttempts}
            ORDER BY available_at ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        )
        UPDATE outbox_event o
        SET locked_by = #{instanceId},
            locked_until = #{lockedUntil},
            claim_token = #{claimToken},
            attempts = o.attempts + 1
        FROM candidate c
        WHERE o.id = c.id
        RETURNING o.id, o.space_id, o.aggregate_type, o.aggregate_id,
                  o.event_type, o.payload::text AS payload, o.attempts, o.claim_token
        """,
    )
    fun claim(
        @Param("now") now: OffsetDateTime,
        @Param("lockedUntil") lockedUntil: OffsetDateTime,
        @Param("instanceId") instanceId: String,
        @Param("claimToken") claimToken: UUID,
        @Param("maxAttempts") maxAttempts: Int,
    ): ClaimedOutboxEvent?

    @Update(
        """
        UPDATE outbox_event
        SET status = 'PUBLISHED',
            published_at = #{now},
            payload = '{"redacted":true}'::jsonb,
            locked_by = NULL,
            locked_until = NULL,
            claim_token = NULL,
            last_error = NULL
        WHERE id = #{id} AND claim_token = #{claimToken} AND status = 'PENDING'
        """,
    )
    fun markPublished(
        @Param("id") id: UUID,
        @Param("claimToken") claimToken: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE outbox_event
        SET status = 'PENDING',
            available_at = #{availableAt},
            last_error = #{error},
            locked_by = NULL,
            locked_until = NULL,
            claim_token = NULL
        WHERE id = #{id} AND claim_token = #{claimToken} AND status = 'PENDING'
        """,
    )
    fun markRetryableFailure(
        @Param("id") id: UUID,
        @Param("claimToken") claimToken: UUID,
        @Param("error") error: String,
        @Param("availableAt") availableAt: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE outbox_event
        SET status = 'FAILED',
            last_error = #{error},
            payload = '{"redacted":true}'::jsonb,
            locked_by = NULL,
            locked_until = NULL,
            claim_token = NULL
        WHERE id = #{id} AND claim_token = #{claimToken} AND status = 'PENDING'
        """,
    )
    fun markTerminalFailure(
        @Param("id") id: UUID,
        @Param("claimToken") claimToken: UUID,
        @Param("error") error: String,
    ): Int
}
