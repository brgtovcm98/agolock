package com.agolock.sharing.domain

import com.agolock.hierarchy.domain.GrantLevel
import java.time.OffsetDateTime
import java.util.UUID

data class Share(
    var id: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var roomId: UUID? = null,
    var shelfId: UUID? = null,
    var granteeUserId: UUID = UUID(0, 0),
    var permission: GrantLevel = GrantLevel.VIEW,
    var grantedBy: UUID = UUID(0, 0),
    var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    var updatedAt: OffsetDateTime = OffsetDateTime.MIN,
    var revokedAt: OffsetDateTime? = null,
)
