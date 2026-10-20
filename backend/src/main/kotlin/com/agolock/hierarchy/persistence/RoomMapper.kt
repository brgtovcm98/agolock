package com.agolock.hierarchy.persistence

import com.agolock.hierarchy.domain.Room
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface RoomMapper {
    @Select(
        """
        SELECT id, space_id, name, description, version, created_at, updated_at, deleted_at
        FROM room
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun findById(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Room?

    @Select(
        """
        <script>
        SELECT id, space_id, name, description, version, created_at, updated_at, deleted_at
        FROM room
        WHERE space_id = #{spaceId} AND deleted_at IS NULL
        <if test="cursorName != null">
          AND (name, id) &gt; (#{cursorName}, #{cursorId})
        </if>
        ORDER BY name ASC, id ASC
        LIMIT #{limit}
        </script>
        """,
    )
    fun list(
        @Param("spaceId") spaceId: UUID,
        @Param("cursorName") cursorName: String?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Room>

    @Select(
        """
        SELECT COUNT(*)
        FROM shelf
        WHERE room_id = #{roomId} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun countShelves(
        @Param("spaceId") spaceId: UUID,
        @Param("roomId") roomId: UUID,
    ): Int

    @Insert(
        """
        INSERT INTO room (id, space_id, name, description, version, created_at, updated_at, deleted_at)
        VALUES (#{id}, #{spaceId}, #{name}, #{description}, #{version}, #{createdAt}, #{updatedAt}, #{deletedAt})
        """,
    )
    fun insert(room: Room)

    @Update(
        """
        UPDATE room
        SET name = #{name}, description = #{description}, version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND version = #{version} AND deleted_at IS NULL
        """,
    )
    @Suppress("LongParameterList")
    fun update(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("name") name: String,
        @Param("description") description: String?,
        @Param("version") version: Long,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE room
        SET deleted_at = #{deletedAt}, updated_at = #{deletedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun softDelete(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("deletedAt") deletedAt: OffsetDateTime,
    ): Int
}
