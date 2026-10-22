package com.agolock.search.persistence

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
        SELECT v.item_id, it.name AS item_name, img.id AS primary_image_id,
               v.view_count, v.last_viewed_at
        FROM user_item_view v
        JOIN item it ON it.id = v.item_id AND it.space_id = v.space_id
        LEFT JOIN LATERAL (
          SELECT image.id
          FROM image
          WHERE image.status = 'READY' AND image.deleted_at IS NULL
            AND (image.item_id = v.item_id OR image.inventory_id IN (
              SELECT id FROM inventory WHERE item_id = v.item_id AND deleted_at IS NULL
            ))
          ORDER BY image.is_primary DESC, image.created_at DESC
          LIMIT 1
        ) img ON true
        WHERE v.user_id = #{userId}
          AND it.deleted_at IS NULL
          AND v.last_viewed_at >= #{since}
        ORDER BY v.view_count DESC, v.last_viewed_at DESC
        LIMIT 10
        """,
    )
    fun list(
        @Param("userId") userId: UUID,
        @Param("since") since: OffsetDateTime,
    ): List<FrequentItemRow>
}
