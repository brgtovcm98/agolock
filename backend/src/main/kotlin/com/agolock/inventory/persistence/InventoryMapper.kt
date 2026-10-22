@file:Suppress("LongParameterList", "MaxLineLength", "TooManyFunctions")

package com.agolock.inventory.persistence

import com.agolock.common.persistence.CountRow
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
    @Select(
        """
        SELECT id, space_id, item_id,
                room_id, shelf_id, box_id,
                name_override, description_override, category_id_override,
                price_amount_override, status, version,
                created_at, updated_at, deleted_at
        FROM inventory
        WHERE id = #{id} AND deleted_at IS NULL
        """,
    )
    fun findByIdAny(
        @Param("id") id: UUID,
    ): Inventory?

    @Select(
        """
        SELECT id, space_id, item_id,
                room_id, shelf_id, box_id,
                name_override, description_override, category_id_override,
                price_amount_override, status, version,
                created_at, updated_at, deleted_at
        FROM inventory
        WHERE id = #{id} AND space_id = #{spaceId} AND deleted_at IS NULL
        """,
    )
    fun findById(
        @Param("spaceId") spaceId: UUID,
        @Param("id") id: UUID,
    ): Inventory?

    @Select(
        """
        <script>
        SELECT a.inventory_id AS id,
               a.item_id,
               a.status,
               a.version,
               a.updated_at,
               a.created_at,
               a.name_override,
               a.description_override,
               a.category_id_override,
               a.price_amount_override,
               it.name AS item_name,
               it.description AS item_description,
               it.category_id AS item_category_id,
               it.price_amount AS item_price_amount,
               COALESCE(a.room_id, sh.room_id, bs.room_id) AS display_room_id,
               COALESCE(a.shelf_id, bx.shelf_id) AS display_shelf_id,
               a.box_id AS display_box_id,
               rm.name AS room_name,
               COALESCE(sh.name, bs.name) AS shelf_name,
               bx.name AS box_name,
               CASE WHEN a.space_id = #{spaceId} THEN 'OWNER'
                    WHEN g.rank = 3 THEN 'MANAGE'
                    WHEN g.rank = 2 THEN 'EDIT'
                    WHEN g.rank = 1 THEN 'VIEW'
                    ELSE 'NONE' END AS effective_permission
        FROM inventory_access a
        JOIN item it ON it.id = a.item_id AND it.space_id = a.space_id AND it.deleted_at IS NULL
        LEFT JOIN shelf sh ON sh.id = a.shelf_id
        LEFT JOIN box bx ON bx.id = a.box_id
        LEFT JOIN shelf bs ON bs.id = bx.shelf_id
        LEFT JOIN room rm ON rm.id = COALESCE(a.room_id, sh.room_id, bs.room_id)
        LEFT JOIN LATERAL (
          SELECT max(CASE s.permission WHEN 'MANAGE' THEN 3 WHEN 'EDIT' THEN 2 ELSE 1 END) AS rank
          FROM share s
          WHERE s.grantee_user_id = #{actorUserId}
            AND s.space_id = a.space_id
            AND s.revoked_at IS NULL
            AND (s.room_id = a.effective_room_id OR s.shelf_id = a.effective_shelf_id)
        ) g ON a.space_id &lt;&gt; #{spaceId}
        WHERE a.status IN
        <foreach collection="statuses" item="status" open="(" separator="," close=")">
          CAST(#{status} AS inventory_status)
        </foreach>
        <choose>
          <when test="scope == 'OWNED'">AND a.space_id = #{spaceId}</when>
          <when test="scope == 'SHARED'">AND a.space_id &lt;&gt; #{spaceId} AND g.rank IS NOT NULL</when>
          <otherwise>AND (a.space_id = #{spaceId} OR g.rank IS NOT NULL)</otherwise>
        </choose>
        <if test="itemId != null">AND a.item_id = #{itemId}</if>
        <if test="cursorUpdated != null">AND (a.updated_at, a.inventory_id) &lt; (#{cursorUpdated}, #{cursorId})</if>
        <if test="roomId != null">AND a.room_id = #{roomId} AND a.shelf_id IS NULL AND a.box_id IS NULL</if>
        <if test="shelfId != null">AND a.shelf_id = #{shelfId} AND a.box_id IS NULL</if>
        <if test="boxId != null">AND a.box_id = #{boxId}</if>
        ORDER BY a.updated_at DESC, a.inventory_id DESC
        LIMIT #{limit}
        </script>
        """,
    )
    fun listAccessible(
        @Param("spaceId") spaceId: UUID,
        @Param("actorUserId") actorUserId: UUID,
        @Param("scope") scope: String,
        @Param("statuses") statuses: List<InventoryStatus>,
        @Param("itemId") itemId: UUID?,
        @Param("roomId") roomId: UUID?,
        @Param("shelfId") shelfId: UUID?,
        @Param("boxId") boxId: UUID?,
        @Param("cursorUpdated") cursorUpdated: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<InventoryListRow>

    @Select(
        """
        SELECT id, space_id, item_id,
                room_id, shelf_id, box_id,
                name_override, description_override, category_id_override,
                price_amount_override, status, version,
                created_at, updated_at, deleted_at
        FROM inventory
        WHERE space_id = #{spaceId} AND item_id = #{itemId} AND deleted_at IS NULL AND status <> 'DISPOSED'
        """,
    )
    fun listByItem(
        @Param("spaceId") spaceId: UUID,
        @Param("itemId") itemId: UUID,
    ): List<Inventory>

    @Select(
        """
        SELECT id, space_id, item_id,
                room_id, shelf_id, box_id,
                name_override, description_override, category_id_override,
                price_amount_override, status, version,
                created_at, updated_at, deleted_at
        FROM inventory
        WHERE space_id = #{spaceId} AND item_id = #{itemId} AND deleted_at IS NULL
        """,
    )
    fun listAllByItem(
        @Param("spaceId") spaceId: UUID,
        @Param("itemId") itemId: UUID,
    ): List<Inventory>

    @Select(
        """
        <script>
        SELECT a.item_id, MAX(CASE s.permission WHEN 'MANAGE' THEN 3 WHEN 'EDIT' THEN 2 ELSE 1 END) AS grant_rank
        FROM inventory_access a
        JOIN share s ON s.space_id = a.space_id
          AND s.grantee_user_id = #{actorUserId}
          AND s.revoked_at IS NULL
          AND (s.room_id = a.effective_room_id OR s.shelf_id = a.effective_shelf_id)
        WHERE a.space_id &lt;&gt; #{spaceId}
          AND a.item_id IN
          <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
            #{itemId}
          </foreach>
        GROUP BY a.item_id
        </script>
        """,
    )
    fun maxGrantRankByItemIds(
        @Param("spaceId") spaceId: UUID,
        @Param("actorUserId") actorUserId: UUID,
        @Param("itemIds") itemIds: List<UUID>,
    ): List<ItemGrantRow>

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
        """
        <script>
        SELECT room_id AS id, COUNT(*) AS cnt
        FROM inventory
        WHERE space_id = #{spaceId} AND deleted_at IS NULL
          AND room_id IN
          <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">
            #{roomId}
          </foreach>
        GROUP BY room_id
        </script>
        """,
    )
    fun countByRoomIds(
        @Param("spaceId") spaceId: UUID,
        @Param("roomIds") roomIds: List<UUID>,
    ): List<CountRow>

    @Select(
        """
        <script>
        SELECT box_id AS id, COUNT(*) AS cnt
        FROM inventory
        WHERE space_id = #{spaceId} AND deleted_at IS NULL
          AND box_id IN
          <foreach collection="boxIds" item="boxId" open="(" separator="," close=")">
            #{boxId}
          </foreach>
        GROUP BY box_id
        </script>
        """,
    )
    fun countByBoxIds(
        @Param("spaceId") spaceId: UUID,
        @Param("boxIds") boxIds: List<UUID>,
    ): List<CountRow>

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
