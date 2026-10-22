package com.agolock.sharing.domain

import com.agolock.hierarchy.domain.GrantLevel
import java.time.OffsetDateTime
import java.util.UUID

data class ShareInvitation(
    var id: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var roomId: UUID? = null,
    var shelfId: UUID? = null,
    var invitedEmail: String = "",
    var invitedUserId: UUID? = null,
    var permission: GrantLevel = GrantLevel.VIEW,
    var tokenHash: String = "",
    var status: InvitationStatus = InvitationStatus.PENDING,
    var expiresAt: OffsetDateTime = OffsetDateTime.MIN,
    var createdBy: UUID = UUID(0, 0),
    var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    var acceptedAt: OffsetDateTime? = null,
    var declinedAt: OffsetDateTime? = null,
    var revokedAt: OffsetDateTime? = null,
)
