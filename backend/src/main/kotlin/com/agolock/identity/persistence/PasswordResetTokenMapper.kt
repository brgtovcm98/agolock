package com.agolock.identity.persistence

import com.agolock.identity.domain.PasswordResetToken
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface PasswordResetTokenMapper {
    @Insert(
        """
        INSERT INTO password_reset_token (
            id, user_id, token_hash, expires_at, used_at, created_at
        ) VALUES (
            #{id}, #{userId}, #{tokenHash}, #{expiresAt}, #{usedAt}, #{createdAt}
        )
        """,
    )
    fun insert(token: PasswordResetToken)

    @Select(
        """
        SELECT id, user_id, token_hash, expires_at, used_at, created_at
        FROM password_reset_token
        WHERE token_hash = #{tokenHash}
        FOR UPDATE
        """,
    )
    fun findByHashForUpdate(
        @Param("tokenHash") tokenHash: String,
    ): PasswordResetToken?

    @Update(
        """
        UPDATE password_reset_token
        SET used_at = #{usedAt}
        WHERE id = #{id} AND used_at IS NULL
        """,
    )
    fun markUsed(
        @Param("id") id: UUID,
        @Param("usedAt") usedAt: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE password_reset_token
        SET used_at = #{usedAt}
        WHERE user_id = #{userId} AND used_at IS NULL
        """,
    )
    fun invalidateActiveForUser(
        @Param("userId") userId: UUID,
        @Param("usedAt") usedAt: OffsetDateTime,
    ): Int
}
