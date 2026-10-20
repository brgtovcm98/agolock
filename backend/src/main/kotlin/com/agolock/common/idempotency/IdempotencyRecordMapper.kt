package com.agolock.common.idempotency

import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface IdempotencyRecordMapper {
    @Select(
        """
        SELECT id, user_id, idempotency_key, request_method, request_path, request_hash,
               response_status, response_body::text AS response_body, created_at, expires_at
        FROM idempotency_record
        WHERE user_id = #{userId} AND idempotency_key = #{key}
        """,
    )
    fun findByUserAndKey(
        @Param("userId") userId: UUID,
        @Param("key") key: String,
    ): IdempotencyRecord?

    @Insert(
        """
        INSERT INTO idempotency_record (
            id, user_id, idempotency_key, request_method, request_path, request_hash,
            response_status, response_body, created_at, expires_at
        ) VALUES (
            #{id}, #{userId}, #{idempotencyKey}, #{requestMethod}, #{requestPath}, #{requestHash},
            #{responseStatus}, CAST(#{responseBody} AS jsonb), #{createdAt}, #{expiresAt}
        )
        """,
    )
    fun insert(record: IdempotencyRecord)

    @Delete("DELETE FROM idempotency_record WHERE expires_at <= #{now}")
    fun deleteExpired(
        @Param("now") now: OffsetDateTime,
    ): Int

    @Delete("DELETE FROM idempotency_record WHERE id = #{id}")
    fun deleteById(
        @Param("id") id: UUID,
    ): Int
}
