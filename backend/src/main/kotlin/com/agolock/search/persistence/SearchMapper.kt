@file:Suppress("MaxLineLength", "LongParameterList")

package com.agolock.search.persistence

import com.agolock.inventory.domain.InventoryStatus
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Select
import java.time.OffsetDateTime
import java.util.UUID

data class SearchQuery(
    val ownSpaceId: UUID,
    val actorUserId: UUID,
    val includeOwned: Boolean,
    val includeShared: Boolean,
    val sharedSpaceIds: List<UUID>,
    val roomIds: List<UUID>,
    val shelfIds: List<UUID>,
    val boxIds: List<UUID>,
    val q: String?,
    val statuses: List<InventoryStatus>,
    val roomFilterId: UUID?,
    val shelfFilterId: UUID?,
    val boxFilterId: UUID?,
    val categoryId: UUID?,
    val ownerId: UUID?,
    val hasImage: Boolean?,
    val sort: String,
    val limit: Int,
    val cursorKey: String? = null,
    val cursorItemId: UUID? = null,
    val cursorUpdatedAt: OffsetDateTime? = null,
    val tokens: List<String> = q?.split(' ')?.distinct()?.sortedByDescending { it.length } ?: emptyList(),
)

data class SearchRow(
    val inventoryId: UUID,
    val itemId: UUID,
    val spaceId: UUID,
    val itemName: String,
    val effectiveName: String,
    val effectiveDescription: String?,
    val categoryId: UUID?,
    val categoryName: String?,
    val categoryColor: String?,
    val status: InventoryStatus,
    val roomName: String?,
    val shelfName: String?,
    val boxName: String?,
    val ownerName: String?,
    val updatedAt: OffsetDateTime,
    val inventoryCount: Int,
    val sortKey: String,
    val groupUpdatedAt: OffsetDateTime,
    val groupPrimaryImageId: UUID?,
)

@Mapper
interface SearchMapper {
    @Select(
        """
        <script>
        WITH
        <if test="q != null and q != ''">
          matched_rooms AS (
            SELECT id, space_id FROM room WHERE deleted_at IS NULL AND lower(name) LIKE '%' || #{tokens[0]} || '%'
          ), matched_shelves AS (
            SELECT id, space_id FROM shelf s WHERE deleted_at IS NULL
              AND (lower(name) LIKE '%' || #{tokens[0]} || '%' OR EXISTS (SELECT 1 FROM matched_rooms r WHERE r.id = s.room_id))
          ), matched_boxes AS (
            SELECT id, space_id FROM box b WHERE deleted_at IS NULL
              AND (lower(name) LIKE '%' || #{tokens[0]} || '%' OR EXISTS (SELECT 1 FROM matched_shelves s WHERE s.id = b.shelf_id))
          ), keyword_matches AS MATERIALIZED (
            SELECT i.id AS inventory_id FROM inventory i WHERE i.deleted_at IS NULL AND i.name_override IS NOT NULL AND (lower(i.name_override) LIKE '%' || #{tokens[0]} || '%' OR lower(i.name_override) % #{tokens[0]})
            UNION
            SELECT i.id FROM item it JOIN inventory i ON i.item_id = it.id AND i.space_id = it.space_id WHERE it.deleted_at IS NULL AND i.name_override IS NULL AND (lower(it.name) LIKE '%' || #{tokens[0]} || '%' OR lower(it.name) % #{tokens[0]})
            UNION
            SELECT i.id FROM inventory i WHERE i.deleted_at IS NULL AND i.description_override IS NOT NULL AND lower(i.description_override) LIKE '%' || #{tokens[0]} || '%'
            UNION
            SELECT i.id FROM item it JOIN inventory i ON i.item_id = it.id AND i.space_id = it.space_id WHERE it.deleted_at IS NULL AND i.description_override IS NULL AND lower(it.description) LIKE '%' || #{tokens[0]} || '%'
            UNION
            SELECT i.id FROM category cat JOIN inventory i ON i.space_id = cat.space_id AND i.category_id_override = cat.id WHERE cat.deleted_at IS NULL AND lower(cat.name) LIKE '%' || #{tokens[0]} || '%'
            UNION
            SELECT i.id FROM category cat JOIN item it ON it.space_id = cat.space_id AND it.category_id = cat.id JOIN inventory i ON i.space_id = it.space_id AND i.item_id = it.id WHERE cat.deleted_at IS NULL AND i.category_id_override IS NULL AND lower(cat.name) LIKE '%' || #{tokens[0]} || '%'
            UNION
            SELECT i.id FROM matched_rooms r JOIN inventory i ON i.space_id = r.space_id AND i.room_id = r.id
            UNION
            SELECT i.id FROM matched_shelves s JOIN inventory i ON i.space_id = s.space_id AND i.shelf_id = s.id
            UNION
            SELECT i.id FROM matched_boxes b JOIN inventory i ON i.space_id = b.space_id AND i.box_id = b.id
          ),
        </if>
        candidate AS NOT MATERIALIZED (
        SELECT
            i.inventory_id AS inventory_id,
            i.item_id,
            i.space_id,
            it.name AS item_name,
            COALESCE(i.name_override, it.name) AS effective_name,
            COALESCE(i.description_override, it.description) AS effective_description,
            it.category_id,
            master_cat.name AS category_name,
            master_cat.color AS category_color,
            i.status,
            rm.name AS room_name,
            s.name AS shelf_name,
            b.name AS box_name,
            u.display_name AS owner_name,
            i.updated_at,
            CASE i.status WHEN 'IN_USE' THEN 0 WHEN 'STORED' THEN 1 ELSE 2 END AS status_rank,
            COALESCE(v.last_viewed_at, '1970-01-01'::timestamptz) AS viewed_at,
            CAST(
              CASE WHEN CAST(#{q} AS text) IS NULL THEN 0 ELSE
                CASE WHEN lower(COALESCE(i.name_override, it.name)) = #{q} THEN 100 ELSE 0 END
                + CASE WHEN lower(COALESCE(i.name_override, it.name)) LIKE #{q} || '%' THEN 50 ELSE 0 END
                + CASE WHEN lower(COALESCE(i.name_override, it.name)) LIKE '%' || #{q} || '%' THEN 30 ELSE 0 END
                + similarity(lower(COALESCE(i.name_override, it.name)), #{q}) * 20
                + CASE WHEN lower(COALESCE(i.description_override, it.description)) LIKE '%' || #{q} || '%' THEN 10 ELSE 0 END
              END
              + greatest(0, 10 - floor(extract(epoch FROM (CURRENT_TIMESTAMP - i.updated_at)) / 86400))
              + least(10, CASE WHEN v.last_viewed_at >= CURRENT_TIMESTAMP - interval '90 days' THEN v.view_count ELSE 0 END)
            AS numeric(10,4)) AS score
        FROM inventory_access i
        JOIN item it ON it.id = i.item_id AND it.space_id = i.space_id
        JOIN space sp ON sp.id = i.space_id
        JOIN app_user u ON u.id = sp.owner_user_id
        LEFT JOIN room rm ON rm.id = i.effective_room_id AND rm.deleted_at IS NULL
        LEFT JOIN shelf s ON s.id = i.effective_shelf_id AND s.deleted_at IS NULL
        LEFT JOIN box b ON b.id = i.box_id AND b.deleted_at IS NULL
        LEFT JOIN category cat ON cat.id = COALESCE(i.category_id_override, it.category_id) AND cat.space_id = i.space_id
        LEFT JOIN category master_cat ON master_cat.id = it.category_id AND master_cat.space_id = i.space_id
        <if test="hasImage != null">
          LEFT JOIN (
            SELECT DISTINCT item_id FROM image
            WHERE status = 'READY' AND deleted_at IS NULL AND item_id IS NOT NULL
          ) item_image_filter ON item_image_filter.item_id = i.item_id
          LEFT JOIN (
            SELECT DISTINCT inventory_id FROM image
            WHERE status = 'READY' AND deleted_at IS NULL AND inventory_id IS NOT NULL
          ) inventory_image_filter ON inventory_image_filter.inventory_id = i.inventory_id
        </if>
        LEFT JOIN user_item_view v ON v.user_id = #{actorUserId} AND v.item_id = i.item_id
        WHERE it.deleted_at IS NULL
          AND sp.deleted_at IS NULL
          AND rm.id IS NOT NULL
          AND i.status IN
          <foreach collection="statuses" item="status" open="(" separator="," close=")">
            CAST(#{status} AS inventory_status)
          </foreach>
          <choose>
            <when test="includeOwned and includeShared and sharedSpaceIds.size() > 0 and (roomIds.size() > 0 or shelfIds.size() > 0 or boxIds.size() > 0)">
              AND (
                i.space_id = #{ownSpaceId}
                OR (
                  i.space_id IN
                  <foreach collection="sharedSpaceIds" item="spaceId" open="(" separator="," close=")">
                    #{spaceId}
                  </foreach>
                  AND (
                    <trim suffixOverrides="OR">
                      <if test="roomIds.size() > 0">
                        i.effective_room_id IN
                        <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">
                          #{roomId}
                        </foreach>
                        OR
                      </if>
                      <if test="shelfIds.size() > 0">
                        i.effective_shelf_id IN
                        <foreach collection="shelfIds" item="shelfId" open="(" separator="," close=")">
                          #{shelfId}
                        </foreach>
                        OR
                      </if>
                      <if test="boxIds.size() > 0">
                        i.box_id IN
                        <foreach collection="boxIds" item="boxId" open="(" separator="," close=")">
                          #{boxId}
                        </foreach>
                      </if>
                    </trim>
                  )
                )
              )
            </when>
            <when test="includeOwned and includeShared">
              AND i.space_id = #{ownSpaceId}
            </when>
            <when test="includeOwned">
              AND i.space_id = #{ownSpaceId}
            </when>
            <when test="includeShared and sharedSpaceIds.size() > 0 and (roomIds.size() > 0 or shelfIds.size() > 0 or boxIds.size() > 0)">
              AND (
                i.space_id IN
                <foreach collection="sharedSpaceIds" item="spaceId" open="(" separator="," close=")">
                  #{spaceId}
                </foreach>
                AND (
                  <trim suffixOverrides="OR">
                    <if test="roomIds.size() > 0">
                      i.effective_room_id IN
                      <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">
                        #{roomId}
                      </foreach>
                      OR
                    </if>
                    <if test="shelfIds.size() > 0">
                      i.effective_shelf_id IN
                      <foreach collection="shelfIds" item="shelfId" open="(" separator="," close=")">
                        #{shelfId}
                      </foreach>
                      OR
                    </if>
                    <if test="boxIds.size() > 0">
                      i.box_id IN
                      <foreach collection="boxIds" item="boxId" open="(" separator="," close=")">
                        #{boxId}
                      </foreach>
                    </if>
                  </trim>
                )
              )
            </when>
            <otherwise>
              AND 1 = 0
            </otherwise>
          </choose>
          <if test="q != null and q != ''">
            AND i.inventory_id IN (SELECT inventory_id FROM keyword_matches)
            <foreach collection="tokens" item="token">
              AND (
                (lower(COALESCE(i.name_override, it.name)) LIKE '%' || #{token} || '%' OR lower(COALESCE(i.name_override, it.name)) % #{token})
                OR lower(COALESCE(i.description_override, it.description)) LIKE '%' || #{token} || '%'
                OR lower(rm.name) LIKE '%' || #{token} || '%'
                OR lower(s.name) LIKE '%' || #{token} || '%'
                OR lower(b.name) LIKE '%' || #{token} || '%'
                OR lower(cat.name) LIKE '%' || #{token} || '%'
              )
            </foreach>
          </if>
          <if test="roomFilterId != null">
            AND i.effective_room_id = #{roomFilterId}
          </if>
          <if test="shelfFilterId != null">
            AND i.effective_shelf_id = #{shelfFilterId}
          </if>
          <if test="boxFilterId != null">AND i.box_id = #{boxFilterId}</if>
          <if test="categoryId != null">
            AND COALESCE(i.category_id_override, it.category_id) = #{categoryId}
          </if>
          <if test="ownerId != null">AND sp.owner_user_id = #{ownerId}</if>
          <if test="hasImage == true">AND (item_image_filter.item_id IS NOT NULL OR inventory_image_filter.inventory_id IS NOT NULL)</if>
          <if test="hasImage == false">AND item_image_filter.item_id IS NULL AND inventory_image_filter.inventory_id IS NULL</if>
        ), grouped AS (
          SELECT item_id, item_name, count(*)::integer AS inventory_count,
                 max(updated_at) AS group_updated_at, max(score) AS score_key,
                 lower(item_name) AS name_key, max(viewed_at) AS viewed_key,
                 min(status_rank) AS status_key
          FROM candidate
          GROUP BY item_id, item_name
        ), keyed AS (
          SELECT grouped.*,
            <choose>
              <when test="sort == 'NAME_ASC'">name_key</when>
              <when test="sort == 'STATUS_ASC'">status_key::text</when>
              <when test="sort == 'VIEWED_DESC'">to_char(viewed_key AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')</when>
              <when test="sort == 'RELEVANCE'">score_key::text</when>
              <otherwise>to_char(group_updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')</otherwise>
            </choose> AS sort_key
          FROM grouped
        ), page AS (
          SELECT * FROM keyed
          <if test="cursorItemId != null">
            WHERE
            <choose>
              <when test="sort == 'NAME_ASC'">
                name_key &gt; #{cursorKey} OR (name_key = #{cursorKey} AND item_id &gt; #{cursorItemId})
              </when>
              <when test="sort == 'STATUS_ASC'">
                status_key &gt; CAST(#{cursorKey} AS integer)
                OR (status_key = CAST(#{cursorKey} AS integer) AND item_id &gt; #{cursorItemId})
              </when>
              <when test="sort == 'VIEWED_DESC'">
                viewed_key &lt; CAST(#{cursorKey} AS timestamptz)
                OR (viewed_key = CAST(#{cursorKey} AS timestamptz) AND item_id &gt; #{cursorItemId})
              </when>
              <when test="sort == 'RELEVANCE'">
                score_key &lt; CAST(#{cursorKey} AS numeric)
                OR (score_key = CAST(#{cursorKey} AS numeric) AND (
                  group_updated_at &lt; #{cursorUpdatedAt}
                  OR (group_updated_at = #{cursorUpdatedAt} AND item_id &gt; #{cursorItemId})
                ))
              </when>
              <otherwise>
                group_updated_at &lt; CAST(#{cursorKey} AS timestamptz)
                OR (group_updated_at = CAST(#{cursorKey} AS timestamptz) AND item_id &gt; #{cursorItemId})
              </otherwise>
            </choose>
          </if>
          ORDER BY
            <choose>
              <when test="sort == 'NAME_ASC'">name_key ASC,</when>
              <when test="sort == 'STATUS_ASC'">status_key ASC,</when>
              <when test="sort == 'VIEWED_DESC'">viewed_key DESC,</when>
              <when test="sort == 'RELEVANCE'">score_key DESC, group_updated_at DESC,</when>
              <otherwise>group_updated_at DESC,</otherwise>
            </choose>
            item_id ASC
          LIMIT #{limit}
        )
        SELECT c.inventory_id, c.item_id, c.space_id, c.item_name, c.effective_name,
               c.effective_description, c.category_id, c.category_name, c.category_color,
               c.status, c.room_name, c.shelf_name, c.box_name,
               c.owner_name, c.updated_at, p.inventory_count, p.sort_key, p.group_updated_at,
               COALESCE(master_image.id, inventory_image.id) AS group_primary_image_id
        FROM page p
        JOIN LATERAL (
          SELECT * FROM candidate c WHERE c.item_id = p.item_id
          ORDER BY status_rank ASC, updated_at DESC, inventory_id ASC LIMIT 6
        ) c ON true
        LEFT JOIN LATERAL (
          SELECT id FROM image
          WHERE item_id = p.item_id AND status = 'READY' AND deleted_at IS NULL
          ORDER BY is_primary DESC, created_at DESC, id ASC LIMIT 1
        ) master_image ON true
        LEFT JOIN LATERAL (
          SELECT id FROM image
          WHERE inventory_id = c.inventory_id AND status = 'READY' AND deleted_at IS NULL
          ORDER BY is_primary DESC, created_at DESC, id ASC LIMIT 1
        ) inventory_image ON true
        ORDER BY
          <choose>
            <when test="sort == 'NAME_ASC'">p.name_key ASC,</when>
            <when test="sort == 'STATUS_ASC'">p.status_key ASC,</when>
            <when test="sort == 'VIEWED_DESC'">p.viewed_key DESC,</when>
            <when test="sort == 'RELEVANCE'">p.score_key DESC, p.group_updated_at DESC,</when>
            <otherwise>p.group_updated_at DESC,</otherwise>
          </choose>
          p.item_id ASC, c.status_rank ASC, c.updated_at DESC, c.inventory_id ASC
        </script>
        """,
    )
    fun search(query: SearchQuery): List<SearchRow>
}
