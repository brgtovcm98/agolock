package com.agolock.hierarchy.api

import com.agolock.common.idempotency.IdempotencyService
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.service.AccessService
import com.agolock.hierarchy.service.BoxService
import com.agolock.hierarchy.service.CreateNodeCommand
import com.agolock.hierarchy.service.MoveBoxCommand
import com.agolock.hierarchy.service.UpdateNodeCommand
import com.agolock.identity.security.AppUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
class BoxController(
    private val boxService: BoxService,
    private val accessService: AccessService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping("/shelves/{shelfId}/boxes")
    @Operation(operationId = "listBoxes", summary = "Shelf의 Box 목록")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shelfId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): BoxListResponse {
        val actor = Actor.from(principal)
        val page = boxService.list(actor, shelfId, cursor, limit)
        return BoxListResponse(
            items = page.items.map { it.toResponse(accessService.effectivePermission(actor, actor.spaceId), false) },
            nextCursor = page.nextCursor,
        )
    }

    @PostMapping("/shelves/{shelfId}/boxes")
    @Operation(operationId = "createBox", summary = "Box 생성")
    fun create(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shelfId: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateNodeRequest,
    ): ResponseEntity<Any> {
        val actor = Actor.from(principal)
        val path = "/api/v1/shelves/$shelfId/boxes"
        val result =
            idempotencyService.execute(
                userId = actor.userId,
                key = idempotencyKey,
                method = "POST",
                path = path,
                request = request,
                responseType = BoxResponse::class.java,
                locationOf = { box -> "/api/v1/boxes/${box.id}" },
            ) {
                boxService
                    .create(actor, shelfId, CreateNodeCommand(request.name, request.description))
                    .toResponse(accessService.effectivePermission(actor, actor.spaceId), false)
            }
        return result.toResponseEntity()
    }

    @GetMapping("/boxes/{boxId}")
    @Operation(operationId = "getBox", summary = "Box 상세")
    fun get(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable boxId: UUID,
    ): BoxResponse {
        val actor = Actor.from(principal)
        return boxService
            .get(actor, boxId)
            .toResponse(accessService.effectivePermission(actor, actor.spaceId), false)
    }

    @PatchMapping("/boxes/{boxId}")
    @Operation(operationId = "updateBox", summary = "Box 수정")
    fun update(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable boxId: UUID,
        @Valid @RequestBody request: UpdateNodeRequest,
    ): BoxResponse {
        val actor = Actor.from(principal)
        return boxService
            .update(actor, boxId, UpdateNodeCommand(request.name, request.description, request.version))
            .toResponse(accessService.effectivePermission(actor, actor.spaceId), false)
    }

    @PatchMapping("/boxes/{boxId}/parent")
    @Operation(operationId = "moveBox", summary = "Box의 Shelf 이동")
    fun move(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable boxId: UUID,
        @Valid @RequestBody request: MoveBoxRequest,
    ): BoxResponse {
        val actor = Actor.from(principal)
        return boxService
            .move(actor, boxId, MoveBoxCommand(request.shelfId, request.version))
            .toResponse(accessService.effectivePermission(actor, actor.spaceId), false)
    }

    @DeleteMapping("/boxes/{boxId}")
    @Operation(operationId = "deleteBox", summary = "Box 삭제")
    fun delete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable boxId: UUID,
    ): ResponseEntity<Void> {
        boxService.delete(Actor.from(principal), boxId)
        return ResponseEntity.noContent().build()
    }
}
