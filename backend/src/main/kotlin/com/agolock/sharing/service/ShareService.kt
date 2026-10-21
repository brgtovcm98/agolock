@file:Suppress("MaxLineLength", "LongParameterList")

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
import com.agolock.sharing.api.ShareResponse
import com.agolock.sharing.domain.Share
import com.agolock.sharing.domain.ShareInvitation
import com.agolock.sharing.persistence.ShareMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ShareService(
    private val shareMapper: ShareMapper,
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val userMapper: UserMapper,
    private val accessService: AccessService,
    private val auditService: AuditService,
    private val clock: Clock,
) {
    fun listOwner(actor: Actor): List<ShareResponse> {
        accessService.requireOwner(actor, actor.spaceId)
        return shareMapper.listActiveBySpace(actor.spaceId).map { toResponse(it) }
    }

    fun listShared(actor: Actor): List<ShareResponse> = shareMapper.listActiveByGrantee(actor.userId).map { toResponse(it) }

    @Transactional
    fun updatePermission(
        actor: Actor,
        shareId: UUID,
        permission: Permission,
    ): ShareResponse {
        if (permission !in setOf(Permission.VIEW, Permission.EDIT, Permission.MANAGE)) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "공유 권한은 VIEW, EDIT, MANAGE 중 하나여야 합니다.")
        }
        val share = requireOwnedShare(actor, shareId)
        if (shareMapper.updatePermission(shareId, permission, OffsetDateTime.now(clock)) == 0) {
            throw notFound("Share를 찾을 수 없습니다.")
        }
        auditService.record(
            share.spaceId,
            actor.userId,
            "SHARE_PERMISSION_CHANGED",
            "SHARE",
            share.id,
            mapOf("previousPermission" to share.permission, "permission" to permission, "granteeUserId" to share.granteeUserId),
        )
        return toResponse(share.copy(permission = permission))
    }

    @Transactional
    fun revoke(
        actor: Actor,
        shareId: UUID,
    ) {
        val share = requireOwnedShare(actor, shareId)
        if (shareMapper.revoke(shareId, OffsetDateTime.now(clock)) == 0) {
            throw notFound("Share를 찾을 수 없습니다.")
        }
        auditService.record(
            share.spaceId,
            actor.userId,
            "SHARE_REVOKED",
            "SHARE",
            share.id,
            mapOf(
                "roomId" to share.roomId,
                "shelfId" to share.shelfId,
                "permission" to share.permission,
                "granteeUserId" to share.granteeUserId,
            ),
        )
    }

    fun hasActiveGrant(
        roomId: UUID?,
        shelfId: UUID?,
        granteeUserId: UUID,
    ): Boolean =
        if (roomId != null) {
            shareMapper.findActiveRoomGrant(roomId, granteeUserId) != null
        } else {
            shareMapper.findActiveShelfGrant(shelfId!!, granteeUserId) != null
        }

    fun findGrant(
        invitation: ShareInvitation,
        granteeUserId: UUID,
    ): Share? =
        if (invitation.roomId != null) {
            shareMapper.findActiveRoomGrant(invitation.roomId!!, granteeUserId)
        } else {
            shareMapper.findActiveShelfGrant(invitation.shelfId!!, granteeUserId)
        }

    fun createFromInvitation(
        invitation: ShareInvitation,
        granteeUserId: UUID,
    ): Share {
        val now = OffsetDateTime.now(clock)
        val existing = findGrant(invitation, granteeUserId)
        if (existing != null) return existing
        val share =
            Share(
                id = UuidV7.generate(clock),
                spaceId = invitation.spaceId,
                roomId = invitation.roomId,
                shelfId = invitation.shelfId,
                granteeUserId = granteeUserId,
                permission = invitation.permission,
                grantedBy = invitation.createdBy,
                createdAt = now,
                updatedAt = now,
            )
        shareMapper.insert(share)
        return share
    }

    fun toResponse(share: Share): ShareResponse {
        val roomId = share.roomId
        val resource =
            if (roomId != null) {
                val room = roomMapper.findById(share.spaceId, roomId)
                "ROOM" to (room?.name ?: "삭제된 Room")
            } else {
                val shelf = shelfMapper.findById(share.spaceId, share.shelfId!!)
                "SHELF" to (shelf?.name ?: "삭제된 Shelf")
            }
        return ShareResponse(
            id = share.id,
            resourceType = resource.first,
            resourceName = resource.second,
            roomId = share.roomId,
            shelfId = share.shelfId,
            permission = share.permission,
            granteeUserId = share.granteeUserId,
            granteeDisplayName = userMapper.findById(share.granteeUserId)?.displayName,
            createdAt = share.createdAt,
        )
    }

    private fun requireOwnedShare(
        actor: Actor,
        shareId: UUID,
    ): Share {
        accessService.requireOwner(actor, actor.spaceId)
        val share = shareMapper.findById(shareId)
        if (share == null || share.spaceId != actor.spaceId || share.revokedAt != null) {
            throw notFound("Share를 찾을 수 없습니다.")
        }
        return share
    }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)
}
