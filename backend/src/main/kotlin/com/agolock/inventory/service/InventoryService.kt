@file:Suppress(
    "LongParameterList",
    "TooManyFunctions",
    "ThrowsCount",
    "MagicNumber",
    "MaxLineLength",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "UnusedParameter",
)

package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.InventoryLocationResponse
import com.agolock.inventory.api.InventoryResponse
import com.agolock.inventory.api.MovementResponse
import com.agolock.inventory.api.toResponse
import com.agolock.inventory.domain.Inventory
import com.agolock.inventory.domain.InventoryMovement
import com.agolock.inventory.persistence.CategoryMapper
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.InventoryMovementMapper
import com.agolock.inventory.persistence.ItemMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class InventoryService(
    private val inventoryMapper: InventoryMapper,
    private val movementMapper: InventoryMovementMapper,
    private val itemMapper: ItemMapper,
    private val categoryMapper: CategoryMapper,
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
    private val accessService: AccessService,
    private val clock: Clock,
) {
    fun list(
        actor: Actor,
        status: com.agolock.inventory.domain.InventoryStatus?,
        limit: Int?,
    ): List<InventoryResponse> {
        accessService.requireOwner(actor, actor.spaceId)
        return inventoryMapper
            .list(actor.spaceId, status, null, null, null, (limit ?: 100).coerceIn(1, 100))
            .map { toResponse(actor, it) }
    }

    fun listByItem(
        actor: Actor,
        itemId: UUID,
    ): List<InventoryResponse> {
        accessService.requireOwner(actor, actor.spaceId)
        itemMapper.findById(actor.spaceId, itemId) ?: throw notFound("Item을 찾을 수 없습니다.")
        return inventoryMapper.listByItem(actor.spaceId, itemId).map { toResponse(actor, it) }
    }

    fun get(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse {
        accessService.requireOwner(actor, actor.spaceId)
        return toResponse(actor, requireInventory(actor, inventoryId))
    }

    fun getShared(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = accessService.target(inventory.spaceId, inventory.roomId, inventory.shelfId, inventory.boxId)
        val permission = accessService.require(actor, target, com.agolock.hierarchy.domain.Permission.VIEW)
        return toResponse(actor, inventory, permission.name)
    }

    @Transactional
    fun create(
        actor: Actor,
        command: CreateInventoryCommand,
    ): InventoryResponse {
        accessService.requireOwner(actor, actor.spaceId)
        if (command.status == com.agolock.inventory.domain.InventoryStatus.DISPOSED) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "신규 Inventory는 DISPOSED로 만들 수 없습니다.")
        }
        val item =
            itemMapper.findById(actor.spaceId, command.itemId)
                ?: throw notFound("Item을 찾을 수 없습니다.")
        validateLocation(actor, command.roomId, command.shelfId, command.boxId)
        val now = OffsetDateTime.now(clock)
        val inventory =
            Inventory(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                itemId = item.id,
                roomId = command.roomId,
                shelfId = command.shelfId,
                boxId = command.boxId,
                status = command.status,
                version = 0,
                createdAt = now,
                updatedAt = now,
            )
        inventoryMapper.insert(inventory)
        return toResponse(actor, inventory)
    }

    @Transactional
    fun update(
        actor: Actor,
        inventoryId: UUID,
        command: UpdateInventoryCommand,
    ): InventoryResponse {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireInventory(actor, inventoryId)
        if (current.version != command.version) throw versionConflict()
        if (command.status == com.agolock.inventory.domain.InventoryStatus.DISPOSED && !command.confirmDisposal) {
            throw ApiException("DISPOSAL_CONFIRMATION_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY, "폐기 확인이 필요합니다.")
        }
        if (command.status != com.agolock.inventory.domain.InventoryStatus.DISPOSED && command.confirmDisposal) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "폐기 확인은 DISPOSED 전환에만 사용할 수 있습니다.")
        }
        if (command.categoryIdOverride != null && categoryMapper.findById(actor.spaceId, command.categoryIdOverride) == null) {
            throw notFound("카테고리를 찾을 수 없습니다.")
        }
        val nameOverride = command.nameOverride?.trim()?.takeIf(String::isNotEmpty) ?: current.nameOverride
        val descriptionOverride =
            command.descriptionOverride?.trim()?.takeIf(String::isNotEmpty) ?: current.descriptionOverride
        val categoryIdOverride = command.categoryIdOverride ?: current.categoryIdOverride
        val priceAmountOverride = command.priceAmountOverride ?: current.priceAmountOverride
        if (
            nameOverride == current.nameOverride &&
            descriptionOverride == current.descriptionOverride &&
            categoryIdOverride == current.categoryIdOverride &&
            priceAmountOverride == current.priceAmountOverride &&
            command.status == current.status
        ) {
            return toResponse(actor, current)
        }
        val updated =
            inventoryMapper.update(
                actor.spaceId,
                inventoryId,
                nameOverride,
                descriptionOverride,
                categoryIdOverride,
                priceAmountOverride,
                command.status,
                command.version,
                OffsetDateTime.now(clock),
            )
        if (updated == 0) throw versionConflict()
        return toResponse(actor, requireInventory(actor, inventoryId))
    }

    @Transactional
    fun move(
        actor: Actor,
        inventoryId: UUID,
        command: MoveInventoryCommand,
    ): InventoryResponse {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireInventory(actor, inventoryId)
        if (current.version != command.version) throw versionConflict()
        val target = validateLocation(actor, command.roomId, command.shelfId, command.boxId)
        val targetRoomId = if (target.type == "ROOM") target.roomId else null
        val targetShelfId = if (target.type == "SHELF") target.shelfId else null
        val targetBoxId = if (target.type == "BOX") target.boxId else null
        if (current.roomId == targetRoomId && current.shelfId == targetShelfId && current.boxId == targetBoxId) {
            return toResponse(actor, current)
        }
        val now = OffsetDateTime.now(clock)
        val updated =
            inventoryMapper.move(
                actor.spaceId,
                inventoryId,
                targetRoomId,
                targetShelfId,
                targetBoxId,
                command.version,
                now,
            )
        if (updated == 0) throw versionConflict()
        movementMapper.insert(
            InventoryMovement(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                inventoryId = inventoryId,
                fromRoomId = current.roomId,
                fromShelfId = current.shelfId,
                fromBoxId = current.boxId,
                toRoomId = targetRoomId,
                toShelfId = targetShelfId,
                toBoxId = targetBoxId,
                movedBy = actor.userId,
                movedAt = now,
            ),
        )
        return toResponse(actor, requireInventory(actor, inventoryId))
    }

    @Transactional
    fun delete(
        actor: Actor,
        inventoryId: UUID,
    ) {
        accessService.requireOwner(actor, actor.spaceId)
        requireInventory(actor, inventoryId)
        inventoryMapper.softDelete(actor.spaceId, inventoryId, OffsetDateTime.now(clock))
    }

    fun movements(
        actor: Actor,
        inventoryId: UUID,
    ): List<MovementResponse> {
        accessService.requireOwner(actor, actor.spaceId)
        requireInventory(actor, inventoryId)
        return movementMapper.listByInventory(actor.spaceId, inventoryId, 100).map { movement ->
            movement.toResponse { roomId, shelfId, boxId -> location(actor.spaceId, roomId, shelfId, boxId) }
        }
    }

    private fun requireInventory(
        actor: Actor,
        inventoryId: UUID,
    ): Inventory =
        inventoryMapper.findById(actor.spaceId, inventoryId)
            ?: throw notFound("Inventory를 찾을 수 없습니다.")

    private fun validateLocation(
        actor: Actor,
        roomId: UUID?,
        shelfId: UUID?,
        boxId: UUID?,
    ): InventoryLocationResponse {
        val count = listOfNotNull(roomId, shelfId, boxId).size
        if (count != 1) throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "위치는 Room·Shelf·Box 중 하나여야 합니다.")
        return when {
            roomId != null -> {
                val room = roomMapper.findById(actor.spaceId, roomId) ?: throw notFound("Room을 찾을 수 없습니다.")
                InventoryLocationResponse("ROOM", room.id, null, null, room.name)
            }
            shelfId != null -> {
                val shelf = shelfMapper.findById(actor.spaceId, shelfId) ?: throw notFound("Shelf를 찾을 수 없습니다.")
                InventoryLocationResponse("SHELF", shelf.roomId, shelf.id, null, shelf.name)
            }
            else -> {
                val box = boxMapper.findById(actor.spaceId, boxId!!) ?: throw notFound("Box를 찾을 수 없습니다.")
                val shelf = shelfMapper.findById(actor.spaceId, box.shelfId)
                InventoryLocationResponse("BOX", shelf?.roomId, box.shelfId, box.id, box.name)
            }
        }
    }

    private fun toResponse(
        actor: Actor,
        inventory: Inventory,
        permission: String = "OWNER",
    ): InventoryResponse {
        val item = itemMapper.findById(inventory.spaceId, inventory.itemId) ?: throw notFound("Item을 찾을 수 없습니다.")
        val name = inventory.nameOverride ?: item.name
        val description = inventory.descriptionOverride ?: item.description
        val categoryId = inventory.categoryIdOverride ?: item.categoryId
        val price = inventory.priceAmountOverride ?: item.priceAmount
        return InventoryResponse(
            id = inventory.id,
            itemId = inventory.itemId,
            itemName = item.name,
            displayName = name,
            description = description,
            categoryId = categoryId,
            priceAmount = price,
            status = inventory.status,
            location = location(inventory.spaceId, inventory.roomId, inventory.shelfId, inventory.boxId),
            nameOverride = inventory.nameOverride,
            descriptionOverride = inventory.descriptionOverride,
            categoryIdOverride = inventory.categoryIdOverride,
            priceAmountOverride = inventory.priceAmountOverride,
            version = inventory.version,
            effectivePermission = permission,
            isShared = permission != "OWNER",
            createdAt = inventory.createdAt,
            updatedAt = inventory.updatedAt,
        )
    }

    private fun location(
        spaceId: UUID,
        roomId: UUID?,
        shelfId: UUID?,
        boxId: UUID?,
    ): InventoryLocationResponse =
        when {
            boxId != null -> {
                val box = boxMapper.findById(spaceId, boxId)
                val shelf = box?.let { shelfMapper.findById(spaceId, it.shelfId) }
                InventoryLocationResponse("BOX", shelf?.roomId, box?.shelfId, boxId, box?.name ?: "삭제된 Box")
            }
            shelfId != null -> {
                val shelf = shelfMapper.findById(spaceId, shelfId)
                InventoryLocationResponse("SHELF", shelf?.roomId, shelfId, null, shelf?.name ?: "삭제된 Shelf")
            }
            roomId != null -> {
                val room = roomMapper.findById(spaceId, roomId)
                InventoryLocationResponse("ROOM", roomId, null, null, room?.name ?: "삭제된 Room")
            }
            else -> InventoryLocationResponse("UNKNOWN", null, null, null, "위치 없음")
        }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

    private fun versionConflict() = ApiException("VERSION_CONFLICT", HttpStatus.CONFLICT, "다른 변경이 먼저 저장되었습니다.")
}
