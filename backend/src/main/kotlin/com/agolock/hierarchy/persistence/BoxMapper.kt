package com.agolock.hierarchy.persistence

import com.agolock.hierarchy.domain.Box
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface BoxMapper {
    @Select(
        """
        SELECT id, space_id, shelf_id, name, description, version, created_at, updated_at, deleted_at
        FROM box
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun findById(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Box?

    @Select(
        """
        <script>
        SELECT id, space_id, shelf_id, name, description, version, created_at, updated_at, deleted_at
        FROM box
        WHERE space_id = #{spaceId} AND shelf_id = #{shelfId} AND deleted_at IS NULL
        <if test="cursorName != null">
          AND (name, id) &gt; (#{cursorName}, #{cursorId})
        </if>
        ORDER BY name ASC, id ASC
        LIMIT #{limit}
        </script>
        """,
    )
    fun listByShelf(
        @Param("spaceId") spaceId: UUID,
        @Param("shelfId") shelfId: UUID,
        @Param("cursorName") cursorName: String?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Box>

    @Insert(
        """
        INSERT INTO box (id, space_id, shelf_id, name, description, version, created_at, updated_at, deleted_at)
        VALUES (#{id}, #{spaceId}, #{shelfId}, #{name}, #{description}, #{version}, #{createdAt}, #{updatedAt}, #{deletedAt})
        """,
    )
    fun insert(box: Box)

    @Update(
        """
        UPDATE box
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
        UPDATE box
        SET shelf_id = #{shelfId}, version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND version = #{version} AND deleted_at IS NULL
        """,
    )
    fun move(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("shelfId") shelfId: UUID,
        @Param("version") version: Long,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE box
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
