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

import com.agolock.common.cursor.TimeCursorCodec
import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.domain.PageResult
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.domain.toPermission
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
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.persistence.CategoryMapper
import com.agolock.inventory.persistence.InventoryListRow
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
    private val itemService: ItemService,
    private val categoryMapper: CategoryMapper,
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
    private val locationResolver: InventoryLocationResolver,
    private val accessService: AccessService,
    private val clock: Clock,
) {
    fun list(
        actor: Actor,
        statuses: List<com.agolock.inventory.domain.InventoryStatus>?,
        limit: Int?,
        scope: String = "ALL",
        cursorRaw: String? = null,
        roomId: UUID? = null,
        shelfId: UUID? = null,
        boxId: UUID? = null,
    ): PageResult<InventoryResponse> {
        val normalizedScope = scope.uppercase()
        if (normalizedScope !in setOf("OWNED", "SHARED", "ALL")) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "scope는 OWNED, SHARED, ALL 중 하나여야 합니다.")
        }
        if (listOfNotNull(roomId, shelfId, boxId).size > 1) {
            throw ApiException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                "roomId, shelfId, boxId 중 하나만 보낼 수 있습니다.",
            )
        }
        val effectiveLimit = TimeCursorCodec.limit(limit)
        val cursor = TimeCursorCodec.decode(cursorRaw)
        val effectiveStatuses =
            (statuses?.distinct() ?: emptyList())
                .ifEmpty {
                    listOf(
                        com.agolock.inventory.domain.InventoryStatus.IN_USE,
                        com.agolock.inventory.domain.InventoryStatus.STORED,
                    )
                }
        val rows =
            inventoryMapper.listAccessible(
                actor.spaceId,
                actor.userId,
                normalizedScope,
                effectiveStatuses,
                null,
                roomId,
                shelfId,
                boxId,
                cursor?.updatedAt,
                cursor?.id,
                effectiveLimit + 1,
            )
        val page = rows.take(effectiveLimit).map { it.toListResponse() }
        val next =
            if (rows.size > effectiveLimit) {
                page.lastOrNull()?.let { TimeCursorCodec.encode(it.updatedAt, it.id) }
            } else {
                null
            }
        return PageResult(page, next)
    }

    @Transactional(readOnly = true)
    fun listByItem(
        actor: Actor,
        itemId: UUID,
    ): List<InventoryResponse> {
        val item = itemMapper.findByIdAny(itemId) ?: throw notFound("Item을 찾을 수 없습니다.")
        val owner = item.spaceId == actor.spaceId
        if (owner) {
            accessService.requireOwner(actor, actor.spaceId)
        }
        val statuses =
            if (owner) {
                InventoryStatus.entries.filter { it != InventoryStatus.DISPOSED }
            } else {
                InventoryStatus.entries
            }
        return inventoryMapper
            .listAccessible(
                actor.spaceId,
                actor.userId,
                if (owner) "OWNED" else "SHARED",
                statuses,
                itemId,
                null,
                null,
                null,
                null,
                null,
                Int.MAX_VALUE,
            ).map { it.toListResponse() }
    }

    fun get(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(inventory)
        val permission = accessService.require(actor, target, GrantLevel.VIEW)
        return toResponse(actor, inventory, permission)
    }

    fun getShared(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(inventory)
        val permission = accessService.require(actor, target, GrantLevel.VIEW)
        return toResponse(actor, inventory, permission)
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
        val permission = accessService.require(actor, target, GrantLevel.MANAGE)
        if ((command.itemId == null) == (command.newItem == null)) {
            throw ApiException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                "itemId 또는 newItem 중 정확히 하나를 보내야 합니다.",
            )
        }
        val item =
            if (command.newItem != null) {
                if (location.spaceId != actor.spaceId) {
                    throw ApiException(
                        "PERMISSION_DENIED",
                        HttpStatus.FORBIDDEN,
                        "새 물건은 내 Space의 위치에만 등록할 수 있습니다.",
                    )
                }
                accessService.requireOwner(actor, location.spaceId)
                itemService.create(
                    actor,
                    CreateItemCommand(
                        command.newItem.name,
                        command.newItem.description,
                        command.newItem.categoryId,
                        command.newItem.priceAmount,
                    ),
                )
            } else {
                itemService.getReadable(actor, command.itemId!!)
            }
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
        return toResponse(actor, inventory, permission)
    }

    @Transactional
    fun update(
        actor: Actor,
        inventoryId: UUID,
        command: UpdateInventoryCommand,
    ): InventoryResponse {
        val current = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(current)
        val permission = accessService.require(actor, target, GrantLevel.EDIT)
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
            return toResponse(actor, current, permission)
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
            permission,
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
        accessService.require(actor, sourceTarget, GrantLevel.MANAGE)
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
        val permission = accessService.require(actor, target, GrantLevel.MANAGE)
        if (current.roomId == location.roomId && current.shelfId == location.shelfId && current.boxId == location.boxId) {
            return toResponse(actor, current, permission)
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
            permission,
        )
    }

    @Transactional
    fun delete(
        actor: Actor,
        inventoryId: UUID,
    ) {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val target = inventoryTarget(inventory)
        accessService.require(actor, target, GrantLevel.MANAGE)
        inventoryMapper.softDelete(inventory.spaceId, inventoryId, OffsetDateTime.now(clock))
    }

    fun moveTargets(
        actor: Actor,
        inventoryId: UUID,
    ): List<com.agolock.inventory.api.LocationItemResponse> {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        accessService.require(actor, inventoryTarget(inventory), GrantLevel.MANAGE)
        val result = mutableListOf<com.agolock.inventory.api.LocationItemResponse>()
        roomMapper.list(inventory.spaceId, null, null, 100).forEach { room ->
            val roomTarget = accessService.target(inventory.spaceId, roomId = room.id)
            if (accessService.effectivePermission(actor, roomTarget).atLeast(GrantLevel.MANAGE)) {
                result +=
                    com.agolock.inventory.api
                        .LocationItemResponse("ROOM", room.id, room.name)
            }
            shelfMapper.listByRoom(inventory.spaceId, room.id, null, null, 100).forEach { shelf ->
                val shelfTarget = accessService.target(inventory.spaceId, room.id, shelf.id)
                if (accessService.effectivePermission(actor, shelfTarget).atLeast(GrantLevel.MANAGE)) {
                    result +=
                        com.agolock.inventory.api
                            .LocationItemResponse("SHELF", shelf.id, "${room.name} > ${shelf.name}")
                }
                boxMapper.listByShelf(inventory.spaceId, shelf.id, null, null, 100).forEach { box ->
                    val boxTarget = accessService.target(inventory.spaceId, room.id, shelf.id, box.id)
                    if (accessService.effectivePermission(actor, boxTarget).atLeast(GrantLevel.MANAGE)) {
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

    private fun inventoryTarget(inventory: Inventory): AccessTarget {
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

    private fun InventoryListRow.toListResponse(): InventoryResponse {
        val type =
            when {
                displayBoxId != null -> "BOX"
                displayShelfId != null -> "SHELF"
                else -> "ROOM"
            }
        val locationName =
            when (type) {
                "BOX" -> boxName ?: "삭제된 Box"
                "SHELF" -> shelfName ?: "삭제된 Shelf"
                else -> roomName ?: "삭제된 Room"
            }
        return InventoryResponse(
            id = id,
            itemId = itemId,
            itemName = itemName,
            displayName = nameOverride ?: itemName,
            description = descriptionOverride ?: itemDescription,
            categoryId = categoryIdOverride ?: itemCategoryId,
            priceAmount = priceAmountOverride ?: itemPriceAmount,
            status = status,
            location =
                InventoryLocationResponse(
                    type = type,
                    roomId = displayRoomId,
                    shelfId = displayShelfId,
                    boxId = displayBoxId,
                    name = locationName,
                ),
            nameOverride = nameOverride,
            descriptionOverride = descriptionOverride,
            categoryIdOverride = categoryIdOverride,
            priceAmountOverride = priceAmountOverride,
            version = version,
            effectivePermission = Permission.valueOf(effectivePermission),
            isShared = effectivePermission != "OWNER",
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun toResponse(
        actor: Actor,
        inventory: Inventory,
        permission: EffectivePermission,
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
            effectivePermission = permission.toPermission(),
            isShared = permission is EffectivePermission.Granted,
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
