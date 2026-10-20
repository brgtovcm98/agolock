@file:Suppress("LongParameterList", "MaxLineLength")

package com.agolock.sharing.persistence

import com.agolock.sharing.domain.InvitationStatus
import com.agolock.sharing.domain.ShareInvitation
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface ShareInvitationMapper {
    @Select("SELECT * FROM share_invitation WHERE id = #{id}")
    fun findById(
        @Param("id") id: UUID,
    ): ShareInvitation?

    @Select("SELECT * FROM share_invitation WHERE id = #{id} FOR UPDATE")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): ShareInvitation?

    @Select("SELECT * FROM share_invitation WHERE token_hash = #{tokenHash}")
    fun findByTokenHash(
        @Param("tokenHash") tokenHash: String,
    ): ShareInvitation?

    @Select(
        "SELECT * FROM share_invitation WHERE invited_email = #{email} AND status = 'PENDING' AND expires_at > #{now} ORDER BY created_at DESC",
    )
    fun listPendingReceived(
        @Param("email") email: String,
        @Param("now") now: OffsetDateTime,
    ): List<ShareInvitation>

    @Select("SELECT * FROM share_invitation WHERE space_id = #{spaceId} ORDER BY created_at DESC")
    fun listBySpace(
        @Param("spaceId") spaceId: UUID,
    ): List<ShareInvitation>

    @Insert(
        """
        INSERT INTO share_invitation (
            id, space_id, room_id, shelf_id, invited_email, invited_user_id, permission, token_hash,
            status, expires_at, created_by, created_at, accepted_at, declined_at, revoked_at
        ) VALUES (
            #{id}, #{spaceId}, #{roomId}, #{shelfId}, #{invitedEmail}, #{invitedUserId}, CAST(#{permission} AS permission), #{tokenHash},
            CAST(#{status} AS invitation_status), #{expiresAt}, #{createdBy}, #{createdAt}, #{acceptedAt}, #{declinedAt}, #{revokedAt}
        )
        """,
    )
    fun insert(invitation: ShareInvitation)

    @Update("UPDATE share_invitation SET status = 'EXPIRED' WHERE status = 'PENDING' AND expires_at <= #{now}")
    fun expireStale(
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE share_invitation SET status = CAST(#{status} AS invitation_status), invited_user_id = #{invitedUserId},
            accepted_at = #{acceptedAt}, declined_at = #{declinedAt}, revoked_at = #{revokedAt}
        WHERE id = #{id} AND status = 'PENDING'
        """,
    )
    fun updateStatus(
        @Param("id") id: UUID,
        @Param("status") status: InvitationStatus,
        @Param("invitedUserId") invitedUserId: UUID?,
        @Param("acceptedAt") acceptedAt: OffsetDateTime?,
        @Param("declinedAt") declinedAt: OffsetDateTime?,
        @Param("revokedAt") revokedAt: OffsetDateTime?,
    ): Int
}
