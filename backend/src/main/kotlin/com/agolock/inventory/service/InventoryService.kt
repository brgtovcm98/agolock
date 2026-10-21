@file:Suppress(
    "LongParameterList",
    "TooManyFunctions",
    "ThrowsCount",
    "MagicNumber",
    "MaxLineLength",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "UnusedParameter",
    "NestedBlockDepth",
    "LongMethod",
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
import com.agolock.sharing.persistence.ShareMapper
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
    private val shareMapper: ShareMapper,
    private val clock: Clock,
) {
    fun list(
        actor: Actor,
        statuses: List<com.agolock.inventory.domain.InventoryStatus>?,
        limit: Int?,
        scope: String = "OWNED",
    ): List<InventoryResponse> {
        val normalizedScope = scope.uppercase()
        if (normalizedScope !in setOf("OWNED", "SHARED", "ALL")) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "scope는 OWNED, SHARED, ALL 중 하나여야 합니다.")
        }
        val effectiveLimit = (limit ?: 100).coerceIn(1, 100)
        val effectiveStatuses =
            (statuses?.distinct() ?: emptyList())
                .ifEmpty {
                    listOf(
                        com.agolock.inventory.domain.InventoryStatus.IN_USE,
                        com.agolock.inventory.domain.InventoryStatus.STORED,
                    )
                }
        val owned =
            if (normalizedScope == "SHARED") {
                emptyList()
            } else {
                accessService.requireOwner(actor, actor.spaceId)
                inventoryMapper
                    .list(actor.spaceId, effectiveStatuses, null, null, null, effectiveLimit)
                    .map { toResponse(actor, it, "OWNER") }
            }
        if (normalizedScope == "OWNED") return owned
        val shared =
            shareMapper.listActiveByGrantee(actor.userId).flatMap { share ->
                inventoryMapper
                    .list(share.spaceId, effectiveStatuses, null, null, null, effectiveLimit)
                    .filter { inventory ->
                        val target = inventoryTarget(inventory)
                        (share.roomId != null && target.roomId == share.roomId) ||
                            (share.shelfId != null && target.shelfId == share.shelfId)
                    }.mapNotNull { inventory ->
                        val target = inventoryTarget(inventory)
                        val permission = accessService.effectivePermission(actor, target)
                        if (permission == com.agolock.hierarchy.domain.Permission.NONE) {
                            null
                        } else {
                            toResponse(actor, inventory, permission.name)
                        }
                    }
            }
        return (owned + shared)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
            .take(effectiveLimit)
    }

    fun listByItem(
        actor: Actor,
        itemId: UUID,
    ): List<InventoryResponse> {
        accessService.requireOwner(actor, actor.spaceId)
        itemMapper.findById(actor.spaceId, itemId) ?: throw notFound("Item을 찾을 수 없습니다.")
        return inventoryMapper.listByItem(actor.spaceId, itemId).map { toResponse(actor, it, "OWNER") }
    }

    fun get(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(inventory)
        val permission = accessService.require(actor, target, com.agolock.hierarchy.domain.Permission.VIEW)
        return toResponse(actor, inventory, permission.name)
    }

    fun getShared(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(inventory)
        val permission = accessService.require(actor, target, com.agolock.hierarchy.domain.Permission.VIEW)
        return toResponse(actor, inventory, permission.name)
    }

    @Transactional
    fun create(
        actor: Actor,
        command: CreateInventoryCommand,
    ): InventoryResponse {
        if (command.status == com.agolock.inventory.domain.InventoryStatus.DISPOSED) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "신규 Inventory는 DISPOSED로 만들 수 없습니다.")
        }
        val location = resolveLocation(command.roomId, command.shelfId, command.boxId)
        val target =
            accessService.target(
                location.spaceId,
                location.response.roomId,
                location.response.shelfId,
                location.response.boxId,
            )
        val permission = accessService.require(actor, target, com.agolock.hierarchy.domain.Permission.MANAGE)
        val item =
            itemMapper.findByIdAny(command.itemId)
                ?: throw notFound("Item을 찾을 수 없습니다.")
        if (item.spaceId != location.spaceId) throw notFound("Item을 찾을 수 없습니다.")
        val now = OffsetDateTime.now(clock)
        val inventory =
            Inventory(
                id = UuidV7.generate(clock),
                spaceId = location.spaceId,
                itemId = item.id,
                roomId = location.roomId,
                shelfId = location.shelfId,
                boxId = location.boxId,
                status = command.status,
                version = 0,
                createdAt = now,
                updatedAt = now,
            )
        inventoryMapper.insert(inventory)
        return toResponse(actor, inventory, permission.name)
    }

    @Transactional
    fun update(
        actor: Actor,
        inventoryId: UUID,
        command: UpdateInventoryCommand,
    ): InventoryResponse {
        val current = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(current)
        val permission = accessService.require(actor, target, com.agolock.hierarchy.domain.Permission.EDIT)
        if (current.version != command.version) throw versionConflict()
        val targetStatus =
            if (command.statusPresent) {
                command.status ?: throw ApiException(
                    "VALIDATION_FAILED",
                    HttpStatus.BAD_REQUEST,
                    "status는 null일 수 없습니다.",
                )
            } else {
                current.status
            }
        if (targetStatus == com.agolock.inventory.domain.InventoryStatus.DISPOSED && !command.confirmDisposal) {
            throw ApiException("DISPOSAL_CONFIRMATION_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY, "폐기 확인이 필요합니다.")
        }
        if (targetStatus != com.agolock.inventory.domain.InventoryStatus.DISPOSED && command.confirmDisposal) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "폐기 확인은 DISPOSED 전환에만 사용할 수 있습니다.")
        }
        if (command.categoryIdOverridePresent &&
            command.categoryIdOverride != null &&
            categoryMapper.findById(current.spaceId, command.categoryIdOverride) == null
        ) {
            throw notFound("카테고리를 찾을 수 없습니다.")
        }
        val nameOverride =
            if (command.nameOverridePresent) {
                command.nameOverride?.trim()?.takeIf(String::isNotEmpty)
            } else {
                current.nameOverride
            }
        val descriptionOverride =
            if (command.descriptionOverridePresent) {
                command.descriptionOverride?.trim()?.takeIf(String::isNotEmpty)
            } else {
                current.descriptionOverride
            }
        val categoryIdOverride =
            if (command.categoryIdOverridePresent) command.categoryIdOverride else current.categoryIdOverride
        val priceAmountOverride =
            if (command.priceAmountOverridePresent) command.priceAmountOverride else current.priceAmountOverride
        if (
            nameOverride == current.nameOverride &&
            descriptionOverride == current.descriptionOverride &&
            categoryIdOverride == current.categoryIdOverride &&
            priceAmountOverride == current.priceAmountOverride &&
            targetStatus == current.status
        ) {
            return toResponse(actor, current, permission.name)
        }
        val updated =
            inventoryMapper.update(
                current.spaceId,
                inventoryId,
                nameOverride,
                descriptionOverride,
                categoryIdOverride,
                priceAmountOverride,
                targetStatus,
                command.version,
                OffsetDateTime.now(clock),
            )
        if (updated == 0) throw versionConflict()
        return toResponse(
            actor,
            inventoryMapper.findById(current.spaceId, inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다."),
            permission.name,
        )
    }

    @Transactional
    fun move(
        actor: Actor,
        inventoryId: UUID,
        command: MoveInventoryCommand,
    ): InventoryResponse {
        val current = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val sourceTarget = inventoryTarget(current)
        accessService.require(actor, sourceTarget, com.agolock.hierarchy.domain.Permission.MANAGE)
        if (current.version != command.version) throw versionConflict()
        val location = resolveLocation(command.roomId, command.shelfId, command.boxId)
        if (location.spaceId != current.spaceId) throw notFound("이동할 위치를 찾을 수 없습니다.")
        val target =
            accessService.target(
                location.spaceId,
                location.response.roomId,
                location.response.shelfId,
                location.response.boxId,
            )
        val permission = accessService.require(actor, target, com.agolock.hierarchy.domain.Permission.MANAGE)
        if (current.roomId == location.roomId && current.shelfId == location.shelfId && current.boxId == location.boxId) {
            return toResponse(actor, current, permission.name)
        }
        val now = OffsetDateTime.now(clock)
        val updated =
            inventoryMapper.move(
                current.spaceId,
                inventoryId,
                location.roomId,
                location.shelfId,
                location.boxId,
                command.version,
                now,
            )
        if (updated == 0) throw versionConflict()
        movementMapper.insert(
            InventoryMovement(
                id = UuidV7.generate(clock),
                spaceId = current.spaceId,
                inventoryId = inventoryId,
                fromRoomId = current.roomId,
                fromShelfId = current.shelfId,
                fromBoxId = current.boxId,
                toRoomId = location.roomId,
                toShelfId = location.shelfId,
                toBoxId = location.boxId,
                movedBy = actor.userId,
                movedAt = now,
            ),
        )
        return toResponse(
            actor,
            inventoryMapper.findById(current.spaceId, inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다."),
            permission.name,
        )
    }

    @Transactional
    fun delete(
        actor: Actor,
        inventoryId: UUID,
    ) {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(inventory)
        accessService.require(actor, target, com.agolock.hierarchy.domain.Permission.MANAGE)
        inventoryMapper.softDelete(inventory.spaceId, inventoryId, OffsetDateTime.now(clock))
    }

    fun moveTargets(
        actor: Actor,
        inventoryId: UUID,
    ): List<com.agolock.inventory.api.LocationItemResponse> {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        accessService.require(actor, inventoryTarget(inventory), com.agolock.hierarchy.domain.Permission.MANAGE)
        val result = mutableListOf<com.agolock.inventory.api.LocationItemResponse>()
        roomMapper.list(inventory.spaceId, null, null, 100).forEach { room ->
            val roomTarget = accessService.target(inventory.spaceId, roomId = room.id)
            if (accessService.effectivePermission(actor, roomTarget).atLeast(com.agolock.hierarchy.domain.Permission.MANAGE)) {
                result +=
                    com.agolock.inventory.api
                        .LocationItemResponse("ROOM", room.id, room.name)
            }
            shelfMapper.listByRoom(inventory.spaceId, room.id, null, null, 100).forEach { shelf ->
                val shelfTarget = accessService.target(inventory.spaceId, room.id, shelf.id)
                if (accessService.effectivePermission(actor, shelfTarget).atLeast(com.agolock.hierarchy.domain.Permission.MANAGE)) {
                    result +=
                        com.agolock.inventory.api
                            .LocationItemResponse("SHELF", shelf.id, "${room.name} > ${shelf.name}")
                }
                boxMapper.listByShelf(inventory.spaceId, shelf.id, null, null, 100).forEach { box ->
                    val boxTarget = accessService.target(inventory.spaceId, room.id, shelf.id, box.id)
                    if (accessService.effectivePermission(actor, boxTarget).atLeast(com.agolock.hierarchy.domain.Permission.MANAGE)) {
                        result +=
                            com.agolock.inventory.api
                                .LocationItemResponse("BOX", box.id, "${room.name} > ${shelf.name} > ${box.name}")
                    }
                }
            }
        }
        return result
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

    private fun inventoryTarget(inventory: Inventory): com.agolock.hierarchy.domain.AccessTarget {
        val shelf = inventory.shelfId?.let { shelfMapper.findByIdAny(it) }
        val boxShelf =
            inventory.boxId?.let { boxId ->
                boxMapper.findByIdAny(boxId)?.let { shelfMapper.findByIdAny(it.shelfId) }
            }
        val roomId = inventory.roomId ?: shelf?.roomId ?: boxShelf?.roomId
        return accessService.target(inventory.spaceId, roomId, inventory.shelfId, inventory.boxId)
    }

    private data class LocationRef(
        val spaceId: UUID,
        val roomId: UUID?,
        val shelfId: UUID?,
        val boxId: UUID?,
        val response: InventoryLocationResponse,
    )

    private fun resolveLocation(
        roomId: UUID?,
        shelfId: UUID?,
        boxId: UUID?,
    ): LocationRef {
        val count = listOfNotNull(roomId, shelfId, boxId).size
        if (count != 1) throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "위치는 Room·Shelf·Box 중 하나여야 합니다.")
        return when {
            roomId != null -> {
                val room = roomMapper.findByIdAny(roomId) ?: throw notFound("Room을 찾을 수 없습니다.")
                LocationRef(room.spaceId, room.id, null, null, InventoryLocationResponse("ROOM", room.id, null, null, room.name))
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

    private fun toResponse(
        actor: Actor,
        inventory: Inventory,
        permission: String,
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
