@file:Suppress("MaxLineLength")

package com.agolock.inventory.persistence

import com.agolock.inventory.domain.InventoryMovement
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import java.util.UUID

@Mapper
interface InventoryMovementMapper {
    @Insert(
        """
        INSERT INTO inventory_movement (
            id, space_id, inventory_id, from_room_id, from_shelf_id, from_box_id,
            to_room_id, to_shelf_id, to_box_id, moved_by, moved_at
        ) VALUES (
            #{id}, #{spaceId}, #{inventoryId}, #{fromRoomId}, #{fromShelfId}, #{fromBoxId},
            #{toRoomId}, #{toShelfId}, #{toBoxId}, #{movedBy}, #{movedAt}
        )
        """,
    )
    fun insert(movement: InventoryMovement)

    @Select(
        "SELECT * FROM inventory_movement WHERE space_id = #{spaceId} AND inventory_id = #{inventoryId} ORDER BY moved_at DESC, id DESC LIMIT #{limit}",
    )
    fun listByInventory(
        @Param("spaceId") spaceId: UUID,
        @Param("inventoryId") inventoryId: UUID,
        @Param("limit") limit: Int,
    ): List<InventoryMovement>
}
