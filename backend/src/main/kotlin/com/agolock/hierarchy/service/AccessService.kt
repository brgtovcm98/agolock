package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 계층 리소스와 M3 Inventory 위치의 ACL 단일 판정 지점입니다.
 *
 * M2는 소유자만 유효 권한을 가지며, M4의 GrantLookup 구현이 추가되면
 * 같은 target 문맥으로 Room/Shelf 상속과 isShared를 계산합니다.
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
        return AccessTarget(spaceId, owner, roomId, shelfId, boxId)
    }

    fun effectivePermission(
        actor: Actor,
        target: AccessTarget,
    ): Permission {
        if (target.ownerUserId == actor.userId) return Permission.OWNER
        return grantLookup.permissionFor(actor, target) ?: Permission.NONE
    }

    /**
     * M2 호출부 호환용. Space 단위 소유권만 판정합니다.
     */
    @Suppress("ReturnCount")
    fun effectivePermission(
        actor: Actor,
        spaceId: UUID,
    ): Permission {
        if (spaceId != actor.spaceId) return Permission.NONE
        val owner = spaceOwnership.ownerOf(spaceId) ?: return Permission.NONE
        return effectivePermission(actor, AccessTarget(spaceId, owner))
    }

    fun require(
        actor: Actor,
        target: AccessTarget,
        minimum: Permission,
    ): Permission {
        val permission = effectivePermission(actor, target)
        if (permission == Permission.NONE) throw notFound()
        if (!permission.atLeast(minimum)) throw forbidden()
        return permission
    }

    fun requireView(
        actor: Actor,
        spaceId: UUID,
    ): Permission = require(actor, target(spaceId), Permission.VIEW)

    fun requireOwner(
        actor: Actor,
        spaceId: UUID,
    ): Permission = require(actor, target(spaceId), Permission.OWNER)

    fun isShared(
        actor: Actor,
        target: AccessTarget,
    ): Boolean {
        val permission = effectivePermission(actor, target)
        return permission != Permission.NONE && permission != Permission.OWNER
    }

    fun resolveAll(
        actor: Actor,
        targets: Collection<AccessTarget>,
    ): Map<String, Permission> = targets.associate { it.key to effectivePermission(actor, it) }

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
