package com.agolock.hierarchy.api

import com.agolock.common.error.ApiException
import com.agolock.common.idempotency.IdempotencyService
import com.agolock.common.idempotency.IdempotentResult
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.toPermission
import com.agolock.hierarchy.service.AccessService
import com.agolock.hierarchy.service.CreateNodeCommand
import com.agolock.hierarchy.service.RoomService
import com.agolock.hierarchy.service.UpdateNodeCommand
import com.agolock.identity.security.AppUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "hierarchy")
class RoomController(
    private val roomService: RoomService,
    private val accessService: AccessService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping
    @Operation(operationId = "listRooms", summary = "Room 목록")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): RoomListResponse {
        val actor = Actor.from(principal)
        val page = roomService.list(actor, cursor, limit)
        val permission = accessService.effectivePermission(actor, actor.spaceId)
        return RoomListResponse(
            items =
                page.items.map {
                    it.toResponse(permission.toPermission(), permission is EffectivePermission.Granted)
                },
            nextCursor = page.nextCursor,
        )
    }

    @PostMapping
    @Operation(operationId = "createRoom", summary = "Room 생성")
    fun create(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateNodeRequest,
    ): ResponseEntity<Any> {
        val actor = Actor.from(principal)
        val result =
            idempotencyService.execute(
                userId = actor.userId,
                key = idempotencyKey,
                method = "POST",
                path = "/api/v1/rooms",
                request = request,
                responseType = RoomResponse::class.java,
                locationOf = { room -> "/api/v1/rooms/${room.id}" },
            ) {
                roomService
                    .create(actor, CreateNodeCommand(request.name, request.description))
                    .toResponse(accessService.effectivePermission(actor, actor.spaceId).toPermission(), false)
            }
        return result.toResponseEntity()
    }

    @GetMapping("/{roomId}")
    @Operation(operationId = "getRoom", summary = "Room 상세")
    fun get(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable roomId: UUID,
    ): RoomResponse {
        val actor = Actor.from(principal)
        val room = roomService.get(actor, roomId)
        val permission =
            accessService.effectivePermission(actor, accessService.target(room.spaceId, roomId = room.id))
        return room.toResponse(
            permission.toPermission(),
            permission is EffectivePermission.Granted,
        )
    }

    @PatchMapping("/{roomId}")
    @Operation(operationId = "updateRoom", summary = "Room 수정")
    fun update(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable roomId: UUID,
        @Valid @RequestBody request: UpdateNodeRequest,
    ): RoomResponse {
        val actor = Actor.from(principal)
        val version =
            request.version
                ?: throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "version은 필수입니다.")
        val room =
            roomService.update(
                actor,
                roomId,
                UpdateNodeCommand(request.name, request.description, version, request.descriptionPresent),
            )
        return room.toResponse(accessService.effectivePermission(actor, actor.spaceId).toPermission(), false)
    }

    @DeleteMapping("/{roomId}")
    @Operation(operationId = "deleteRoom", summary = "Room 삭제")
    fun delete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable roomId: UUID,
    ): ResponseEntity<Void> {
        roomService.delete(Actor.from(principal), roomId)
        return ResponseEntity.noContent().build()
    }
}

fun <T> IdempotentResult<T>.toResponseEntity(): ResponseEntity<Any> {
    val builder =
        ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_JSON)
    location?.let { builder.location(URI.create(it)) }
    return builder.body(body)
}
