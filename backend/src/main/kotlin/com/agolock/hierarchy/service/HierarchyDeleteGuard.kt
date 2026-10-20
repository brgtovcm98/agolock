package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.inventory.persistence.InventoryMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * M3에서 inventory 존재 검사를 이 클래스에 추가합니다.
 */
@Service
class HierarchyDeleteGuard(
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val inventoryMapper: InventoryMapper,
) {
    fun assertRoomEmpty(
        spaceId: UUID,
        roomId: UUID,
    ) {
        if (roomMapper.countShelves(spaceId, roomId) > 0 || inventoryMapper.countByRoom(spaceId, roomId) > 0) {
            throw notEmpty("하위 Shelf 또는 Inventory가 있는 Room은 삭제할 수 없습니다.")
        }
    }

    fun assertShelfEmpty(
        spaceId: UUID,
        shelfId: UUID,
    ) {
        if (shelfMapper.countBoxes(spaceId, shelfId) > 0 || inventoryMapper.countByShelf(spaceId, shelfId) > 0) {
            throw notEmpty("하위 Box 또는 Inventory가 있는 Shelf는 삭제할 수 없습니다.")
        }
    }

    fun assertBoxEmpty(
        spaceId: UUID,
        boxId: UUID,
    ) {
        if (inventoryMapper.countByBox(spaceId, boxId) > 0) {
            throw notEmpty("Inventory가 있는 Box는 삭제할 수 없습니다.")
        }
    }

    private fun notEmpty(message: String): ApiException =
        ApiException(
            code = "LOCATION_NOT_EMPTY",
            status = HttpStatus.CONFLICT,
            message = message,
        )
}
