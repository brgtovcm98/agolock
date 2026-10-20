package com.agolock.identity.persistence

import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface InviteCodeUseMapper {
    @Insert(
        """
        INSERT INTO invite_code_use (id, invite_code_id, user_id, used_at)
        VALUES (#{id}, #{inviteCodeId}, #{userId}, #{usedAt})
        """,
    )
    fun insert(
        @Param("id") id: UUID,
        @Param("inviteCodeId") inviteCodeId: UUID,
        @Param("userId") userId: UUID,
        @Param("usedAt") usedAt: OffsetDateTime,
    )
}
