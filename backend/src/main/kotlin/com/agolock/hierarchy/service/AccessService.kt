package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * M2의 계층 리소스는 소유자만 접근할 수 있습니다.
 * M4에서 GrantLookup 구현을 연결하면 이 클래스가 effectivePermission의 단일 지점이 됩니다.
 */
@Service
class AccessService(
    private val spaceOwnership: SpaceOwnership,
    private val grantLookup: GrantLookup,
) {
    @Suppress("ReturnCount")
    fun effectivePermission(
        actor: Actor,
        spaceId: UUID,
    ): Permission {
        if (spaceId != actor.spaceId) return Permission.NONE
        val owner = spaceOwnership.ownerOf(spaceId) ?: return Permission.NONE
        if (owner == actor.userId) return Permission.OWNER
        return grantLookup.permissionFor(actor, spaceId) ?: Permission.NONE
    }

    fun requireView(
        actor: Actor,
        spaceId: UUID,
    ): Permission {
        val permission = effectivePermission(actor, spaceId)
        if (permission == Permission.NONE) {
            throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "리소스를 찾을 수 없습니다.",
            )
        }
        return permission
    }

    fun requireOwner(
        actor: Actor,
        spaceId: UUID,
    ): Permission {
        val permission = effectivePermission(actor, spaceId)
        if (permission == Permission.NONE) {
            throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "리소스를 찾을 수 없습니다.",
            )
        }
        if (!permission.atLeast(Permission.OWNER)) {
            throw ApiException(
                code = "PERMISSION_DENIED",
                status = HttpStatus.FORBIDDEN,
                message = "Space 소유자만 이 작업을 수행할 수 있습니다.",
            )
        }
        return permission
    }

    fun isShared(
        spaceId: UUID,
        actor: Actor,
    ): Boolean {
        val permission = effectivePermission(actor, spaceId)
        return permission != Permission.NONE && permission != Permission.OWNER
    }
}
