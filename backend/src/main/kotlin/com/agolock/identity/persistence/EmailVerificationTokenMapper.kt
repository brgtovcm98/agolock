package com.agolock.identity.persistence

import com.agolock.identity.domain.EmailVerificationToken
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface EmailVerificationTokenMapper {
    @Insert(
        """
        INSERT INTO email_verification_token (id, user_id, token_hash, expires_at, used_at, created_at)
        VALUES (#{id}, #{userId}, #{tokenHash}, #{expiresAt}, #{usedAt}, #{createdAt})
        """,
    )
    fun insert(token: EmailVerificationToken)

    @Select("SELECT * FROM email_verification_token WHERE token_hash = #{tokenHash} FOR UPDATE")
    fun findByTokenHashForUpdate(
        @Param("tokenHash") tokenHash: String,
    ): EmailVerificationToken?

    @Update("UPDATE email_verification_token SET used_at = #{now} WHERE id = #{id} AND used_at IS NULL")
    fun consume(
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update("UPDATE email_verification_token SET used_at = #{now} WHERE user_id = #{userId} AND used_at IS NULL")
    fun invalidateUnused(
        @Param("userId") userId: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int
}
