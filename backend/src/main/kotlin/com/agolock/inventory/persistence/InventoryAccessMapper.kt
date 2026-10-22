package com.agolock.inventory.persistence

import com.agolock.inventory.domain.InventoryLocation
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.util.UUID

/**
 * `inventory_access` view 조회 전용 매퍼입니다.
 *
 * 필요한 컬럼만 명시하고, 컬럼명은 map-underscore-to-camel-case로 매핑합니다.
 */
@Mapper
interface InventoryAccessMapper {
    @Select(
        """
        SELECT inventory_id, space_id, item_id, box_id, status,
               effective_room_id, effective_shelf_id, location_type
        FROM inventory_access
        WHERE inventory_id = #{inventoryId}
        """,
    )
    fun findById(
        @Param("inventoryId") inventoryId: UUID,
    ): InventoryLocation?
}
