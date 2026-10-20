package com.agolock.sharing.service

import com.agolock.hierarchy.domain.Permission
import java.util.UUID

data class CreateInvitationCommand(
    val roomId: UUID?,
    val shelfId: UUID?,
    val invitedEmail: String,
    val permission: Permission,
)
