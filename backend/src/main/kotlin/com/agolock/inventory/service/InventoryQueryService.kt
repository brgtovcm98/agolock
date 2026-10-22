package com.agolock.inventory.service

import com.agolock.common.cursor.TimeCursorCodec
import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.domain.PageResult
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.InventoryResponse
import com.agolock.inventory.api.MovementResponse
import com.agolock.inventory.api.toResponse
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.InventoryMovementMapper
import com.agolock.inventory.persistence.ItemMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class InventoryQueryService(
    private val inventoryMapper: InventoryMapper,
    private val movementMapper: InventoryMovementMapper,
    private val itemMapper: ItemMapper,
    private val accessService: AccessService,
    private val assembler: InventoryResponseAssembler,
    private val support: InventoryAccessSupport,
) {
    @Suppress("LongParameterList")
    fun list(
        actor: Actor,
        statuses: List<InventoryStatus>?,
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
                .ifEmpty { listOf(InventoryStatus.IN_USE, InventoryStatus.STORED) }
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
        val page = rows.take(effectiveLimit).map { assembler.toResponse(it) }
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
            ).map { assembler.toResponse(it) }
    }

    fun get(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse {
        val inventory = inventoryMapper.findByIdAny(inventoryId) ?: throw notFound("Inventory를 찾을 수 없습니다.")
        val permission = accessService.require(actor, support.inventoryTarget(inventory), GrantLevel.VIEW)
        return assembler.toResponse(inventory, permission)
    }

    fun getShared(
        actor: Actor,
        inventoryId: UUID,
    ): InventoryResponse = get(actor, inventoryId)

    fun movements(
        actor: Actor,
        inventoryId: UUID,
    ): List<MovementResponse> {
        accessService.requireOwner(actor, actor.spaceId)
        support.requireInventory(actor, inventoryId)
        return movementMapper.listByInventory(actor.spaceId, inventoryId, MAX_MOVEMENT_PAGE).map { movement ->
            movement.toResponse { roomId, shelfId, boxId -> assembler.location(actor.spaceId, roomId, shelfId, boxId) }
        }
    }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

    private companion object {
        const val MAX_MOVEMENT_PAGE = 100
    }
}
