package com.agolock.sharing.api

import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.sharing.service.ShareService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "sharing")
class ShareController(
    private val shareService: ShareService,
) {
    @GetMapping("/shared")
    @Operation(operationId = "listSharedResources", summary = "공유받은 리소스")
    fun shared(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): ShareListResponse = ShareListResponse(shareService.listShared(Actor.from(principal)))

    @GetMapping("/shares")
    @Operation(operationId = "listShares", summary = "보낸 공유 목록")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): ShareListResponse = ShareListResponse(shareService.listOwner(Actor.from(principal)))

    @PatchMapping("/shares/{shareId}")
    @Operation(operationId = "updateShare", summary = "공유 권한 변경")
    fun update(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shareId: UUID,
        @Valid @RequestBody request: UpdateShareRequest,
    ): ShareResponse =
        shareService.updatePermission(
            Actor.from(principal),
            shareId,
            request.permission,
        )

    @DeleteMapping("/shares/{shareId}")
    @Operation(operationId = "revokeShare", summary = "공유 철회")
    fun revoke(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable shareId: UUID,
    ): ResponseEntity<Void> {
        shareService.revoke(Actor.from(principal), shareId)
        return ResponseEntity.noContent().build()
    }
}
