package com.agolock.identity.service

import com.agolock.common.config.AuthProperties
import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.identity.domain.AppUser
import com.agolock.identity.domain.InviteCode
import com.agolock.identity.domain.PasswordResetToken
import com.agolock.identity.domain.Space
import com.agolock.identity.domain.UserStatus
import com.agolock.identity.mail.PasswordResetRequestedEvent
import com.agolock.identity.persistence.InviteCodeMapper
import com.agolock.identity.persistence.InviteCodeUseMapper
import com.agolock.identity.persistence.PasswordResetTokenMapper
import com.agolock.identity.persistence.SpaceMapper
import com.agolock.identity.persistence.UserMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID

@Service
@Suppress("LongParameterList", "TooManyFunctions")
class AuthService(
    private val userMapper: UserMapper,
    private val spaceMapper: SpaceMapper,
    private val inviteCodeMapper: InviteCodeMapper,
    private val inviteCodeUseMapper: InviteCodeUseMapper,
    private val passwordResetTokenMapper: PasswordResetTokenMapper,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
    private val sessionService: SessionService,
    private val rateLimiter: InMemoryRateLimiter,
    private val loginAttempts: LoginAttemptService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val authProperties: AuthProperties,
    private val clock: Clock,
) {
    @Transactional
    fun signup(
        command: SignupCommand,
        clientKey: String,
    ): AuthResult {
        rateLimiter.check("signup:ip:$clientKey", SIGNUP_RATE_LIMIT, SIGNUP_RATE_WINDOW)
        val email = normalizeEmail(command.email)
        val displayName = command.displayName.trim()
        validatePassword(command.password)
        validateDisplayName(displayName)

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

    fun login(
        command: LoginCommand,
        clientKey: String,
    ): AuthResult {
        val email = normalizeEmail(command.email)
        rateLimiter.check("login:ip:$clientKey", LOGIN_IP_RATE_LIMIT, LOGIN_RATE_WINDOW)
        rateLimiter.check(
            "login:${tokenService.hashToken(email)}:$clientKey",
            LOGIN_RATE_LIMIT,
            LOGIN_RATE_WINDOW,
        )

        val attemptKey = "$email|$clientKey"
        loginAttempts.assertNotLocked(attemptKey)

        val user = userMapper.findActiveByEmail(email)
        val valid =
            user != null &&
                user.status == UserStatus.ACTIVE &&
                passwordHasher.matches(command.password, user.passwordHash)

        if (!valid) {
            loginAttempts.recordFailure(attemptKey)
            throw ApiException(
                code = "INVALID_CREDENTIALS",
                status = HttpStatus.UNAUTHORIZED,
                message = "이메일 또는 비밀번호가 올바르지 않습니다.",
            )
        }

        loginAttempts.reset(attemptKey)
        val space =
            spaceMapper.findByOwnerUserId(user.id)
                ?: throw ApiException(
                    code = "RESOURCE_NOT_FOUND",
                    status = HttpStatus.NOT_FOUND,
                    message = "사용자의 Space를 찾을 수 없습니다.",
                )
        return AuthResult(user, space)
    }

    @Transactional(readOnly = true)
    fun me(userId: UUID): AuthResult {
        val user =
            userMapper
                .findById(userId)
                ?.takeIf { it.deletedAt == null && it.status == UserStatus.ACTIVE }
                ?: throw unauthenticated()
        val space =
            spaceMapper.findByOwnerUserId(user.id)
                ?: throw unauthenticated()
        return AuthResult(user, space)
    }

    @Transactional
    fun updateDisplayName(
        userId: UUID,
        displayName: String,
    ): AuthResult {
        val normalized = displayName.trim()
        validateDisplayName(normalized)
        val updated = userMapper.updateDisplayName(userId, normalized, OffsetDateTime.now(clock))
        if (updated == 0) {
            throw unauthenticated()
        }
        return me(userId)
    }

    @Transactional
    fun updateSpace(
        userId: UUID,
        spaceId: UUID,
        name: String,
    ): Space {
        val normalized = name.trim()
        if (normalized.isEmpty() || normalized.length > SPACE_NAME_MAX_LENGTH) {
            throw ApiException(
                code = "VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                message = "Space 이름은 1자 이상 ${SPACE_NAME_MAX_LENGTH}자 이하여야 합니다.",
            )
        }
        val space = spaceMapper.findById(spaceId)
        if (space == null || space.ownerUserId != userId) {
            throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "Space를 찾을 수 없습니다.",
            )
        }
        val now = OffsetDateTime.now(clock)
        spaceMapper.updateName(spaceId, normalized, now)
        return space.copy(name = normalized, updatedAt = now)
    }

    @Transactional
    fun requestPasswordReset(
        emailInput: String,
        clientKey: String,
    ) {
        val email = normalizeEmail(emailInput)
        rateLimiter.check(
            "password-reset:ip:$clientKey",
            PASSWORD_RESET_IP_RATE_LIMIT,
            PASSWORD_RESET_RATE_WINDOW,
        )
        rateLimiter.check(
            "password-reset:${tokenService.hashToken(email)}",
            PASSWORD_RESET_RATE_LIMIT,
            PASSWORD_RESET_RATE_WINDOW,
        )
        val user = userMapper.findActiveByEmail(email)?.takeIf { it.status == UserStatus.ACTIVE } ?: return

        val now = OffsetDateTime.now(clock)
        val rawToken = tokenService.createToken()
        val token =
            PasswordResetToken(
                id = UuidV7.generate(clock),
                userId = user.id,
                tokenHash = tokenService.hashToken(rawToken),
                expiresAt = now.plus(authProperties.passwordReset.ttl),
                usedAt = null,
                createdAt = now,
            )
        passwordResetTokenMapper.invalidateActiveForUser(user.id, now)
        passwordResetTokenMapper.insert(token)

        applicationEventPublisher.publishEvent(
            PasswordResetRequestedEvent(
                email = user.email,
                displayName = user.displayName,
                token = rawToken,
            ),
        )
    }

    @Transactional
    @Suppress("ThrowsCount")
    fun confirmPasswordReset(
        token: String,
        newPassword: String,
        clientKey: String,
    ) {
        rateLimiter.check(
            "password-reset-confirm:ip:$clientKey",
            PASSWORD_RESET_CONFIRM_RATE_LIMIT,
            PASSWORD_RESET_CONFIRM_RATE_WINDOW,
        )
        validatePassword(newPassword)
        val now = OffsetDateTime.now(clock)
        val row =
            passwordResetTokenMapper.findByHashForUpdate(tokenService.hashToken(token))
                ?: throw invalidResetToken()

        if (row.usedAt != null || !row.expiresAt.isAfter(now)) {
            throw invalidResetToken()
        }

        val user =
            userMapper
                .findById(row.userId)
                ?.takeIf { it.deletedAt == null && it.status == UserStatus.ACTIVE }
                ?: throw invalidResetToken()

        userMapper.updatePassword(user.id, passwordHasher.hash(newPassword), now)
        passwordResetTokenMapper.markUsed(row.id, now)
        passwordResetTokenMapper.invalidateActiveForUser(user.id, now)
        sessionService.invalidateAllForUser(user.id)
    }

    @Suppress("ThrowsCount")
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

    private fun normalizeEmail(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun validatePassword(password: String) {
        if (password.length !in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH) {
            throw ApiException(
                code = "VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                message = "비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상 ${PASSWORD_MAX_LENGTH}자 이하여야 합니다.",
            )
        }
    }

    private fun validateDisplayName(displayName: String) {
        if (displayName.isEmpty() || displayName.length > DISPLAY_NAME_MAX_LENGTH) {
            throw ApiException(
                code = "VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                message = "표시 이름은 1자 이상 ${DISPLAY_NAME_MAX_LENGTH}자 이하여야 합니다.",
            )
        }
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

    private fun invalidResetToken(): ApiException =
        ApiException(
            code = "PASSWORD_RESET_TOKEN_INVALID",
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            message = "비밀번호 재설정 링크가 만료되었거나 유효하지 않습니다.",
        )

    private fun unauthenticated(): ApiException =
        ApiException(
            code = "AUTHENTICATION_REQUIRED",
            status = HttpStatus.UNAUTHORIZED,
            message = "로그인이 필요합니다.",
        )

    companion object {
        const val PASSWORD_MIN_LENGTH = 10
        const val PASSWORD_MAX_LENGTH = 128
        const val DISPLAY_NAME_MAX_LENGTH = 80
        const val SPACE_NAME_MAX_LENGTH = 120
        private const val SIGNUP_RATE_LIMIT = 10
        private const val LOGIN_RATE_LIMIT = 10
        private const val LOGIN_IP_RATE_LIMIT = 30
        private const val PASSWORD_RESET_RATE_LIMIT = 5
        private const val PASSWORD_RESET_IP_RATE_LIMIT = 20
        private const val PASSWORD_RESET_CONFIRM_RATE_LIMIT = 10
        private val SIGNUP_RATE_WINDOW: Duration = Duration.ofHours(1)
        private val LOGIN_RATE_WINDOW: Duration = Duration.ofMinutes(1)
        private val PASSWORD_RESET_RATE_WINDOW: Duration = Duration.ofHours(1)
        private val PASSWORD_RESET_CONFIRM_RATE_WINDOW: Duration = Duration.ofMinutes(1)
    }
}
