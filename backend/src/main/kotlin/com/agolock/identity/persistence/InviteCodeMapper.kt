package com.agolock.identity.persistence

import com.agolock.identity.domain.InviteCode
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface InviteCodeMapper {
    @Select(
        """
        SELECT id, code_hash, label, max_uses, used_count, expires_at,
               revoked_at, created_by, created_at
        FROM invite_code
        WHERE code_hash = #{codeHash}
        FOR UPDATE
        """,
    )
    fun findByHashForUpdate(
        @Param("codeHash") codeHash: String,
    ): InviteCode?

    @Select(
        """
        SELECT id, code_hash, label, max_uses, used_count, expires_at,
               revoked_at, created_by, created_at
        FROM invite_code
        ORDER BY created_at DESC
        """,
    )
    fun findAll(): List<InviteCode>

    @Insert(
        """
        INSERT INTO invite_code (
            id, code_hash, label, max_uses, used_count, expires_at,
            revoked_at, created_by, created_at
        ) VALUES (
            #{id}, #{codeHash}, #{label}, #{maxUses}, #{usedCount}, #{expiresAt},
            #{revokedAt}, #{createdBy}, #{createdAt}
        )
        """,
    )
    fun insert(code: InviteCode)

    @Update(
        """
        UPDATE invite_code
        SET used_count = used_count + 1
        WHERE id = #{id} AND used_count < max_uses AND revoked_at IS NULL
        """,
    )
    fun incrementUsedCount(
        @Param("id") id: UUID,
    ): Int

    @Update(
        """
        UPDATE invite_code
        SET revoked_at = #{revokedAt}
        WHERE id = #{id} AND revoked_at IS NULL
        """,
    )
    fun revoke(
        @Param("id") id: UUID,
        @Param("revokedAt") revokedAt: OffsetDateTime,
    ): Int
}
