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
@Suppress("LongParameterList")
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
    @Suppress("LongMethod", "ThrowsCount")
    fun create(
        actor: Actor,
        command: CreateInventoryCommand,
    ): InventoryResponse {
        if (command.status == InventoryStatus.DISPOSED) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "신규 Inventory는 DISPOSED로 만들 수 없습니다.")
        }
        val location = support.resolveLocation(command.roomId, command.shelfId, command.boxId)
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
        return assembler.toResponse(inventory, permission)
    }

    @Transactional
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ComplexCondition", "ThrowsCount")
    fun update(
        actor: Actor,
        inventoryId: UUID,
        command: UpdateInventoryCommand,
    ): InventoryResponse {
        val current = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val permission = accessService.require(actor, support.inventoryTarget(current), GrantLevel.EDIT)
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
            return assembler.toResponse(current, permission)
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
        return assembler.toResponse(
            inventoryMapper.findById(current.spaceId, inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다."),
            permission,
        )
    }

    @Transactional
    @Suppress("ThrowsCount")
    fun move(
        actor: Actor,
        inventoryId: UUID,
        command: MoveInventoryCommand,
    ): InventoryResponse {
        val current = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        accessService.require(actor, support.inventoryTarget(current), GrantLevel.MANAGE)
        if (current.version != command.version) throw versionConflict()
        val location = support.resolveLocation(command.roomId, command.shelfId, command.boxId)
        if (location.spaceId != current.spaceId) throw notFound("이동할 위치를 찾을 수 없습니다.")
        val target =
            accessService.target(
                location.spaceId,
                location.response.roomId,
                location.response.shelfId,
                location.response.boxId,
            )
        val permission = accessService.require(actor, target, GrantLevel.MANAGE)
        if (current.roomId == location.roomId &&
            current.shelfId == location.shelfId &&
            current.boxId == location.boxId
        ) {
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
        return assembler.toResponse(
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
        accessService.require(actor, support.inventoryTarget(inventory), GrantLevel.MANAGE)
        inventoryMapper.softDelete(inventory.spaceId, inventoryId, OffsetDateTime.now(clock))
    }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

    private fun versionConflict() = ApiException("VERSION_CONFLICT", HttpStatus.CONFLICT, "다른 변경이 먼저 저장되었습니다.")
}
