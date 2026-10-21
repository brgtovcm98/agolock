@file:Suppress("LongParameterList", "MaxLineLength", "TooManyFunctions")

package com.agolock.inventory.persistence

import com.agolock.inventory.domain.Inventory
import com.agolock.inventory.domain.InventoryStatus
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface InventoryMapper {
    @Select("SELECT * FROM inventory WHERE id = #{id} AND deleted_at IS NULL")
    fun findByIdAny(
        @Param("id") id: UUID,
    ): Inventory?

    @Select("SELECT * FROM inventory WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL")
    fun findById(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Inventory?

    @Select(
        """
        <script>
        SELECT * FROM inventory
        WHERE space_id = #{spaceId} AND deleted_at IS NULL
        <if test="status != null">AND status = CAST(#{status} AS inventory_status)</if>
        <if test="status == null">AND status &lt;&gt; 'DISPOSED'</if>
        <if test="itemId != null">AND item_id = #{itemId}</if>
        <if test="cursorUpdated != null">AND (updated_at, id) &lt; (#{cursorUpdated}, #{cursorId})</if>
        ORDER BY updated_at DESC, id DESC LIMIT #{limit}
        </script>
        """,
    )
    fun list(
        @Param("spaceId") spaceId: UUID,
        @Param("status") status: InventoryStatus?,
        @Param("itemId") itemId: UUID?,
        @Param("cursorUpdated") cursorUpdated: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Inventory>

    @Select("SELECT * FROM inventory WHERE space_id = #{spaceId} AND item_id = #{itemId} AND deleted_at IS NULL AND status <> 'DISPOSED'")
    fun listByItem(
        @Param("spaceId") spaceId: UUID,
        @Param("itemId") itemId: UUID,
    ): List<Inventory>

    @Select("SELECT * FROM inventory WHERE space_id = #{spaceId} AND item_id = #{itemId} AND deleted_at IS NULL")
    fun listAllByItem(
        @Param("spaceId") spaceId: UUID,
        @Param("itemId") itemId: UUID,
    ): List<Inventory>

    @Select("SELECT COUNT(*) FROM inventory WHERE space_id = #{spaceId} AND item_id = #{itemId} AND deleted_at IS NULL")
    fun countByItem(
        @Param("spaceId") spaceId: UUID,
        @Param("itemId") itemId: UUID,
    ): Int

    @Select(
        "SELECT COUNT(*) FROM inventory WHERE space_id = #{spaceId} AND room_id = #{locationId} AND deleted_at IS NULL",
    )
    fun countByRoom(
        @Param("spaceId") spaceId: UUID,
        @Param("locationId") locationId: UUID,
    ): Int

    @Select(
        "SELECT COUNT(*) FROM inventory WHERE space_id = #{spaceId} AND shelf_id = #{locationId} AND deleted_at IS NULL",
    )
    fun countByShelf(
        @Param("spaceId") spaceId: UUID,
        @Param("locationId") locationId: UUID,
    ): Int

    @Select(
        "SELECT COUNT(*) FROM inventory WHERE space_id = #{spaceId} AND box_id = #{locationId} AND deleted_at IS NULL",
    )
    fun countByBox(
        @Param("spaceId") spaceId: UUID,
        @Param("locationId") locationId: UUID,
    ): Int

    @Select(
        "SELECT COUNT(*) FROM inventory WHERE space_id = #{spaceId} AND category_id_override = #{categoryId} AND deleted_at IS NULL",
    )
    fun countByCategoryOverride(
        @Param("spaceId") spaceId: UUID,
        @Param("categoryId") categoryId: UUID,
    ): Int

    @Insert(
        """
        INSERT INTO inventory (
            id, space_id, item_id, room_id, shelf_id, box_id, name_override, description_override,
            category_id_override, price_amount_override, status, version, created_at, updated_at, deleted_at
        ) VALUES (
            #{id}, #{spaceId}, #{itemId}, #{roomId}, #{shelfId}, #{boxId}, #{nameOverride}, #{descriptionOverride},
            #{categoryIdOverride}, #{priceAmountOverride}, CAST(#{status} AS inventory_status),
            #{version}, #{createdAt}, #{updatedAt}, #{deletedAt}
        )
        """,
    )
    fun insert(inventory: Inventory)

    @Update(
        """
        UPDATE inventory SET name_override = #{nameOverride}, description_override = #{descriptionOverride},
            category_id_override = #{categoryIdOverride}, price_amount_override = #{priceAmountOverride},
            status = CAST(#{status} AS inventory_status), version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND version = #{version} AND deleted_at IS NULL
        """,
    )
    fun update(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("nameOverride") nameOverride: String?,
        @Param("descriptionOverride") descriptionOverride: String?,
        @Param("categoryIdOverride") categoryIdOverride: UUID?,
        @Param("priceAmountOverride") priceAmountOverride: Int?,
        @Param("status") status: InventoryStatus,
        @Param("version") version: Long,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE inventory SET room_id = #{roomId}, shelf_id = #{shelfId}, box_id = #{boxId},
            version = version + 1, updated_at = #{updatedAt}
        WHERE id = #{id} AND space_id = #{spaceId} AND version = #{version} AND deleted_at IS NULL
        """,
    )
    fun move(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("roomId") roomId: UUID?,
        @Param("shelfId") shelfId: UUID?,
        @Param("boxId") boxId: UUID?,
        @Param("version") version: Long,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        "UPDATE inventory SET category_id_override = #{targetId}, updated_at = #{now} WHERE category_id_override = #{sourceId} AND space_id = #{spaceId} AND deleted_at IS NULL",
    )
    fun reassignCategoryOverride(
        @Param("spaceId") spaceId: UUID,
        @Param("sourceId") sourceId: UUID,
        @Param("targetId") targetId: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        "UPDATE inventory SET deleted_at = #{now}, updated_at = #{now} WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL",
    )
    fun softDelete(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int
}
