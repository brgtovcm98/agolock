package com.agolock.hierarchy.persistence

import com.agolock.hierarchy.domain.Shelf
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface ShelfMapper {
    @Select(
        """
        SELECT id, space_id, room_id, name, description, version, created_at, updated_at, deleted_at
        FROM shelf
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun findById(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Shelf?

    @Select(
        """
        SELECT id, space_id, room_id, name, description, version, created_at, updated_at, deleted_at
        FROM shelf
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        FOR UPDATE
        """,
    )
    fun findByIdForUpdate(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Shelf?

    @Select(
        """
        <script>
        SELECT id, space_id, room_id, name, description, version, created_at, updated_at, deleted_at
        FROM shelf
        WHERE space_id = #{spaceId} AND room_id = #{roomId} AND deleted_at IS NULL
        <if test="cursorName != null">
          AND (name, id) &gt; (#{cursorName}, #{cursorId})
        </if>
        ORDER BY name ASC, id ASC
        LIMIT #{limit}
        </script>
        """,
    )
    fun listByRoom(
        @Param("spaceId") spaceId: UUID,
        @Param("roomId") roomId: UUID,
        @Param("cursorName") cursorName: String?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Shelf>

    @Select(
        """
        SELECT COUNT(*)
        FROM box
        WHERE shelf_id = #{shelfId} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun countBoxes(
        @Param("spaceId") spaceId: UUID,
        @Param("shelfId") shelfId: UUID,
    ): Int

    @Insert(
        """
        INSERT INTO shelf (id, space_id, room_id, name, description, version, created_at, updated_at, deleted_at)
        VALUES (#{id}, #{spaceId}, #{roomId}, #{name}, #{description}, #{version}, #{createdAt}, #{updatedAt}, #{deletedAt})
        """,
    )
    fun insert(shelf: Shelf)

    @Update(
        """
        UPDATE shelf
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
        UPDATE shelf
        SET room_id = #{roomId}, version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND version = #{version} AND deleted_at IS NULL
        """,
    )
    fun move(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("roomId") roomId: UUID,
        @Param("version") version: Long,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE shelf
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
