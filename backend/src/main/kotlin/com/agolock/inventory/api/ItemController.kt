@file:Suppress("LongParameterList", "MaxLineLength")

package com.agolock.inventory.api

import com.agolock.common.error.ApiException
import com.agolock.common.idempotency.IdempotencyService
import com.agolock.hierarchy.api.toResponseEntity
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.toPermission
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.inventory.service.CreateItemCommand
import com.agolock.inventory.service.InventoryService
import com.agolock.inventory.service.ItemService
import com.agolock.inventory.service.UpdateItemCommand
import io.swagger.v3.oas.annotations.Operation
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
@RequestMapping("/api/v1/items")
@Tag(name = "items")
class ItemController(
    private val itemService: ItemService,
    private val inventoryService: InventoryService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping
    @Operation(operationId = "listItems", summary = "Item 목록")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "ALL") scope: String,
    ): ItemListResponse {
        val actor = Actor.from(principal)
        val page = itemService.list(actor, categoryId, q, limit, cursor, scope)
        return ItemListResponse(
            page.items.map { item ->
                val permission = itemService.permission(actor, item)
                item.toResponse(
                    permission.toPermission().name,
                    permission is EffectivePermission.Granted,
                )
            },
            page.nextCursor,
        )
    }

    @PostMapping
    @Operation(operationId = "createItem", summary = "Item 생성")
    fun create(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateItemRequest,
    ): ResponseEntity<Any> {
        val actor = Actor.from(principal)
        val result =
            idempotencyService.execute(
                userId = actor.userId,
                key = idempotencyKey,
                method = "POST",
                path = "/api/v1/items",
                request = request,
                responseType = ItemResponse::class.java,
                locationOf = { item -> "/api/v1/items/${item.id}" },
            ) {
                itemService
                    .create(
                        actor,
                        CreateItemCommand(request.name, request.description, request.categoryId, request.priceAmount),
                    ).toResponse()
            }
        return result.toResponseEntity()
    }

    @GetMapping("/{itemId}")
    @Operation(operationId = "getItem", summary = "Item 상세")
    fun get(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable itemId: UUID,
    ): ItemResponse {
        val actor = Actor.from(principal)
        val (item, permission) = itemService.getReadableWithPermission(actor, itemId)
        return item.toResponse(
            permission.toPermission().name,
            permission is EffectivePermission.Granted,
        )
    }

    @PatchMapping("/{itemId}")
    @Operation(operationId = "updateItem", summary = "Item 수정")
    fun update(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: UpdateItemRequest,
    ): ItemResponse {
        val version =
            request.version
                ?: throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "version은 필수입니다.")
        val item =
            itemService.update(
                Actor.from(principal),
                itemId,
                UpdateItemCommand(
                    request.name,
                    request.description,
                    request.categoryId,
                    request.priceAmount,
                    request.descriptionPresent,
                    request.categoryIdPresent,
                    request.priceAmountPresent,
                    version,
                ),
            )
        return item.toResponse()
    }

    @DeleteMapping("/{itemId}")
    @Operation(operationId = "deleteItem", summary = "Item 삭제")
    fun delete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable itemId: UUID,
    ): ResponseEntity<Void> {
        itemService.delete(Actor.from(principal), itemId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{itemId}/update-impact")
    @Operation(operationId = "getItemUpdateImpact", summary = "Item 수정 영향 범위")
    fun updateImpact(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable itemId: UUID,
    ): ItemUpdateImpactResponse {
        val (affected, protectedCount) = itemService.updateImpact(Actor.from(principal), itemId)
        return ItemUpdateImpactResponse(affected, protectedCount)
    }

    @GetMapping("/{itemId}/inventories")
    @Operation(operationId = "listItemInventories", summary = "Item의 Inventory 목록")
    fun inventories(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable itemId: UUID,
    ): InventoryListResponse = InventoryListResponse(inventoryService.listByItem(Actor.from(principal), itemId))
}
