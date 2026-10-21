package com.agolock.identity.api

import com.agolock.identity.domain.UserStatus
import com.agolock.identity.service.AuthResult
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class SignupRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    @field:Size(min = 10, max = 128)
    val password: String,
    @field:NotBlank
    @field:Size(min = 1, max = 80)
    val displayName: String,
    val inviteCode: String? = null,
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
    @field:NotBlank
    val password: String,
)

data class UpdateMeRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 80)
    val displayName: String,
)

data class UpdateSpaceRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 120)
    val name: String,
)

data class PasswordResetRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,
)

data class PasswordResetConfirmRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    @field:Size(min = 10, max = 128)
    val newPassword: String,
)

data class AuthConfigResponse(
    val inviteRequired: Boolean,
    val invitationEmailDiscoveryEnabled: Boolean,
    val passwordMinLength: Int,
    val passwordMaxLength: Int,
)

data class CsrfTokenResponse(
    val token: String,
)

data class AuthMeResponse(
    val user: UserResponse,
    val space: SpaceResponse,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: UserStatus,
)

data class SpaceResponse(
    val id: UUID,
    val name: String,
    val ownerUserId: UUID,
)

fun AuthResult.toAuthMeResponse(): AuthMeResponse =
    AuthMeResponse(
        user =
            UserResponse(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                status = user.status,
            ),
        space =
            SpaceResponse(
                id = space.id,
                name = space.name,
                ownerUserId = space.ownerUserId,
            ),
    )
