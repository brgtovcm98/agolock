@file:Suppress("LongParameterList")

package com.agolock.identity.service

import com.agolock.common.config.AuthProperties
import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.identity.domain.PasswordResetToken
import com.agolock.identity.domain.UserStatus
import com.agolock.identity.mail.PasswordResetRequestedEvent
import com.agolock.identity.persistence.PasswordResetTokenMapper
import com.agolock.identity.persistence.UserMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime

@Service
class PasswordResetService(
    private val userMapper: UserMapper,
    private val passwordResetTokenMapper: PasswordResetTokenMapper,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
    private val sessionService: SessionService,
    private val rateLimiter: InMemoryRateLimiter,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val authProperties: AuthProperties,
    private val clock: Clock,
) {
    @Transactional
    fun request(
        emailInput: String,
        clientKey: String,
    ) {
        val email = EmailNormalizer.normalize(emailInput)
        rateLimiter.check(
            "password-reset:ip:$clientKey",
            IP_RATE_LIMIT,
            IP_RATE_WINDOW,
        )
        rateLimiter.check(
            "password-reset:${tokenService.hashToken(email)}",
            ACCOUNT_RATE_LIMIT,
            ACCOUNT_RATE_WINDOW,
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
    fun confirm(
        token: String,
        newPassword: String,
        clientKey: String,
    ) {
        rateLimiter.check(
            "password-reset-confirm:ip:$clientKey",
            CONFIRM_RATE_LIMIT,
            CONFIRM_RATE_WINDOW,
        )
        PasswordPolicy.validate(newPassword)
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

    private fun invalidResetToken(): ApiException =
        ApiException(
            code = "PASSWORD_RESET_TOKEN_INVALID",
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            message = "비밀번호 재설정 링크가 만료되었거나 유효하지 않습니다.",
        )

    private companion object {
        const val ACCOUNT_RATE_LIMIT = 5
        const val IP_RATE_LIMIT = 20
        const val CONFIRM_RATE_LIMIT = 10
        val ACCOUNT_RATE_WINDOW: Duration = Duration.ofHours(1)
        val IP_RATE_WINDOW: Duration = Duration.ofHours(1)
        val CONFIRM_RATE_WINDOW: Duration = Duration.ofMinutes(1)
    }
}
