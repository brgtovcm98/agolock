package com.agolock.hierarchy.service

import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission

/**
 * M4 공유 Grant를 연결할 포트입니다.
 * target의 Room/Shelf 조상 정보를 사용해 상속된 최대 권한을 반환할 수 있습니다.
 */
interface GrantLookup {
    fun permissionFor(
        actor: Actor,
        target: AccessTarget,
    ): Permission?
}

class NoGrantLookup : GrantLookup {
    override fun permissionFor(
        actor: Actor,
        target: AccessTarget,
    ): Permission? = null
}
