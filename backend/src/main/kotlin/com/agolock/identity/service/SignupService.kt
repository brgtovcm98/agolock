@file:Suppress("LongParameterList", "ThrowsCount")

package com.agolock.identity.service

import com.agolock.common.config.AuthProperties
import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.identity.domain.AppUser
import com.agolock.identity.domain.InviteCode
import com.agolock.identity.domain.Space
import com.agolock.identity.domain.UserStatus
import com.agolock.identity.persistence.InviteCodeMapper
import com.agolock.identity.persistence.InviteCodeUseMapper
import com.agolock.identity.persistence.SpaceMapper
import com.agolock.identity.persistence.UserMapper
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@Service
class SignupService(
    private val userMapper: UserMapper,
    private val spaceMapper: SpaceMapper,
    private val inviteCodeMapper: InviteCodeMapper,
    private val inviteCodeUseMapper: InviteCodeUseMapper,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
    private val rateLimiter: InMemoryRateLimiter,
    private val authProperties: AuthProperties,
    private val clock: Clock,
) {
    @Transactional
    fun signup(
        command: SignupCommand,
        clientKey: String,
    ): AuthResult {
        rateLimiter.check("signup:ip:$clientKey", SIGNUP_RATE_LIMIT, SIGNUP_RATE_WINDOW)
        val email = EmailNormalizer.normalize(command.email)
        val displayName = command.displayName.trim()
        PasswordPolicy.validate(command.password)
        ProfilePolicy.validateDisplayName(displayName)

        val invite = resolveInvite(command.inviteCode)
        if (userMapper.findActiveByEmail(email) != null) {
            throw emailAlreadyExists()
        }

        val now = OffsetDateTime.now(clock)
        val user =
            AppUser(
                id = UuidV7.generate(clock),
                email = email,
                passwordHash = passwordHasher.hash(command.password),
                displayName = displayName,
                status = UserStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            )
        try {
            userMapper.insert(user)
        } catch (exception: DuplicateKeyException) {
            throw emailAlreadyExists(exception)
        }

        val space =
            Space(
                id = UuidV7.generate(clock),
                ownerUserId = user.id,
                name = "${displayName}의 Space",
                createdAt = now,
                updatedAt = now,
            )
        spaceMapper.insert(space)

        if (invite != null) {
            consumeInvite(invite, user.id, now)
        }

        return AuthResult(user, space)
    }

    private fun resolveInvite(rawCode: String?): InviteCode? {
        if (!authProperties.signup.inviteRequired) return null

        val code = rawCode?.trim()
        if (code.isNullOrEmpty()) {
            throw ApiException(
                code = "INVITE_CODE_REQUIRED",
                status = HttpStatus.UNPROCESSABLE_ENTITY,
                message = "초대코드가 필요합니다.",
            )
        }

        val invite =
            inviteCodeMapper.findByHashForUpdate(tokenService.hashInviteCode(code))
                ?: throw invalidInviteCode()
        val now = OffsetDateTime.now(clock)
        val expired = invite.expiresAt?.let { !it.isAfter(now) } ?: false
        if (invite.revokedAt != null || expired || invite.usedCount >= invite.maxUses) {
            throw invalidInviteCode()
        }
        return invite
    }

    private fun consumeInvite(
        invite: InviteCode,
        userId: UUID,
        now: OffsetDateTime,
    ) {
        if (inviteCodeMapper.incrementUsedCount(invite.id) == 0) {
            throw invalidInviteCode()
        }
        inviteCodeUseMapper.insert(
            id = UuidV7.generate(clock),
            inviteCodeId = invite.id,
            userId = userId,
            usedAt = now,
        )
    }

    private fun emailAlreadyExists(cause: Throwable? = null): ApiException =
        ApiException(
            code = "EMAIL_ALREADY_EXISTS",
            status = HttpStatus.CONFLICT,
            message = "이미 사용 중인 이메일입니다.",
            cause = cause,
        )

    private fun invalidInviteCode(): ApiException =
        ApiException(
            code = "INVITE_CODE_INVALID",
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            message = "사용할 수 없는 초대코드입니다.",
        )

    private companion object {
        const val SIGNUP_RATE_LIMIT = 10
        val SIGNUP_RATE_WINDOW: Duration = Duration.ofHours(1)
    }
}
