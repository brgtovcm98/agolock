package com.agolock.hierarchy.persistence

import com.agolock.common.persistence.CountRow
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.util.UUID

@Mapper
interface HierarchyCountMapper {
    @Select(
        """
        <script>
        SELECT shelf_id AS id, COUNT(*) AS cnt
        FROM box
        WHERE space_id = #{spaceId} AND deleted_at IS NULL
          AND shelf_id IN
          <foreach collection="shelfIds" item="shelfId" open="(" separator="," close=")">
            #{shelfId}
          </foreach>
        GROUP BY shelf_id
        </script>
        """,
    )
    fun countBoxesByShelfIds(
        @Param("spaceId") spaceId: UUID,
        @Param("shelfIds") shelfIds: List<UUID>,
    ): List<CountRow>

    @Select(
        """
        <script>
        SELECT shelf_id AS id, COUNT(*) AS cnt
        FROM inventory
        WHERE space_id = #{spaceId} AND deleted_at IS NULL
          AND shelf_id IN
          <foreach collection="shelfIds" item="shelfId" open="(" separator="," close=")">
            #{shelfId}
          </foreach>
        GROUP BY shelf_id
        </script>
        """,
    )
    fun countInventoryByShelfIds(
        @Param("spaceId") spaceId: UUID,
        @Param("shelfIds") shelfIds: List<UUID>,
    ): List<CountRow>

    @Select(
        "SELECT COUNT(*) FROM inventory WHERE space_id = #{spaceId} AND shelf_id = #{shelfId} AND deleted_at IS NULL",
    )
    fun countInventoryByShelf(
        @Param("spaceId") spaceId: UUID,
        @Param("shelfId") shelfId: UUID,
    ): Int
}
