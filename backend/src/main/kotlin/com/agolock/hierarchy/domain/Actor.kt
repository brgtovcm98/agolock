package com.agolock.hierarchy.domain

import java.util.UUID

data class Actor(
    val userId: UUID,
    val spaceId: UUID,
) {
    companion object {
        fun from(principal: com.agolock.identity.security.AppUserPrincipal): Actor =
            Actor(userId = principal.userId, spaceId = principal.spaceId)
    }
}
