package com.agolock.sharing.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.api.BoxResponse
import com.agolock.hierarchy.api.RoomResponse
import com.agolock.hierarchy.api.ShelfResponse
import com.agolock.hierarchy.api.toResponse
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.InventoryResponse
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.service.InventoryService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SharedResourceService(
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
    private val inventoryMapper: InventoryMapper,
    private val inventoryService: InventoryService,
    private val accessService: AccessService,
) {
    fun room(
        actor: Actor,
        roomId: UUID,
    ): RoomResponse {
        val room = roomMapper.findByIdAny(roomId) ?: throw notFound("Room을 찾을 수 없습니다.")
        val target = accessService.target(room.spaceId, roomId = room.id)
        val permission = accessService.require(actor, target, Permission.VIEW)
        return room
            .copy(
                shelfCount = roomMapper.countShelves(room.spaceId, room.id),
                inventoryCount = inventoryMapper.countByRoom(room.spaceId, room.id),
            ).toResponse(permission, permission != Permission.OWNER)
    }

    fun shelf(
        actor: Actor,
        shelfId: UUID,
    ): ShelfResponse {
        val shelf = shelfMapper.findByIdAny(shelfId) ?: throw notFound("Shelf를 찾을 수 없습니다.")
        val target = accessService.target(shelf.spaceId, shelf.roomId, shelf.id)
        val permission = accessService.require(actor, target, Permission.VIEW)
        return shelf
            .copy(
                boxCount = shelfMapper.countBoxes(shelf.spaceId, shelf.id),
                inventoryCount = inventoryMapper.countByShelf(shelf.spaceId, shelf.id),
            ).toResponse(permission, permission != Permission.OWNER)
    }

    fun box(
        actor: Actor,
        boxId: UUID,
    ): BoxResponse {
        val box = boxMapper.findByIdAny(boxId) ?: throw notFound("Box를 찾을 수 없습니다.")
        val shelf = shelfMapper.findById(box.spaceId, box.shelfId)
        val target = accessService.target(box.spaceId, shelf?.roomId, box.shelfId, box.id)
        val permission = accessService.require(actor, target, Permission.VIEW)
        return box
            .copy(inventoryCount = inventoryMapper.countByBox(box.spaceId, box.id))
            .toResponse(permission, permission != Permission.OWNER)
    }

    fun inventory(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse = inventoryService.getShared(actor, inventoryId)

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)
}
