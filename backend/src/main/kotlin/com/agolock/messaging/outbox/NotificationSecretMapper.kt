@file:Suppress("LongParameterList")

package com.agolock.messaging.outbox

import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface NotificationSecretMapper {
    @Insert(
        """
        INSERT INTO notification_secret (
            id, space_id, secret_type, ciphertext, nonce, expires_at, created_at
        ) VALUES (
            #{id}, #{spaceId}, #{secretType}, #{ciphertext}, #{nonce}, #{expiresAt}, #{createdAt}
        )
        """,
    )
    fun insert(
        @Param("id") id: UUID,
        @Param("spaceId") spaceId: UUID,
        @Param("secretType") secretType: String,
        @Param("ciphertext") ciphertext: ByteArray,
        @Param("nonce") nonce: ByteArray,
        @Param("expiresAt") expiresAt: OffsetDateTime,
        @Param("createdAt") createdAt: OffsetDateTime,
    )

    @Select(
        """
        SELECT id, space_id, secret_type, ciphertext, nonce, expires_at, created_at
        FROM notification_secret
        WHERE id = #{id}
        """,
    )
    fun findById(
        @Param("id") id: UUID,
    ): NotificationSecret?

    @Delete("DELETE FROM notification_secret WHERE expires_at < #{before}")
    fun deleteExpired(
        @Param("before") before: OffsetDateTime,
    ): Int
}
