package com.agolock.hierarchy.api

import com.agolock.common.error.ApiException
import com.agolock.common.idempotency.IdempotencyService
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.toPermission
import com.agolock.hierarchy.service.AccessService
import com.agolock.hierarchy.service.CreateNodeCommand
import com.agolock.hierarchy.service.MoveShelfCommand
import com.agolock.hierarchy.service.ShelfService
import com.agolock.hierarchy.service.UpdateNodeCommand
import com.agolock.identity.security.AppUserPrincipal
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
@RequestMapping("/api/v1")
@Tag(name = "hierarchy")
class ShelfController(
    private val shelfService: ShelfService,
    private val accessService: AccessService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping("/rooms/{roomId}/shelves")
    @Operation(operationId = "listShelves", summary = "Room의 Shelf 목록")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable roomId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): ShelfListResponse {
        val actor = Actor.from(principal)
        val page = shelfService.list(actor, roomId, cursor, limit)
        return ShelfListResponse(
            items =
                page.items.map {
                    val permission =
                        accessService.effectivePermission(
                            actor,
                            accessService.target(it.spaceId, roomId, it.id),
                        )
                    it.toResponse(permission.toPermission(), permission is EffectivePermission.Granted)
                },
            nextCursor = page.nextCursor,
        )
    }

    @PostMapping("/rooms/{roomId}/shelves")
    @Operation(operationId = "createShelf", summary = "Shelf 생성")
    fun create(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable roomId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateNodeRequest,
    ): ResponseEntity<Any> {
        val actor = Actor.from(principal)
        val path = "/api/v1/rooms/$roomId/shelves"
        val result =
            idempotencyService.execute(
                userId = actor.userId,
                key = idempotencyKey,
                method = "POST",
                path = path,
                request = request,
                responseType = ShelfResponse::class.java,
                locationOf = { shelf -> "/api/v1/shelves/${shelf.id}" },
            ) {
                shelfService
                    .create(actor, roomId, CreateNodeCommand(request.name, request.description))
                    .toResponse(accessService.effectivePermission(actor, actor.spaceId).toPermission(), false)
            }
        return result.toResponseEntity()
    }

    @GetMapping("/shelves/{shelfId}")
    @Operation(operationId = "getShelf", summary = "Shelf 상세")
    fun get(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shelfId: UUID,
    ): ShelfResponse {
        val actor = Actor.from(principal)
        val shelf = shelfService.get(actor, shelfId)
        val permission =
            accessService.effectivePermission(
                actor,
                accessService.target(shelf.spaceId, shelf.roomId, shelf.id),
            )
        return shelf.toResponse(permission.toPermission(), permission is EffectivePermission.Granted)
    }

    @PatchMapping("/shelves/{shelfId}")
    @Operation(operationId = "updateShelf", summary = "Shelf 수정")
    fun update(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shelfId: UUID,
        @Valid @RequestBody request: UpdateNodeRequest,
    ): ShelfResponse {
        val actor = Actor.from(principal)
        val version =
            request.version
                ?: throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "version은 필수입니다.")
        val updated =
            shelfService.update(
                actor,
                shelfId,
                UpdateNodeCommand(request.name, request.description, version, request.descriptionPresent),
            )
        return updated.toResponse(accessService.effectivePermission(actor, actor.spaceId).toPermission(), false)
    }

    @PatchMapping("/shelves/{shelfId}/parent")
    @Operation(operationId = "moveShelf", summary = "Shelf의 Room 이동")
    fun move(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shelfId: UUID,
        @Valid @RequestBody request: MoveShelfRequest,
    ): ShelfResponse {
        val actor = Actor.from(principal)
        return shelfService
            .move(actor, shelfId, MoveShelfCommand(request.roomId, request.version))
            .toResponse(accessService.effectivePermission(actor, actor.spaceId).toPermission(), false)
    }

    @DeleteMapping("/shelves/{shelfId}")
    @Operation(operationId = "deleteShelf", summary = "Shelf 삭제")
    fun delete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shelfId: UUID,
    ): ResponseEntity<Void> {
        shelfService.delete(Actor.from(principal), shelfId)
        return ResponseEntity.noContent().build()
    }
}
