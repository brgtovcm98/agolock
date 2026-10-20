package com.agolock.identity.service

import com.agolock.common.config.AuthProperties
import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class LoginAttemptService(
    private val clock: Clock,
    private val authProperties: AuthProperties,
) {
    private data class Attempt(
        var failures: Int = 0,
        var windowStartedAt: Instant,
        var lockedUntil: Instant? = null,
    )

    private val attempts = mutableMapOf<String, Attempt>()

    fun assertNotLocked(key: String) {
        val now = clock.instant()
        synchronized(attempts) {
            val attempt = attempts[key] ?: return
            val lockedUntil = attempt.lockedUntil
            if (lockedUntil != null) {
                if (now.isBefore(lockedUntil)) {
                    throw lockedException(now, lockedUntil)
                }
                attempts.remove(key)
            }
        }
    }

    fun recordFailure(key: String) {
        val now = clock.instant()
        val policy = authProperties.login
        synchronized(attempts) {
            val attempt =
                attempts.getOrPut(key) {
                    Attempt(windowStartedAt = now)
                }
            if (now.isAfter(attempt.windowStartedAt.plus(policy.window))) {
                attempt.failures = 0
                attempt.windowStartedAt = now
            }
            attempt.failures += 1
            if (attempt.failures >= policy.maxFailures) {
                attempt.lockedUntil = now.plus(policy.lockDuration)
            }
        }
    }

    fun reset(key: String) {
        synchronized(attempts) {
            attempts.remove(key)
        }
    }

    private fun lockedException(
        now: Instant,
        lockedUntil: Instant,
    ): ApiException =
        ApiException(
            code = "RATE_LIMITED",
            status = HttpStatus.TOO_MANY_REQUESTS,
            message = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.",
            retryAfterSeconds =
                java.time.Duration
                    .between(now, lockedUntil)
                    .seconds
                    .coerceAtLeast(1),
        )
}
