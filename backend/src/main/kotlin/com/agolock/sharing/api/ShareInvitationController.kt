package com.agolock.sharing.api

import com.agolock.common.config.AuthProperties
import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.sharing.service.CreateInvitationCommand
import com.agolock.sharing.service.ShareInvitationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/share-invitations")
@Tag(name = "sharing")
class ShareInvitationController(
    private val invitationService: ShareInvitationService,
    private val authProperties: AuthProperties,
) {
    @GetMapping
    @Operation(operationId = "listShareInvitations", summary = "보낸 초대 목록")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): ShareInvitationListResponse = ShareInvitationListResponse(invitationService.listOwner(Actor.from(principal)))

    @PostMapping
    @Operation(operationId = "createShareInvitation", summary = "공유 초대 생성")
    fun create(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: CreateInvitationRequest,
    ): ResponseEntity<ShareInvitationResponse> {
        val invitation =
            invitationService.create(
                Actor.from(principal),
                CreateInvitationCommand(
                    request.roomId,
                    request.shelfId,
                    request.invitedEmail,
                    request.permission,
                ),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation)
    }

    @GetMapping("/received")
    @Operation(operationId = "listReceivedShareInvitations", summary = "받은 초대 목록")
    fun received(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): ShareInvitationListResponse {
        ensureEmailDiscovery()
        return ShareInvitationListResponse(invitationService.listReceived(Actor.from(principal)))
    }

    @PostMapping("/received/{invitationId}/accept")
    @Operation(operationId = "acceptShareInvitation", summary = "초대 수락")
    fun accept(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable invitationId: UUID,
    ): ShareResponse {
        ensureEmailDiscovery()
        return invitationService.accept(Actor.from(principal), invitationId).share
    }

    @PostMapping("/received/{invitationId}/decline")
    @Operation(operationId = "declineShareInvitation", summary = "초대 거절")
    fun decline(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable invitationId: UUID,
    ): ResponseEntity<Void> {
        ensureEmailDiscovery()
        invitationService.decline(Actor.from(principal), invitationId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/accept")
    @Operation(operationId = "acceptInvitationByToken", summary = "토큰으로 초대 수락")
    fun acceptByToken(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: InvitationTokenRequest,
    ): ShareResponse = invitationService.acceptByToken(Actor.from(principal), request.token).share

    @PostMapping("/decline")
    @Operation(operationId = "declineInvitationByToken", summary = "토큰으로 초대 거절")
    fun declineByToken(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: InvitationTokenRequest,
    ): ResponseEntity<Void> {
        invitationService.declineByToken(Actor.from(principal), request.token)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{invitationId}/resend")
    @Operation(operationId = "resendShareInvitation", summary = "초대 재발송")
    fun resend(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable invitationId: UUID,
    ): ResponseEntity<ShareInvitationResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(
            invitationService.resend(Actor.from(principal), invitationId),
        )

    @DeleteMapping("/{invitationId}")
    @Operation(operationId = "revokeShareInvitation", summary = "초대 철회")
    fun revoke(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable invitationId: UUID,
    ): ResponseEntity<Void> {
        invitationService.revoke(Actor.from(principal), invitationId)
        return ResponseEntity.noContent().build()
    }

    private fun ensureEmailDiscovery() {
        if (!authProperties.invitation.emailDiscoveryEnabled) {
            throw ApiException(
                "EMAIL_DISCOVERY_DISABLED",
                HttpStatus.FORBIDDEN,
                "메일의 초대 링크로 수락해 주세요.",
            )
        }
    }
}
