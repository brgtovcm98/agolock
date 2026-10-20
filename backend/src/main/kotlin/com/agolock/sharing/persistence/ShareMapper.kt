@file:Suppress("MaxLineLength")

package com.agolock.sharing.persistence

import com.agolock.hierarchy.domain.Permission
import com.agolock.sharing.domain.Share
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface ShareMapper {
    @Select("SELECT * FROM share WHERE id = #{id}")
    fun findById(
        @Param("id") id: UUID,
    ): Share?

    @Select(
        """
        SELECT * FROM share
        WHERE grantee_user_id = #{granteeUserId} AND revoked_at IS NULL
          AND (room_id = #{roomId} OR shelf_id = #{shelfId})
        """,
    )
    fun findActiveForTarget(
        @Param("granteeUserId") granteeUserId: UUID,
        @Param("roomId") roomId: UUID?,
        @Param("shelfId") shelfId: UUID?,
    ): List<Share>

    @Select("SELECT * FROM share WHERE grantee_user_id = #{granteeUserId} AND revoked_at IS NULL ORDER BY created_at DESC")
    fun listActiveByGrantee(
        @Param("granteeUserId") granteeUserId: UUID,
    ): List<Share>

    @Select("SELECT * FROM share WHERE space_id = #{spaceId} AND revoked_at IS NULL ORDER BY created_at DESC")
    fun listActiveBySpace(
        @Param("spaceId") spaceId: UUID,
    ): List<Share>

    @Select("SELECT * FROM share WHERE room_id = #{roomId} AND grantee_user_id = #{granteeUserId} AND revoked_at IS NULL")
    fun findActiveRoomGrant(
        @Param("roomId") roomId: UUID,
        @Param("granteeUserId") granteeUserId: UUID,
    ): Share?

    @Select("SELECT * FROM share WHERE shelf_id = #{shelfId} AND grantee_user_id = #{granteeUserId} AND revoked_at IS NULL")
    fun findActiveShelfGrant(
        @Param("shelfId") shelfId: UUID,
        @Param("granteeUserId") granteeUserId: UUID,
    ): Share?

    @Insert(
        """
        INSERT INTO share (id, space_id, room_id, shelf_id, grantee_user_id, permission, granted_by, created_at, updated_at, revoked_at)
        VALUES (#{id}, #{spaceId}, #{roomId}, #{shelfId}, #{granteeUserId}, CAST(#{permission} AS permission), #{grantedBy}, #{createdAt}, #{updatedAt}, #{revokedAt})
        """,
    )
    fun insert(share: Share)

    @Update("UPDATE share SET permission = CAST(#{permission} AS permission), updated_at = #{now} WHERE id = #{id} AND revoked_at IS NULL")
    fun updatePermission(
        @Param("id") id: UUID,
        @Param("permission") permission: Permission,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update("UPDATE share SET revoked_at = #{now}, updated_at = #{now} WHERE id = #{id} AND revoked_at IS NULL")
    fun revoke(
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int
}
