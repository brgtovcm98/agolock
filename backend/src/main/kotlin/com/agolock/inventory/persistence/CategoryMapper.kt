@file:Suppress("LongParameterList", "MaxLineLength")

package com.agolock.inventory.persistence

import com.agolock.inventory.domain.Category
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface CategoryMapper {
    @Select(
        """
        SELECT id, space_id, name,
                normalized_name, color, sort_order,
                created_at, updated_at, deleted_at
        FROM category
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun findById(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Category?

    @Select(
        "SELECT id, space_id, name, normalized_name, color, sort_order, created_at, updated_at, deleted_at FROM category WHERE space_id = #{spaceId} AND deleted_at IS NULL ORDER BY sort_order ASC, name ASC, id ASC LIMIT #{limit}",
    )
    fun list(
        @Param("spaceId") spaceId: UUID,
        @Param("limit") limit: Int,
    ): List<Category>

    @Select(
        "SELECT id, space_id, name, normalized_name, color, sort_order, created_at, updated_at, deleted_at FROM category WHERE space_id = #{spaceId} AND normalized_name = #{normalizedName} AND deleted_at IS NULL",
    )
    fun findActiveByNormalizedName(
        @Param("spaceId") spaceId: UUID,
        @Param("normalizedName") normalizedName: String,
    ): Category?

    @Insert(
        """
        INSERT INTO category (id, space_id, name, normalized_name, color, sort_order, created_at, updated_at, deleted_at)
        VALUES (#{id}, #{spaceId}, #{name}, #{normalizedName}, #{color}, #{sortOrder}, #{createdAt}, #{updatedAt}, #{deletedAt})
        """,
    )
    fun insert(category: Category)

    @Update(
        """
        UPDATE category SET name = #{name}, normalized_name = #{normalizedName}, color = #{color},
            sort_order = #{sortOrder}, updated_at = #{updatedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun update(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("name") name: String,
        @Param("normalizedName") normalizedName: String,
        @Param("color") color: String?,
        @Param("sortOrder") sortOrder: Int,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        "UPDATE category SET deleted_at = #{now}, updated_at = #{now} WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL",
    )
    fun softDelete(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int
}
