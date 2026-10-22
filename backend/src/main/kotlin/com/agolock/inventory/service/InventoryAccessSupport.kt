package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.InventoryLocationResponse
import com.agolock.inventory.domain.Inventory
import com.agolock.inventory.persistence.InventoryMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class InventoryAccessSupport(
    private val inventoryMapper: InventoryMapper,
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
    private val locationResolver: InventoryLocationResolver,
    private val accessService: AccessService,
) {
    fun requireInventory(
        actor: Actor,
        inventoryId: UUID,
    ): Inventory =
        inventoryMapper.findById(actor.spaceId, inventoryId)
            ?: throw notFound("Inventory를 찾을 수 없습니다.")

    fun inventoryTarget(inventory: Inventory): AccessTarget {
        val location =
            locationResolver.resolve(inventory.id)
                ?: throw notFound("Inventory를 찾을 수 없습니다.")
        return accessService.target(
            location.spaceId,
            location.effectiveRoomId,
            location.effectiveShelfId,
            location.boxId,
        )
    }

    @Suppress("ThrowsCount")
    fun resolveLocation(
        roomId: UUID?,
        shelfId: UUID?,
        boxId: UUID?,
    ): LocationRef {
        val count = listOfNotNull(roomId, shelfId, boxId).size
        if (count != 1) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "위치는 Room·Shelf·Box 중 하나여야 합니다.")
        }
        return when {
            roomId != null -> {
                val room = roomMapper.findByIdAny(roomId) ?: throw notFound("Room을 찾을 수 없습니다.")
                LocationRef(
                    room.spaceId,
                    room.id,
                    null,
                    null,
                    InventoryLocationResponse("ROOM", room.id, null, null, room.name),
                )
            }
            shelfId != null -> {
                val shelf = shelfMapper.findByIdAny(shelfId) ?: throw notFound("Shelf를 찾을 수 없습니다.")
                LocationRef(
                    shelf.spaceId,
                    null,
                    shelf.id,
                    null,
                    InventoryLocationResponse("SHELF", shelf.roomId, shelf.id, null, shelf.name),
                )
            }
            else -> {
                val box = boxMapper.findByIdAny(boxId!!) ?: throw notFound("Box를 찾을 수 없습니다.")
                val shelf = shelfMapper.findById(box.spaceId, box.shelfId)
                LocationRef(
                    box.spaceId,
                    null,
                    null,
                    box.id,
                    InventoryLocationResponse("BOX", shelf?.roomId, box.shelfId, box.id, box.name),
                )
            }
        }
    }

    data class LocationRef(
        val spaceId: UUID,
        val roomId: UUID?,
        val shelfId: UUID?,
        val boxId: UUID?,
        val response: InventoryLocationResponse,
    )

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)
}
