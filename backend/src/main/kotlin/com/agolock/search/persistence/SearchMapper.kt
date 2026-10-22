@file:Suppress("MaxLineLength", "LongParameterList")

package com.agolock.search.persistence

import com.agolock.inventory.domain.InventoryStatus
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
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
    // keyset 페이지에서 그룹 Inventory를 재조회할 때만 채웁니다.
    val itemIds: List<UUID>? = null,
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
    val primaryImageId: UUID?,
    val ownerName: String?,
    val updatedAt: OffsetDateTime,
)

@Mapper
interface SearchMapper {
    @Select(
        """
        <script>
        SELECT
            i.id AS inventory_id,
            i.item_id,
            i.space_id,
            it.name AS item_name,
            COALESCE(i.name_override, it.name) AS effective_name,
            COALESCE(i.description_override, it.description) AS effective_description,
            COALESCE(i.category_id_override, it.category_id) AS category_id,
            cat.name AS category_name,
            cat.color AS category_color,
            i.status,
            COALESCE(rm.name, sr.name, br.name) AS room_name,
            COALESCE(s.name, bs.name) AS shelf_name,
            b.name AS box_name,
            img.id AS primary_image_id,
            u.display_name AS owner_name,
            i.updated_at
        FROM inventory i
        JOIN item it ON it.id = i.item_id AND it.space_id = i.space_id
        JOIN space sp ON sp.id = i.space_id
        JOIN app_user u ON u.id = sp.owner_user_id
        LEFT JOIN room rm ON rm.id = i.room_id
        LEFT JOIN shelf s ON s.id = i.shelf_id
        LEFT JOIN room sr ON sr.id = s.room_id
        LEFT JOIN box b ON b.id = i.box_id
        LEFT JOIN shelf bs ON bs.id = b.shelf_id
        LEFT JOIN room br ON br.id = bs.room_id
        LEFT JOIN category cat ON cat.id = COALESCE(i.category_id_override, it.category_id) AND cat.space_id = i.space_id
        LEFT JOIN LATERAL (
            SELECT image.id
            FROM image
            WHERE image.status = 'READY' AND image.deleted_at IS NULL
              AND (image.item_id = i.item_id OR image.inventory_id = i.id)
            ORDER BY (image.inventory_id = i.id) DESC, image.is_primary DESC, image.created_at DESC
            LIMIT 1
        ) img ON true
        LEFT JOIN user_item_view v ON v.user_id = #{actorUserId} AND v.item_id = i.item_id
        WHERE i.deleted_at IS NULL
          AND it.deleted_at IS NULL
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
                        i.room_id IN
                        <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">
                          #{roomId}
                        </foreach>
                        OR
                      </if>
                      <if test="shelfIds.size() > 0">
                        i.shelf_id IN
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
                      i.room_id IN
                      <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">
                        #{roomId}
                      </foreach>
                      OR
                    </if>
                    <if test="shelfIds.size() > 0">
                      i.shelf_id IN
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
          <if test="itemIds != null and itemIds.size() > 0">
            AND i.item_id IN
            <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
              #{itemId}
            </foreach>
          </if>
          <if test="q != null and q != ''">
            AND (
              lower(COALESCE(i.name_override, it.name)) LIKE '%' || lower(#{q}) || '%'
              OR lower(COALESCE(i.description_override, it.description)) LIKE '%' || lower(#{q}) || '%'
              OR lower(COALESCE(rm.name, sr.name, br.name)) LIKE '%' || lower(#{q}) || '%'
              OR lower(COALESCE(s.name, bs.name)) LIKE '%' || lower(#{q}) || '%'
              OR lower(b.name) LIKE '%' || lower(#{q}) || '%'
              OR lower(cat.name) LIKE '%' || lower(#{q}) || '%'
            )
          </if>
          <if test="roomFilterId != null">
            AND COALESCE(i.room_id, s.room_id, bs.room_id) = #{roomFilterId}
          </if>
          <if test="shelfFilterId != null">
            AND COALESCE(i.shelf_id, b.shelf_id) = #{shelfFilterId}
          </if>
          <if test="boxFilterId != null">AND i.box_id = #{boxFilterId}</if>
          <if test="categoryId != null">
            AND COALESCE(i.category_id_override, it.category_id) = #{categoryId}
          </if>
          <if test="ownerId != null">AND sp.owner_user_id = #{ownerId}</if>
          <if test="hasImage == true">AND img.id IS NOT NULL</if>
          <if test="hasImage == false">AND img.id IS NULL</if>
        ORDER BY
          <choose>
            <when test="sort == 'UPDATED_DESC'">i.updated_at DESC, i.id DESC</when>
            <when test="sort == 'NAME_ASC'">effective_name ASC, i.id ASC</when>
            <when test="sort == 'VIEWED_DESC'">COALESCE(v.view_count, 0) DESC, i.updated_at DESC, i.id DESC</when>
            <when test="sort == 'STATUS_ASC'">CASE i.status WHEN 'IN_USE' THEN 0 WHEN 'STORED' THEN 1 ELSE 2 END ASC, i.updated_at DESC, i.id DESC</when>
            <otherwise>
              (CASE
                WHEN #{q} IS NULL OR #{q} = '' THEN 0
                WHEN lower(COALESCE(i.name_override, it.name)) = lower(#{q}) THEN 100
                WHEN lower(COALESCE(i.name_override, it.name)) LIKE lower(#{q}) || '%' THEN 50
                WHEN lower(COALESCE(i.name_override, it.name)) LIKE '%' || lower(#{q}) || '%' THEN 30
                WHEN lower(COALESCE(i.description_override, it.description)) LIKE '%' || lower(#{q}) || '%' THEN 10
                ELSE 0
              END + COALESCE(similarity(COALESCE(i.name_override, it.name), #{q}), 0) * 20) DESC,
              i.updated_at DESC,
              i.id DESC
            </otherwise>
          </choose>
        LIMIT #{limit}
        </script>
        """,
    )
    fun search(query: SearchQuery): List<SearchRow>

    @Select(
        """
        <script>
        WITH hit AS (
            SELECT a.item_id, a.updated_at
            FROM inventory_access a
            JOIN item it ON it.id = a.item_id AND it.space_id = a.space_id
            LEFT JOIN room rm ON rm.id = a.effective_room_id
            LEFT JOIN shelf sh ON sh.id = a.effective_shelf_id
            LEFT JOIN box bx ON bx.id = a.box_id
            JOIN space sp ON sp.id = a.space_id
            JOIN app_user u ON u.id = sp.owner_user_id
            LEFT JOIN category cat ON cat.id = COALESCE(a.category_id_override, it.category_id) AND cat.space_id = a.space_id
            WHERE it.deleted_at IS NULL
              AND a.status IN
              <foreach collection="query.statuses" item="status" open="(" separator="," close=")">
                CAST(#{status} AS inventory_status)
              </foreach>
              <choose>
                <when test="query.includeOwned and query.includeShared and query.sharedSpaceIds.size() > 0 and (query.roomIds.size() > 0 or query.shelfIds.size() > 0 or query.boxIds.size() > 0)">
                  AND (
                    a.space_id = #{query.ownSpaceId}
                    OR (
                      a.space_id IN
                      <foreach collection="query.sharedSpaceIds" item="spaceId" open="(" separator="," close=")">
                        #{spaceId}
                      </foreach>
                      AND (
                        <trim suffixOverrides="OR">
                          <if test="query.roomIds.size() > 0">
                            a.room_id IN
                            <foreach collection="query.roomIds" item="roomId" open="(" separator="," close=")">
                              #{roomId}
                            </foreach>
                            OR
                          </if>
                          <if test="query.shelfIds.size() > 0">
                            a.shelf_id IN
                            <foreach collection="query.shelfIds" item="shelfId" open="(" separator="," close=")">
                              #{shelfId}
                            </foreach>
                            OR
                          </if>
                          <if test="query.boxIds.size() > 0">
                            a.box_id IN
                            <foreach collection="query.boxIds" item="boxId" open="(" separator="," close=")">
                              #{boxId}
                            </foreach>
                          </if>
                        </trim>
                      )
                    )
                  )
                </when>
                <when test="query.includeOwned and query.includeShared">
                  AND a.space_id = #{query.ownSpaceId}
                </when>
                <when test="query.includeOwned">
                  AND a.space_id = #{query.ownSpaceId}
                </when>
                <when test="query.includeShared and query.sharedSpaceIds.size() > 0 and (query.roomIds.size() > 0 or query.shelfIds.size() > 0 or query.boxIds.size() > 0)">
                  AND (
                    a.space_id IN
                    <foreach collection="query.sharedSpaceIds" item="spaceId" open="(" separator="," close=")">
                      #{spaceId}
                    </foreach>
                    AND (
                      <trim suffixOverrides="OR">
                        <if test="query.roomIds.size() > 0">
                          a.room_id IN
                          <foreach collection="query.roomIds" item="roomId" open="(" separator="," close=")">
                            #{roomId}
                          </foreach>
                          OR
                        </if>
                        <if test="query.shelfIds.size() > 0">
                          a.shelf_id IN
                          <foreach collection="query.shelfIds" item="shelfId" open="(" separator="," close=")">
                            #{shelfId}
                          </foreach>
                          OR
                        </if>
                        <if test="query.boxIds.size() > 0">
                          a.box_id IN
                          <foreach collection="query.boxIds" item="boxId" open="(" separator="," close=")">
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
              <if test="query.q != null and query.q != ''">
                AND (
                  lower(COALESCE(a.name_override, it.name)) LIKE '%' || lower(#{query.q}) || '%'
                  OR lower(COALESCE(a.description_override, it.description)) LIKE '%' || lower(#{query.q}) || '%'
                  OR lower(rm.name) LIKE '%' || lower(#{query.q}) || '%'
                  OR lower(sh.name) LIKE '%' || lower(#{query.q}) || '%'
                  OR lower(bx.name) LIKE '%' || lower(#{query.q}) || '%'
                  OR lower(cat.name) LIKE '%' || lower(#{query.q}) || '%'
                )
              </if>
              <if test="query.roomFilterId != null">AND a.effective_room_id = #{query.roomFilterId}</if>
              <if test="query.shelfFilterId != null">AND a.effective_shelf_id = #{query.shelfFilterId}</if>
              <if test="query.boxFilterId != null">AND a.box_id = #{query.boxFilterId}</if>
              <if test="query.categoryId != null">
                AND COALESCE(a.category_id_override, it.category_id) = #{query.categoryId}
              </if>
              <if test="query.ownerId != null">AND sp.owner_user_id = #{query.ownerId}</if>
              <if test="query.hasImage == true">
                AND EXISTS (
                  SELECT 1
                  FROM image img
                  WHERE img.status = 'READY' AND img.deleted_at IS NULL
                    AND (img.item_id = a.item_id OR img.inventory_id = a.inventory_id)
                )
              </if>
              <if test="query.hasImage == false">
                AND NOT EXISTS (
                  SELECT 1
                  FROM image img
                  WHERE img.status = 'READY' AND img.deleted_at IS NULL
                    AND (img.item_id = a.item_id OR img.inventory_id = a.inventory_id)
                )
              </if>
        ),
        grp AS (
            SELECT item_id, max(updated_at) AS updated_at, count(*) AS inventory_count
            FROM hit
            GROUP BY item_id
        )
        SELECT item_id, updated_at, inventory_count
        FROM grp
        <if test="cursorUpdated != null">
          WHERE (updated_at, item_id) &lt; (#{cursorUpdated}, #{cursorId})
        </if>
        ORDER BY updated_at DESC, item_id DESC
        LIMIT #{query.limit}
        </script>
        """,
    )
    fun searchPageUpdated(
        @Param("query") q: SearchQuery,
        @Param("cursorUpdated") cursorUpdated: OffsetDateTime?,
        @Param("cursorId") cursorId: UUID?,
    ): List<SearchPageRow>
}
