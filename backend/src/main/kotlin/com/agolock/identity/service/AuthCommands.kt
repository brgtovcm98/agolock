package com.agolock.identity.service

import com.agolock.identity.domain.AppUser
import com.agolock.identity.domain.Space
import java.time.Duration

data class SignupCommand(
    val email: String,
    val password: String,
    val displayName: String,
    val inviteCode: String?,
)

data class LoginCommand(
    val email: String,
    val password: String,
)

data class AuthResult(
    val user: AppUser,
    val space: Space,
)

data class CreatedInviteCode(
    val inviteCodeId: java.util.UUID,
    val rawCode: String,
    val maxUses: Int,
    val expiresAt: java.time.OffsetDateTime?,
)

data class LoginLimitPolicy(
    val maxFailures: Int,
    val window: Duration,
    val lockDuration: Duration,
)
