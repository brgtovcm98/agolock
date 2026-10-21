@file:Suppress("LongParameterList", "MaxLineLength", "TooManyFunctions")

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

    @Select(
        """
        <script>
        SELECT * FROM share_invitation
        WHERE space_id = #{spaceId}
        <if test="status != null">AND status = CAST(#{status} AS invitation_status)</if>
        <if test="cursorCreatedAt != null">AND (created_at, id) &lt; (#{cursorCreatedAt}, #{cursorId})</if>
        ORDER BY created_at DESC, id DESC
        LIMIT #{limit}
        </script>
        """,
    )
    fun listPageBySpace(
        @Param("spaceId") spaceId: UUID,
        @Param("status") status: InvitationStatus?,
        @Param("cursorCreatedAt") cursorCreatedAt: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<ShareInvitation>

    @Select(
        """
        <script>
        SELECT * FROM share_invitation
        WHERE invited_email = #{email} AND status = 'PENDING' AND expires_at > #{now}
        <if test="cursorCreatedAt != null">AND (created_at, id) &lt; (#{cursorCreatedAt}, #{cursorId})</if>
        ORDER BY created_at DESC, id DESC
        LIMIT #{limit}
        </script>
        """,
    )
    fun listPagePendingReceived(
        @Param("email") email: String,
        @Param("now") now: OffsetDateTime,
        @Param("cursorCreatedAt") cursorCreatedAt: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
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

    @Select(
        """
        SELECT status::text FROM outbox_event
        WHERE aggregate_type = 'SHARE_INVITATION' AND aggregate_id = #{invitationId}
        ORDER BY created_at DESC, id DESC LIMIT 1
        """,
    )
    fun latestDeliveryStatus(
        @Param("invitationId") invitationId: UUID,
    ): String?

    @Update("UPDATE share_invitation SET token_hash = #{tokenHash} WHERE id = #{id} AND status = 'PENDING'")
    fun updateTokenHash(
        @Param("id") id: UUID,
        @Param("tokenHash") tokenHash: String,
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
