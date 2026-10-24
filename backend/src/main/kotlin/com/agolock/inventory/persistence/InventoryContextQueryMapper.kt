@file:Suppress("LongParameterList", "MaxLineLength")

package com.agolock.inventory.persistence

import com.agolock.inventory.domain.Category
import com.agolock.inventory.domain.Item
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.OffsetDateTime
import java.util.UUID

data class LocationTargetRow(
    val type: String,
    val id: UUID,
    val path: String,
    val typeRank: Int,
)

data class ItemImpactCounts(
    val total: Int,
    val affected: Int,
    val nameAffected: Int,
    val descriptionAffected: Int,
    val categoryAffected: Int,
    val priceAffected: Int,
)

@Mapper
interface InventoryContextQueryMapper {
    @Select(
        """<script>
    SELECT c.id, c.space_id, c.name, c.normalized_name, c.color, c.sort_order, c.created_at, c.updated_at, c.deleted_at
    FROM category c WHERE c.space_id = #{spaceId} AND c.deleted_at IS NULL
    AND (c.space_id = #{ownedSpaceId} OR EXISTS (SELECT 1 FROM inventory_access ia JOIN item ci ON ci.id = ia.item_id AND ci.space_id = ia.space_id AND ci.deleted_at IS NULL JOIN share sh ON sh.space_id = ia.space_id
AND sh.grantee_user_id = #{userId} AND sh.revoked_at IS NULL
AND ((sh.room_id IS NOT NULL AND sh.room_id = ia.effective_room_id)
OR (sh.shelf_id IS NOT NULL AND sh.shelf_id = ia.effective_shelf_id))
WHERE ia.space_id = c.space_id AND (ci.category_id = c.id OR ia.category_id_override = c.id)))
    <if test="categoryId != null">AND c.id = #{categoryId}</if>
    <if test="cursorId != null">AND (c.sort_order, c.name, c.id) &gt; (#{sortOrder}, #{name}, #{cursorId})</if>
    ORDER BY c.sort_order, c.name, c.id LIMIT #{limit}
    </script>""",
    )
    fun categories(
        @Param("ownedSpaceId") ownedSpaceId: UUID,
        @Param("userId") userId: UUID,
        @Param("spaceId") spaceId: UUID,
        @Param("categoryId") categoryId: UUID?,
        @Param("sortOrder") sortOrder: Int?,
        @Param("name") name: String?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Category>

    @Select(
        """<script>
    WITH targets AS (
      SELECT 'ROOM' AS type, r.id, r.name AS path, 0 AS type_rank, r.space_id, r.id AS room_id, NULL::uuid AS shelf_id
      FROM room r WHERE r.space_id = #{spaceId} AND r.deleted_at IS NULL
      UNION ALL
      SELECT 'SHELF', s.id, r.name || ' &gt; ' || s.name, 1, s.space_id, r.id, s.id
      FROM shelf s JOIN room r ON r.id = s.room_id AND r.space_id = s.space_id AND r.deleted_at IS NULL
      WHERE s.space_id = #{spaceId} AND s.deleted_at IS NULL
      UNION ALL
      SELECT 'BOX', b.id, r.name || ' &gt; ' || s.name || ' &gt; ' || b.name, 2, b.space_id, r.id, s.id
      FROM box b JOIN shelf s ON s.id = b.shelf_id AND s.space_id = b.space_id AND s.deleted_at IS NULL
      JOIN room r ON r.id = s.room_id AND r.space_id = s.space_id AND r.deleted_at IS NULL
      WHERE b.space_id = #{spaceId} AND b.deleted_at IS NULL
    )
    SELECT t.type, t.id, t.path, t.type_rank FROM targets t
    WHERE (t.space_id = #{ownedSpaceId} OR EXISTS (SELECT 1 FROM share sh WHERE sh.space_id = t.space_id
      AND sh.grantee_user_id = #{userId} AND sh.revoked_at IS NULL AND sh.permission = 'MANAGE'
      AND (sh.room_id = t.room_id OR sh.shelf_id = t.shelf_id)))
    <if test="q != null">AND t.path ILIKE '%' || #{q} || '%'</if>
    <if test="cursorId != null">AND (t.type_rank, t.id) &gt; (#{typeRank}, #{cursorId})</if>
    ORDER BY t.type_rank, t.id LIMIT #{limit}
    </script>""",
    )
    fun targets(
        @Param("ownedSpaceId") ownedSpaceId: UUID,
        @Param("userId") userId: UUID,
        @Param("spaceId") spaceId: UUID,
        @Param("q") q: String?,
        @Param("typeRank") typeRank: Int?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<LocationTargetRow>

    @Select(
        """<script>
    SELECT i.id, i.space_id, i.name, i.description, i.category_id, i.price_amount, i.currency, i.version, i.created_at, i.updated_at, i.deleted_at
    FROM item i WHERE i.deleted_at IS NULL
    AND ((i.space_id = #{ownedSpaceId} AND #{scope} != 'SHARED') OR (i.space_id != #{ownedSpaceId} AND #{scope} != 'OWNED'
    AND EXISTS (SELECT 1 FROM inventory_access ia JOIN share sh ON sh.space_id = ia.space_id
    AND sh.grantee_user_id = #{userId} AND sh.revoked_at IS NULL
    AND (sh.room_id = ia.effective_room_id OR sh.shelf_id = ia.effective_shelf_id) WHERE ia.item_id = i.id AND ia.space_id = i.space_id)))
    <if test="targetSpaceId != null">AND i.space_id = #{targetSpaceId}</if>
    <if test="categoryId != null">AND i.category_id = #{categoryId}</if>
    <if test="q != null">AND i.name ILIKE '%' || #{q} || '%'</if>
    <if test="cursorId != null">AND (i.updated_at, i.id) &lt; (#{cursorUpdated}, #{cursorId})</if>
    ORDER BY i.updated_at DESC, i.id DESC LIMIT #{limit}
    </script>""",
    )
    fun items(
        @Param("ownedSpaceId") ownedSpaceId: UUID,
        @Param("userId") userId: UUID,
        @Param("scope") scope: String,
        @Param("targetSpaceId") targetSpaceId: UUID?,
        @Param("categoryId") categoryId: UUID?,
        @Param("q") q: String?,
        @Param("cursorUpdated") cursorUpdated: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
        @Param("limit") limit: Int,
    ): List<Item>

    @Select(
        """
    SELECT COUNT(*)::int AS total,
      COUNT(*) FILTER (WHERE (#{name} AND name_override IS NULL) OR (#{description} AND description_override IS NULL)
        OR (#{category} AND category_id_override IS NULL) OR (#{price} AND price_amount_override IS NULL))::int AS affected,
      COUNT(*) FILTER (WHERE name_override IS NULL)::int AS name_affected,
      COUNT(*) FILTER (WHERE description_override IS NULL)::int AS description_affected,
      COUNT(*) FILTER (WHERE category_id_override IS NULL)::int AS category_affected,
      COUNT(*) FILTER (WHERE price_amount_override IS NULL)::int AS price_affected
    FROM inventory WHERE space_id = #{spaceId} AND item_id = #{itemId} AND deleted_at IS NULL
    """,
    )
    fun impact(
        @Param("spaceId") spaceId: UUID,
        @Param("itemId") itemId: UUID,
        @Param("name") name: Boolean,
        @Param("description") description: Boolean,
        @Param("category") category: Boolean,
        @Param("price") price: Boolean,
    ): ItemImpactCounts
}
