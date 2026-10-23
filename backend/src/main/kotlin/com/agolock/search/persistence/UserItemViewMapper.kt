package com.agolock.search.persistence

import com.agolock.search.domain.SearchAccessScope
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.time.OffsetDateTime
import java.util.UUID

data class FrequentItemRow(
    val itemId: UUID,
    val itemName: String,
    val primaryImageId: UUID?,
    val viewCount: Long,
    val lastViewedAt: OffsetDateTime,
)

@Mapper
interface UserItemViewMapper {
    @Insert(
        """
        INSERT INTO user_item_view (user_id, item_id, space_id, view_count, last_viewed_at)
        VALUES (#{userId}, #{itemId}, #{spaceId}, 1, #{now})
        ON CONFLICT (user_id, item_id) DO UPDATE SET
          view_count = CASE
            WHEN user_item_view.last_viewed_at <= #{cutoff} THEN user_item_view.view_count + 1
            ELSE user_item_view.view_count
          END,
          last_viewed_at = CASE
            WHEN user_item_view.last_viewed_at <= #{cutoff} THEN #{now}
            ELSE user_item_view.last_viewed_at
          END
        """,
    )
    fun record(
        @Param("userId") userId: UUID,
        @Param("itemId") itemId: UUID,
        @Param("spaceId") spaceId: UUID,
        @Param("now") now: OffsetDateTime,
        @Param("cutoff") cutoff: OffsetDateTime,
    )

    @Select(
        """
        <script>
        WITH accessible_inventory AS (
          SELECT i.inventory_id AS id, i.item_id
          FROM inventory_access i
          JOIN space sp ON sp.id = i.space_id AND sp.deleted_at IS NULL
          WHERE i.status != 'DISPOSED'
            AND (
              i.space_id = #{access.ownSpaceId}
              <if test="access.roomIds.size() > 0">
                OR i.effective_room_id IN
                <foreach collection="access.roomIds" item="id" open="(" separator="," close=")">#{id}</foreach>
              </if>
              <if test="access.shelfIds.size() > 0">
                OR i.effective_shelf_id IN
                <foreach collection="access.shelfIds" item="id" open="(" separator="," close=")">#{id}</foreach>
              </if>
              <if test="access.boxIds.size() > 0">
                OR i.box_id IN
                <foreach collection="access.boxIds" item="id" open="(" separator="," close=")">#{id}</foreach>
              </if>
            )
        )
        SELECT v.item_id, it.name AS item_name, img.id AS primary_image_id,
               v.view_count, v.last_viewed_at
        FROM user_item_view v
        JOIN item it ON it.id = v.item_id AND it.space_id = v.space_id
        LEFT JOIN LATERAL (
          SELECT image.id
          FROM image
          WHERE image.status = 'READY' AND image.deleted_at IS NULL
            AND (image.item_id = v.item_id OR image.inventory_id IN (
              SELECT id FROM accessible_inventory WHERE item_id = v.item_id
            ))
          ORDER BY image.is_primary DESC, image.created_at DESC
          LIMIT 1
        ) img ON true
        WHERE v.user_id = #{userId}
          AND it.deleted_at IS NULL
          AND v.last_viewed_at &gt;= #{since}
          AND EXISTS (SELECT 1 FROM accessible_inventory WHERE item_id = v.item_id)
        ORDER BY v.view_count DESC, v.last_viewed_at DESC
        LIMIT 10
        </script>
        """,
    )
    fun list(
        @Param("userId") userId: UUID,
        @Param("since") since: OffsetDateTime,
        @Param("access") access: SearchAccessScope,
    ): List<FrequentItemRow>
}
