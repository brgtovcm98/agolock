package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.InventoryResponse
import com.agolock.inventory.domain.Inventory
import com.agolock.inventory.domain.InventoryMovement
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.domain.Item
import com.agolock.inventory.persistence.CategoryMapper
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.InventoryMovementMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Suppress("LongParameterList", "TooManyFunctions")
class InventoryCommandService(
    private val inventoryMapper: InventoryMapper,
    private val movementMapper: InventoryMovementMapper,
    private val itemService: ItemService,
    private val categoryMapper: CategoryMapper,
    private val accessService: AccessService,
    private val assembler: InventoryResponseAssembler,
    private val support: InventoryAccessSupport,
    private val clock: Clock,
) {
    @Transactional
    fun create(
        actor: Actor,
        command: CreateInventoryCommand,
    ): InventoryResponse {
        validateCreateStatus(command)
        val location = support.resolveLocation(command.roomId, command.shelfId, command.boxId)
        val permission = accessService.require(actor, locationTarget(location), GrantLevel.MANAGE)
        val item = resolveItem(actor, command, location.spaceId)
        val inventory = newInventory(command, location, item)
        inventoryMapper.insert(inventory)
        return assembler.toResponse(inventory, permission)
    }

    @Transactional
    fun update(
        actor: Actor,
        inventoryId: UUID,
        command: UpdateInventoryCommand,
    ): InventoryResponse {
        val current = findInventory(inventoryId)
        val permission = accessService.require(actor, support.inventoryTarget(current), GrantLevel.EDIT)
        requireVersion(current.version, command.version)
        val targetStatus = resolveTargetStatus(current, command)
        validateCategoryOverride(current, command)
        val overrides = resolveOverrides(current, command)
        if (isNoOp(current, overrides, targetStatus)) {
            return assembler.toResponse(current, permission)
        }
        val updated =
            inventoryMapper.update(
                current.spaceId,
                inventoryId,
                overrides.name,
                overrides.description,
                overrides.categoryId,
                overrides.priceAmount,
                targetStatus,
                command.version,
                OffsetDateTime.now(clock),
            )
        requireUpdated(updated)
        return assembler.toResponse(support.findInventory(current.spaceId, inventoryId), permission)
    }

    @Transactional
    fun move(
        actor: Actor,
        inventoryId: UUID,
        command: MoveInventoryCommand,
    ): InventoryResponse {
        val current = findInventory(inventoryId)
        accessService.require(actor, support.inventoryTarget(current), GrantLevel.MANAGE)
        requireVersion(current.version, command.version)
        val location = resolveMoveLocation(current, command)
        val permission = accessService.require(actor, locationTarget(location), GrantLevel.MANAGE)
        if (isSameLocation(current, location)) {
            return assembler.toResponse(current, permission)
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
        requireUpdated(updated)
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
        return assembler.toResponse(support.findInventory(current.spaceId, inventoryId), permission)
    }

    @Transactional
    fun delete(
        actor: Actor,
        inventoryId: UUID,
    ) {
        val inventory = findInventory(inventoryId)
        accessService.require(actor, support.inventoryTarget(inventory), GrantLevel.MANAGE)
        inventoryMapper.softDelete(inventory.spaceId, inventoryId, OffsetDateTime.now(clock))
    }

    private fun validateCreateStatus(command: CreateInventoryCommand) {
        if (command.status == InventoryStatus.DISPOSED) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "신규 Inventory는 DISPOSED로 만들 수 없습니다.")
        }
    }

    @Suppress("ThrowsCount")
    private fun resolveItem(
        actor: Actor,
        command: CreateInventoryCommand,
        locationSpaceId: UUID,
    ): Item {
        if ((command.itemId == null) == (command.newItem == null)) {
            throw ApiException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                "itemId 또는 newItem 중 정확히 하나를 보내야 합니다.",
            )
        }
        val item =
            if (command.newItem != null) {
                if (locationSpaceId != actor.spaceId) {
                    throw ApiException(
                        "PERMISSION_DENIED",
                        HttpStatus.FORBIDDEN,
                        "새 물건은 내 Space의 위치에만 등록할 수 있습니다.",
                    )
                }
                accessService.requireOwner(actor, locationSpaceId)
                itemService.create(actor, command.newItem.toCommand())
            } else {
                itemService.getReadable(actor, command.itemId!!)
            }
        if (item.spaceId != locationSpaceId) throw notFound("Item을 찾을 수 없습니다.")
        return item
    }

    private fun newInventory(
        command: CreateInventoryCommand,
        location: InventoryAccessSupport.LocationRef,
        item: Item,
    ): Inventory {
        val now = OffsetDateTime.now(clock)
        return Inventory(
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
    }

    @Suppress("ThrowsCount")
    private fun resolveTargetStatus(
        current: Inventory,
        command: UpdateInventoryCommand,
    ): InventoryStatus {
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
        if (targetStatus == InventoryStatus.DISPOSED && !command.confirmDisposal) {
            throw ApiException("DISPOSAL_CONFIRMATION_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY, "폐기 확인이 필요합니다.")
        }
        if (targetStatus != InventoryStatus.DISPOSED && command.confirmDisposal) {
            throw ApiException(
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST,
                "폐기 확인은 DISPOSED 전환에만 사용할 수 있습니다.",
            )
        }
        return targetStatus
    }

    private fun validateCategoryOverride(
        current: Inventory,
        command: UpdateInventoryCommand,
    ) {
        val categoryId = command.categoryIdOverride
        if (command.categoryIdOverridePresent &&
            categoryId != null &&
            categoryMapper.findById(current.spaceId, categoryId) == null
        ) {
            throw notFound("카테고리를 찾을 수 없습니다.")
        }
    }

    private fun resolveOverrides(
        current: Inventory,
        command: UpdateInventoryCommand,
    ): Overrides =
        Overrides(
            name =
                if (command.nameOverridePresent) {
                    command.nameOverride?.trim()?.takeIf(String::isNotEmpty)
                } else {
                    current.nameOverride
                },
            description =
                if (command.descriptionOverridePresent) {
                    command.descriptionOverride?.trim()?.takeIf(String::isNotEmpty)
                } else {
                    current.descriptionOverride
                },
            categoryId =
                if (command.categoryIdOverridePresent) command.categoryIdOverride else current.categoryIdOverride,
            priceAmount =
                if (command.priceAmountOverridePresent) command.priceAmountOverride else current.priceAmountOverride,
        )

    private fun isNoOp(
        current: Inventory,
        overrides: Overrides,
        targetStatus: InventoryStatus,
    ): Boolean =
        overrides.name == current.nameOverride &&
            overrides.description == current.descriptionOverride &&
            overrides.categoryId == current.categoryIdOverride &&
            overrides.priceAmount == current.priceAmountOverride &&
            targetStatus == current.status

    @Suppress("ThrowsCount")
    private fun resolveMoveLocation(
        current: Inventory,
        command: MoveInventoryCommand,
    ): InventoryAccessSupport.LocationRef {
        val location = support.resolveLocation(command.roomId, command.shelfId, command.boxId)
        if (location.spaceId != current.spaceId) throw notFound("이동할 위치를 찾을 수 없습니다.")
        return location
    }

    private fun locationTarget(location: InventoryAccessSupport.LocationRef) =
        accessService.target(
            location.spaceId,
            location.response.roomId,
            location.response.shelfId,
            location.response.boxId,
        )

    private fun isSameLocation(
        current: Inventory,
        location: InventoryAccessSupport.LocationRef,
    ): Boolean =
        current.roomId == location.roomId &&
            current.shelfId == location.shelfId &&
            current.boxId == location.boxId

    private fun findInventory(inventoryId: UUID): Inventory =
        inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")

    private fun requireVersion(
        currentVersion: Long,
        requestedVersion: Long,
    ) {
        if (currentVersion != requestedVersion) throw versionConflict()
    }

    private fun requireUpdated(updated: Int) {
        if (updated == 0) throw versionConflict()
    }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

    private fun versionConflict() = ApiException("VERSION_CONFLICT", HttpStatus.CONFLICT, "다른 변경이 먼저 저장되었습니다.")

    private data class Overrides(
        val name: String?,
        val description: String?,
        val categoryId: UUID?,
        val priceAmount: Int?,
    )

    private fun NewItemCommand.toCommand() = CreateItemCommand(name, description, categoryId, priceAmount)
}
