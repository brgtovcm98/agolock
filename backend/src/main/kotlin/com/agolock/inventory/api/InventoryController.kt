package com.agolock.inventory.api

import com.agolock.common.error.ApiException
import com.agolock.common.idempotency.IdempotencyService
import com.agolock.hierarchy.api.toResponseEntity
import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.service.CreateInventoryCommand
import com.agolock.inventory.service.InventoryCommandService
import com.agolock.inventory.service.InventoryMoveTargetService
import com.agolock.inventory.service.InventoryQueryService
import com.agolock.inventory.service.MoveInventoryCommand
import com.agolock.inventory.service.NewItemCommand
import com.agolock.inventory.service.UpdateInventoryCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/inventories")
@Tag(name = "inventories")
class InventoryController(
    private val query: InventoryQueryService,
    private val command: InventoryCommandService,
    private val moveTargetService: InventoryMoveTargetService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping
    @Operation(operationId = "listInventories", summary = "Inventory 목록")
    @Suppress("LongParameterList")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestParam(required = false) status: List<InventoryStatus>?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false, defaultValue = "ALL") scope: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) roomId: UUID?,
        @RequestParam(required = false) shelfId: UUID?,
        @RequestParam(required = false) boxId: UUID?,
    ): InventoryListResponse {
        val page =
            query.list(
                Actor.from(principal),
                status,
                limit,
                scope,
                cursor,
                roomId,
                shelfId,
                boxId,
            )
        return InventoryListResponse(page.items, page.nextCursor)
    }

    @PostMapping
    @Operation(operationId = "createInventory", summary = "Inventory 생성")
    fun create(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateInventoryRequest,
    ): ResponseEntity<Any> {
        val actor = Actor.from(principal)
        val result =
            idempotencyService.execute(
                userId = actor.userId,
                key = idempotencyKey,
                method = "POST",
                path = "/api/v1/inventories",
                request = request,
                responseType = InventoryResponse::class.java,
                locationOf = { inventory -> "/api/v1/inventories/${inventory.id}" },
            ) {
                command.create(
                    actor,
                    CreateInventoryCommand(
                        request.itemId,
                        request.newItem?.let {
                            NewItemCommand(
                                it.name,
                                it.description,
                                it.categoryId,
                                it.priceAmount,
                            )
                        },
                        request.roomId,
                        request.shelfId,
                        request.boxId,
                        request.status,
                    ),
                )
            }
        return result.toResponseEntity()
    }

    @GetMapping("/{inventoryId}")
    @Operation(operationId = "getInventory", summary = "Inventory 상세")
    fun get(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
    ): InventoryResponse = query.get(Actor.from(principal), inventoryId)

    @PatchMapping("/{inventoryId}")
    @Operation(operationId = "updateInventory", summary = "Inventory 수정")
    fun update(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
        @Valid @RequestBody request: UpdateInventoryRequest,
    ): InventoryResponse {
        val version =
            request.version
                ?: throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "version은 필수입니다.")
        return command.update(
            Actor.from(principal),
            inventoryId,
            UpdateInventoryCommand(
                request.nameOverride,
                request.descriptionOverride,
                request.categoryIdOverride,
                request.priceAmountOverride,
                request.nameOverridePresent,
                request.descriptionOverridePresent,
                request.categoryIdOverridePresent,
                request.priceAmountOverridePresent,
                request.statusPresent,
                request.status,
                request.confirmDisposal,
                version,
            ),
        )
    }

    @PatchMapping("/{inventoryId}/location")
    @Operation(operationId = "moveInventory", summary = "Inventory 위치 이동")
    fun move(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
        @Valid @RequestBody request: MoveInventoryRequest,
    ): InventoryResponse {
        val version =
            request.version
                ?: throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "version은 필수입니다.")
        return command.move(
            Actor.from(principal),
            inventoryId,
            MoveInventoryCommand(request.roomId, request.shelfId, request.boxId, version),
        )
    }

    @DeleteMapping("/{inventoryId}")
    @Operation(operationId = "deleteInventory", summary = "Inventory 삭제")
    fun delete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
        @RequestParam version: Long,
    ): ResponseEntity<Void> {
        command.delete(Actor.from(principal), inventoryId, version)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{inventoryId}/move-targets")
    @Operation(operationId = "listMoveTargets", summary = "이동 가능한 위치")
    fun moveTargets(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
        @Parameter(schema = Schema(maxLength = 200))
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) cursor: String?,
    ): LocationListResponse {
        val page = moveTargetService.moveTargets(Actor.from(principal), inventoryId, q, limit, cursor)
        return LocationListResponse(page.items, page.nextCursor)
    }

    @GetMapping("/{inventoryId}/movements")
    @Operation(operationId = "listInventoryMovements", summary = "Inventory 이동 이력")
    fun movements(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
    ): List<MovementResponse> = query.movements(Actor.from(principal), inventoryId)
}
