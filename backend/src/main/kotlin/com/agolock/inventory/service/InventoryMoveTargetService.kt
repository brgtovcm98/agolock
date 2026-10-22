package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.LocationItemResponse
import com.agolock.inventory.persistence.InventoryMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class InventoryMoveTargetService(
    private val inventoryMapper: InventoryMapper,
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
    private val accessService: AccessService,
    private val support: InventoryAccessSupport,
) {
    @Suppress("NestedBlockDepth")
    fun moveTargets(
        actor: Actor,
        inventoryId: java.util.UUID,
    ): List<LocationItemResponse> {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        accessService.require(actor, support.inventoryTarget(inventory), GrantLevel.MANAGE)
        val result = mutableListOf<LocationItemResponse>()
        roomMapper.list(inventory.spaceId, null, null, MAX_LOCATION_PAGE).forEach { room ->
            val roomTarget = accessService.target(inventory.spaceId, roomId = room.id)
            if (accessService.effectivePermission(actor, roomTarget).atLeast(GrantLevel.MANAGE)) {
                result += LocationItemResponse("ROOM", room.id, room.name)
            }
            shelfMapper.listByRoom(inventory.spaceId, room.id, null, null, MAX_LOCATION_PAGE).forEach { shelf ->
                val shelfTarget = accessService.target(inventory.spaceId, room.id, shelf.id)
                if (accessService.effectivePermission(actor, shelfTarget).atLeast(GrantLevel.MANAGE)) {
                    result += LocationItemResponse("SHELF", shelf.id, "${room.name} > ${shelf.name}")
                }
                boxMapper.listByShelf(inventory.spaceId, shelf.id, null, null, MAX_LOCATION_PAGE).forEach { box ->
                    val boxTarget = accessService.target(inventory.spaceId, room.id, shelf.id, box.id)
                    if (accessService.effectivePermission(actor, boxTarget).atLeast(GrantLevel.MANAGE)) {
                        result += LocationItemResponse("BOX", box.id, "${room.name} > ${shelf.name} > ${box.name}")
                    }
                }
            }
        }
        return result
    }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

    private companion object {
        const val MAX_LOCATION_PAGE = 100
    }
}
