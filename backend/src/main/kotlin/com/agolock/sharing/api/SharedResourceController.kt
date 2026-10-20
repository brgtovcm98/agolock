package com.agolock.sharing.api

import com.agolock.hierarchy.api.BoxResponse
import com.agolock.hierarchy.api.RoomResponse
import com.agolock.hierarchy.api.ShelfResponse
import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.inventory.api.InventoryResponse
import com.agolock.sharing.service.SharedResourceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/shared")
@Tag(name = "sharing")
class SharedResourceController(
    private val sharedResourceService: SharedResourceService,
) {
    @GetMapping("/rooms/{roomId}")
    @Operation(operationId = "getSharedRoom", summary = "공유받은 Room 상세")
    fun room(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable roomId: UUID,
    ): RoomResponse = sharedResourceService.room(Actor.from(principal), roomId)

    @GetMapping("/shelves/{shelfId}")
    @Operation(operationId = "getSharedShelf", summary = "공유받은 Shelf 상세")
    fun shelf(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shelfId: UUID,
    ): ShelfResponse = sharedResourceService.shelf(Actor.from(principal), shelfId)

    @GetMapping("/boxes/{boxId}")
    @Operation(operationId = "getSharedBox", summary = "공유받은 Box 상세")
    fun box(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable boxId: UUID,
    ): BoxResponse = sharedResourceService.box(Actor.from(principal), boxId)

    @GetMapping("/inventories/{inventoryId}")
    @Operation(operationId = "getSharedInventory", summary = "공유받은 Inventory 상세")
    fun inventory(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
    ): InventoryResponse = sharedResourceService.inventory(Actor.from(principal), inventoryId)
}
