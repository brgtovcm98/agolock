@file:Suppress("LongParameterList", "TooManyFunctions", "ThrowsCount", "MagicNumber", "MaxLineLength", "LongMethod")

package com.agolock.sharing.service

import com.agolock.common.audit.AuditService
import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.hierarchy.service.AccessService
import com.agolock.identity.persistence.UserMapper
import com.agolock.identity.service.TokenService
import com.agolock.messaging.outbox.OutboxService
import com.agolock.sharing.api.ShareInvitationResponse
import com.agolock.sharing.domain.InvitationStatus
import com.agolock.sharing.domain.ShareInvitation
import com.agolock.sharing.persistence.ShareInvitationMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID

@Service
class ShareInvitationService(
    private val invitationMapper: ShareInvitationMapper,
    private val shareService: ShareService,
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val userMapper: UserMapper,
    private val tokenService: TokenService,
    private val accessService: AccessService,
    private val auditService: AuditService,
    private val outboxService: OutboxService,
    private val clock: Clock,
) {
    @Transactional
    fun create(
        actor: Actor,
        command: CreateInvitationCommand,
    ): ShareInvitationResponse {
        accessService.requireOwner(actor, actor.spaceId)
        val targetCount = listOfNotNull(command.roomId, command.shelfId).size
        if (targetCount != 1) throw validation("초대 대상은 Room 또는 Shelf 중 하나여야 합니다.")
        validatePermission(command.permission)
        val room = command.roomId?.let { roomMapper.findById(actor.spaceId, it) ?: throw notFound("Room을 찾을 수 없습니다.") }
        val shelf = command.shelfId?.let { shelfMapper.findById(actor.spaceId, it) ?: throw notFound("Shelf를 찾을 수 없습니다.") }
        val email = normalizeEmail(command.invitedEmail)
        val user = userMapper.findActiveByEmail(email)
        if (email == userMapper.findById(actor.userId)?.email?.lowercase(Locale.ROOT)) {
            throw validation("자기 자신은 초대할 수 없습니다.")
        }
        if (user != null && shareService.hasActiveGrant(room?.id, shelf?.id, user.id)) {
            throw ApiException("SHARE_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 공유 중인 사용자입니다.")
        }
        val now = OffsetDateTime.now(clock)
        invitationMapper.expireStale(now)
        val rawToken = tokenService.createToken()
        val invitation =
            ShareInvitation(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                roomId = room?.id,
                shelfId = shelf?.id,
                invitedEmail = email,
                invitedUserId = user?.id,
                permission = command.permission,
                tokenHash = tokenService.hashToken(rawToken),
                status = InvitationStatus.PENDING,
                expiresAt = now.plusDays(7),
                createdBy = actor.userId,
                createdAt = now,
            )
        try {
            invitationMapper.insert(invitation)
            outboxService.enqueueInvitation(
                actor.spaceId,
                invitation.id,
                mapOf(
                    "recipientEmail" to email,
                    "templateKey" to "SHARE_INVITATION",
                    "invitationId" to invitation.id,
                    "token" to rawToken,
                    "expiresAt" to invitation.expiresAt,
                    "resourceType" to if (invitation.roomId != null) "ROOM" else "SHELF",
                ),
            )
            auditService.record(
                actor.spaceId,
                actor.userId,
                "SHARE_INVITATION_CREATED",
                "SHARE_INVITATION",
                invitation.id,
                mapOf("roomId" to invitation.roomId, "shelfId" to invitation.shelfId, "permission" to invitation.permission),
            )
        } catch (exception: org.springframework.dao.DuplicateKeyException) {
            throw ApiException(
                "INVITATION_ALREADY_PENDING",
                HttpStatus.CONFLICT,
                "같은 대상에 대기 중인 초대가 이미 있습니다.",
                cause = exception,
            )
        }
        return invitation.toResponse(room?.name, shelf?.name)
    }

    fun listOwner(actor: Actor): List<ShareInvitationResponse> {
        accessService.requireOwner(actor, actor.spaceId)
        return invitationMapper.listBySpace(actor.spaceId).map { toResponse(it) }
    }

    fun listReceived(actor: Actor): List<ShareInvitationResponse> {
        val user = userMapper.findById(actor.userId) ?: throw unauthenticated()
        return invitationMapper
            .listPendingReceived(user.email, OffsetDateTime.now(clock))
            .map { toResponse(it) }
    }

    @Transactional
    fun accept(
        actor: Actor,
        invitationId: UUID,
    ): ShareServiceResponse {
        val user = userMapper.findById(actor.userId) ?: throw unauthenticated()
        val invitation = invitationMapper.findByIdForUpdate(invitationId) ?: throw notFound("초대를 찾을 수 없습니다.")
        if (invitation.invitedEmail.lowercase(Locale.ROOT) != user.email.lowercase(Locale.ROOT)) {
            throw ApiException("INVITATION_EMAIL_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY, "로그인한 계정의 이메일과 초대 이메일이 일치하지 않습니다.")
        }
        val now = OffsetDateTime.now(clock)
        if (invitation.status == InvitationStatus.ACCEPTED) {
            val existing = shareService.findGrant(invitation, user.id)
            if (existing != null) return ShareServiceResponse(shareService.toResponse(existing))
            throw notPending()
        }
        if (invitation.status != InvitationStatus.PENDING) throw notPending()
        if (!invitation.expiresAt.isAfter(now)) {
            throw ApiException("INVITATION_EXPIRED", HttpStatus.CONFLICT, "만료된 초대입니다.")
        }
        val roomId = invitation.roomId
        if (roomId != null && roomMapper.findById(invitation.spaceId, roomId) == null) {
            throw notFound("초대 대상 Room을 찾을 수 없습니다.")
        }
        val shelfId = invitation.shelfId
        if (shelfId != null && shelfMapper.findById(invitation.spaceId, shelfId) == null) {
            throw notFound("초대 대상 Shelf를 찾을 수 없습니다.")
        }
        if (shareService.findGrant(invitation, user.id) != null) {
            throw ApiException("SHARE_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 공유 중인 사용자입니다.")
        }
        val share = shareService.createFromInvitation(invitation, user.id)
        invitationMapper.updateStatus(invitation.id, InvitationStatus.ACCEPTED, user.id, now, null, null)
        auditService.record(
            invitation.spaceId,
            user.id,
            "SHARE_INVITATION_ACCEPTED",
            "SHARE_INVITATION",
            invitation.id,
            mapOf("shareId" to share.id, "roomId" to invitation.roomId, "shelfId" to invitation.shelfId),
        )
        auditService.record(
            invitation.spaceId,
            user.id,
            "SHARE_CREATED",
            "SHARE",
            share.id,
            mapOf("permission" to share.permission, "granteeUserId" to user.id),
        )
        return ShareServiceResponse(shareService.toResponse(share))
    }

    @Transactional
    fun acceptByToken(
        actor: Actor,
        token: String,
    ): ShareServiceResponse {
        val invitation =
            invitationMapper.findByTokenHash(tokenService.hashToken(token))
                ?: throw notFound("초대를 찾을 수 없습니다.")
        return accept(actor, invitation.id)
    }

    @Transactional
    fun declineByToken(
        actor: Actor,
        token: String,
    ) {
        val invitation =
            invitationMapper.findByTokenHash(tokenService.hashToken(token))
                ?: throw notFound("초대를 찾을 수 없습니다.")
        decline(actor, invitation.id)
    }

    @Transactional
    fun decline(
        actor: Actor,
        invitationId: UUID,
    ) {
        val user = userMapper.findById(actor.userId) ?: throw unauthenticated()
        val invitation = invitationMapper.findByIdForUpdate(invitationId) ?: throw notFound("초대를 찾을 수 없습니다.")
        if (invitation.invitedEmail.lowercase(Locale.ROOT) != user.email.lowercase(Locale.ROOT)) {
            throw notFound("초대를 찾을 수 없습니다.")
        }
        if (invitation.status != InvitationStatus.PENDING) throw notPending()
        invitationMapper.updateStatus(invitation.id, InvitationStatus.DECLINED, user.id, null, OffsetDateTime.now(clock), null)
        auditService.record(
            invitation.spaceId,
            user.id,
            "SHARE_INVITATION_DECLINED",
            "SHARE_INVITATION",
            invitation.id,
            mapOf("roomId" to invitation.roomId, "shelfId" to invitation.shelfId),
        )
    }

    @Transactional
    fun resend(
        actor: Actor,
        invitationId: UUID,
    ): ShareInvitationResponse {
        accessService.requireOwner(actor, actor.spaceId)
        val invitation = invitationMapper.findByIdForUpdate(invitationId) ?: throw notFound("초대를 찾을 수 없습니다.")
        if (invitation.spaceId != actor.spaceId || invitation.status != InvitationStatus.PENDING) throw notPending()
        if (!invitation.expiresAt.isAfter(OffsetDateTime.now(clock))) {
            throw ApiException("INVITATION_EXPIRED", HttpStatus.CONFLICT, "만료된 초대입니다.")
        }
        val rawToken = tokenService.createToken()
        if (invitationMapper.updateTokenHash(invitation.id, tokenService.hashToken(rawToken)) == 0) throw notPending()
        outboxService.enqueueInvitation(
            invitation.spaceId,
            invitation.id,
            mapOf(
                "recipientEmail" to invitation.invitedEmail,
                "templateKey" to "SHARE_INVITATION",
                "invitationId" to invitation.id,
                "token" to rawToken,
                "expiresAt" to invitation.expiresAt,
                "resourceType" to if (invitation.roomId != null) "ROOM" else "SHELF",
            ),
        )
        auditService.record(
            invitation.spaceId,
            actor.userId,
            "SHARE_INVITATION_RESENT",
            "SHARE_INVITATION",
            invitation.id,
            mapOf("roomId" to invitation.roomId, "shelfId" to invitation.shelfId),
        )
        return toResponse(invitation)
    }

    @Transactional
    fun revoke(
        actor: Actor,
        invitationId: UUID,
    ) {
        accessService.requireOwner(actor, actor.spaceId)
        val invitation = invitationMapper.findByIdForUpdate(invitationId) ?: throw notFound("초대를 찾을 수 없습니다.")
        if (invitation.spaceId != actor.spaceId) throw notFound("초대를 찾을 수 없습니다.")
        if (invitation.status == InvitationStatus.REVOKED) return
        if (invitation.status != InvitationStatus.PENDING) throw notPending()
        invitationMapper.updateStatus(invitation.id, InvitationStatus.REVOKED, null, null, null, OffsetDateTime.now(clock))
        auditService.record(
            invitation.spaceId,
            actor.userId,
            "SHARE_INVITATION_REVOKED",
            "SHARE_INVITATION",
            invitation.id,
            mapOf("roomId" to invitation.roomId, "shelfId" to invitation.shelfId),
        )
    }

    private fun toResponse(invitation: ShareInvitation): ShareInvitationResponse {
        val room = invitation.roomId?.let { roomMapper.findById(invitation.spaceId, it) }
        val shelf = invitation.shelfId?.let { shelfMapper.findById(invitation.spaceId, it) }
        return invitation.toResponse(room?.name, shelf?.name)
    }

    private fun ShareInvitation.toResponse(
        roomName: String?,
        shelfName: String?,
    ) = ShareInvitationResponse(
        id = id,
        resourceType = if (roomId != null) "ROOM" else "SHELF",
        resourceName = roomName ?: shelfName ?: "삭제된 리소스",
        roomId = roomId,
        shelfId = shelfId,
        invitedEmail = invitedEmail,
        permission = permission,
        status = status,
        expiresAt = expiresAt,
        createdAt = createdAt,
    )

    private fun normalizeEmail(value: String) = value.trim().lowercase(Locale.ROOT)

    private fun validatePermission(permission: Permission) {
        if (permission !in setOf(Permission.VIEW, Permission.EDIT, Permission.MANAGE)) {
            throw validation("공유 권한은 VIEW, EDIT, MANAGE 중 하나여야 합니다.")
        }
    }

    private fun notPending() = ApiException("INVITATION_NOT_PENDING", HttpStatus.CONFLICT, "대기 중인 초대가 아닙니다.")

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

    private fun unauthenticated() = ApiException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")

    private fun validation(message: String) = ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message)
}

data class ShareServiceResponse(
    val share: com.agolock.sharing.api.ShareResponse,
)
