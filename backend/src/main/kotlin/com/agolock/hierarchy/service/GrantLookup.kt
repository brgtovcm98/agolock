package com.agolock.hierarchy.service

import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
import java.util.UUID

/**
 * M4 공유 Grant를 연결할 포트입니다. M2에서는 항상 null입니다.
 */
interface GrantLookup {
    fun permissionFor(
        actor: Actor,
        spaceId: UUID,
    ): Permission?
}

@org.springframework.stereotype.Service
class NoGrantLookup : GrantLookup {
    override fun permissionFor(
        actor: Actor,
        spaceId: UUID,
    ): Permission? = null
}
