package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.GrantLevel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 계층 리소스와 M3 Inventory 위치의 ACL 단일 판정 지점입니다.
 *
 * 소유권은 [EffectivePermission.Owner], 공유 Grant는 [EffectivePermission.Granted]로
 * 계산해 `VIEW < EDIT < MANAGE` 비교와 응답 문자열 변환을 분리합니다.
 */
@Service
class AccessService(
    private val spaceOwnership: SpaceOwnership,
    private val grantLookup: GrantLookup,
) {
    fun target(
        spaceId: UUID,
        roomId: UUID? = null,
        shelfId: UUID? = null,
        boxId: UUID? = null,
    ): AccessTarget {
        val owner = spaceOwnership.ownerOf(spaceId) ?: throw notFound()
        return when {
            boxId != null -> {
                require(roomId != null && shelfId != null) { "Box target에는 roomId와 shelfId가 필요합니다." }
                AccessTarget.Box(spaceId, owner, roomId, shelfId, boxId)
            }
            shelfId != null -> {
                require(roomId != null) { "Shelf target에는 roomId가 필요합니다." }
                AccessTarget.Shelf(spaceId, owner, roomId, shelfId)
            }
            roomId != null -> AccessTarget.Room(spaceId, owner, roomId)
            else -> AccessTarget.Space(spaceId, owner)
        }
    }

    fun effectivePermission(
        actor: Actor,
        target: AccessTarget,
    ): EffectivePermission {
        if (target.ownerUserId == actor.userId) return EffectivePermission.Owner
        return grantLookup
            .permissionFor(actor, target)
            ?.let(EffectivePermission::Granted)
            ?: EffectivePermission.None
    }

    /**
     * M2 호출부 호환용. Space 단위 소유권만 판정합니다.
     */
    @Suppress("ReturnCount")
    fun effectivePermission(
        actor: Actor,
        spaceId: UUID,
    ): EffectivePermission {
        if (spaceId != actor.spaceId) return EffectivePermission.None
        val owner = spaceOwnership.ownerOf(spaceId) ?: return EffectivePermission.None
        return effectivePermission(actor, AccessTarget.Space(spaceId, owner))
    }

    fun require(
        actor: Actor,
        target: AccessTarget,
        minimum: GrantLevel,
    ): EffectivePermission {
        val permission = effectivePermission(actor, target)
        if (permission is EffectivePermission.None) throw notFound()
        if (!permission.atLeast(minimum)) throw forbidden()
        return permission
    }

    fun requireView(
        actor: Actor,
        spaceId: UUID,
    ): EffectivePermission = require(actor, target(spaceId), GrantLevel.VIEW)

    fun requireOwner(
        actor: Actor,
        spaceId: UUID,
    ): EffectivePermission {
        val permission = effectivePermission(actor, target(spaceId))
        if (permission !is EffectivePermission.Owner) throw forbidden()
        return permission
    }

    fun isShared(
        actor: Actor,
        target: AccessTarget,
    ): Boolean = effectivePermission(actor, target) is EffectivePermission.Granted

    private fun notFound(): ApiException =
        ApiException(
            code = "RESOURCE_NOT_FOUND",
            status = HttpStatus.NOT_FOUND,
            message = "리소스를 찾을 수 없습니다.",
        )

    private fun forbidden(): ApiException =
        ApiException(
            code = "PERMISSION_DENIED",
            status = HttpStatus.FORBIDDEN,
            message = "이 작업을 수행할 권한이 없습니다.",
        )
}
