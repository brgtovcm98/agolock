package com.agolock.sharing.service

import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.service.GrantLookup
import com.agolock.sharing.persistence.ShareMapper
import org.springframework.stereotype.Service

@Service
class ShareGrantLookup(
    private val shareMapper: ShareMapper,
) : GrantLookup {
    override fun permissionFor(
        actor: Actor,
        target: AccessTarget,
    ): Permission? =
        shareMapper
            .findActiveForTarget(actor.userId, target.roomId, target.shelfId)
            .maxByOrNull { it.permission.ordinal }
            ?.permission
}
