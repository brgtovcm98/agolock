@file:Suppress("MagicNumber")

package com.agolock.identity.service

import com.agolock.common.error.ApiException
import com.agolock.identity.domain.UserStatus
import com.agolock.identity.persistence.SpaceMapper
import com.agolock.identity.persistence.UserMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class LoginService(
    private val userMapper: UserMapper,
    private val spaceMapper: SpaceMapper,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
    private val rateLimiter: InMemoryRateLimiter,
    private val loginAttempts: LoginAttemptService,
) {
    fun login(
        command: LoginCommand,
        clientKey: String,
    ): AuthResult {
        val email = EmailNormalizer.normalize(command.email)
        rateLimiter.check("login:ip:$clientKey", IP_RATE_LIMIT, IP_RATE_WINDOW)
        rateLimiter.check(
            "login:${tokenService.hashToken(email)}:$clientKey",
            ACCOUNT_RATE_LIMIT,
            ACCOUNT_RATE_WINDOW,
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

    private companion object {
        const val ACCOUNT_RATE_LIMIT = 10
        const val IP_RATE_LIMIT = 30
        val ACCOUNT_RATE_WINDOW: Duration = Duration.ofMinutes(1)
        val IP_RATE_WINDOW: Duration = Duration.ofMinutes(1)
    }
}
