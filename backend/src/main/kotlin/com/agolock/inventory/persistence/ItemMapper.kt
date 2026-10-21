@file:Suppress("LongParameterList", "MaxLineLength")

package com.agolock.inventory.persistence

import com.agolock.inventory.domain.Item
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface ItemMapper {
    @Select("SELECT * FROM item WHERE id = #{id} AND deleted_at IS NULL")
    fun findByIdAny(
        @Param("id") id: UUID,
    ): Item?

    @Select("SELECT * FROM item WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL")
    fun findById(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Item?

    @Select(
        """
        <script>
        SELECT * FROM item
        WHERE space_id = #{spaceId} AND deleted_at IS NULL
        <if test="categoryId != null">AND category_id = #{categoryId}</if>
        <if test="q != null and q != ''">AND name ILIKE '%' || #{q} || '%'</if>
        <if test="cursorUpdated != null">AND (updated_at, id) &lt; (#{cursorUpdated}, #{cursorId})</if>
        ORDER BY updated_at DESC, id DESC LIMIT #{limit}
        </script>
        """,
    )
    fun list(
        @Param("spaceId") spaceId: UUID,
        @Param("categoryId") categoryId: UUID?,
        @Param("q") q: String?,
        @Param("cursorUpdated") cursorUpdated: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Item>

    @Select(
        """
        <script>
        SELECT DISTINCT i.* FROM item i
        JOIN inventory inv ON inv.item_id = i.id AND inv.space_id = i.space_id AND inv.deleted_at IS NULL
        JOIN share s ON s.space_id = i.space_id AND s.grantee_user_id = #{userId} AND s.revoked_at IS NULL
          AND ((s.room_id IS NOT NULL AND inv.room_id = s.room_id)
            OR (s.shelf_id IS NOT NULL AND inv.shelf_id = s.shelf_id))
        WHERE i.deleted_at IS NULL
        <if test="categoryId != null">AND i.category_id = #{categoryId}</if>
        <if test="q != null and q != ''">AND i.name ILIKE '%' || #{q} || '%'</if>
        <if test="cursorUpdated != null">AND (i.updated_at, i.id) &lt; (#{cursorUpdated}, #{cursorId})</if>
        ORDER BY i.updated_at DESC, i.id DESC LIMIT #{limit}
        </script>
        """,
    )
    fun listShared(
        @Param("userId") userId: UUID,
        @Param("categoryId") categoryId: UUID?,
        @Param("q") q: String?,
        @Param("cursorUpdated") cursorUpdated: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Item>

    @Select("SELECT * FROM item WHERE space_id = #{spaceId} AND category_id = #{categoryId} AND deleted_at IS NULL")
    fun listByCategory(
        @Param("spaceId") spaceId: UUID,
        @Param("categoryId") categoryId: UUID,
    ): List<Item>

    @Insert(
        """
        INSERT INTO item (id, space_id, name, description, category_id, price_amount, currency, version, created_at, updated_at, deleted_at)
        VALUES (#{id}, #{spaceId}, #{name}, #{description}, #{categoryId}, #{priceAmount}, #{currency}, #{version}, #{createdAt}, #{updatedAt}, #{deletedAt})
        """,
    )
    fun insert(item: Item)

    @Update(
        """
        UPDATE item SET name = #{name}, description = #{description}, category_id = #{categoryId},
            price_amount = #{priceAmount}, version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND version = #{version} AND deleted_at IS NULL
        """,
    )
    fun update(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("name") name: String,
        @Param("description") description: String?,
        @Param("categoryId") categoryId: UUID?,
        @Param("priceAmount") priceAmount: Int?,
        @Param("version") version: Long,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        "UPDATE item SET category_id = #{targetId}, updated_at = #{now} WHERE category_id = #{sourceId} AND space_id = #{spaceId} AND deleted_at IS NULL",
    )
    fun reassignCategory(
        @Param("spaceId") spaceId: UUID,
        @Param("sourceId") sourceId: UUID,
        @Param("targetId") targetId: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update("UPDATE item SET deleted_at = #{now}, updated_at = #{now} WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL")
    fun softDelete(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int
}
