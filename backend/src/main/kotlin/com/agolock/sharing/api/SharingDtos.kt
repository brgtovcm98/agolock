package com.agolock.sharing.api

import com.agolock.hierarchy.domain.Permission
import com.agolock.sharing.domain.InvitationStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class CreateInvitationRequest(
    val roomId: UUID? = null,
    val shelfId: UUID? = null,
    @field:Email @field:Size(max = 254) val invitedEmail: String,
    @field:NotNull val permission: Permission,
)

data class UpdateShareRequest(
    @field:NotNull val permission: Permission,
)

data class ShareResponse(
    val id: UUID,
    val resourceType: String,
    val resourceName: String,
    val roomId: UUID?,
    val shelfId: UUID?,
    val permission: Permission,
    val granteeUserId: UUID,
    val granteeDisplayName: String?,
    val createdAt: OffsetDateTime,
)

data class ShareListResponse(
    val items: List<ShareResponse>,
)

data class ShareInvitationResponse(
    val id: UUID,
    val resourceType: String,
    val resourceName: String,
    val roomId: UUID?,
    val shelfId: UUID?,
    val invitedEmail: String,
    val permission: Permission,
    val status: InvitationStatus,
    val expiresAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
)

data class ShareInvitationListResponse(
    val items: List<ShareInvitationResponse>,
)
