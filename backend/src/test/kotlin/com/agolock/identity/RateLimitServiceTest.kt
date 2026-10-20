package com.agolock.identity

import com.agolock.common.config.AuthProperties
import com.agolock.common.error.ApiException
import com.agolock.identity.service.InMemoryRateLimiter
import com.agolock.identity.service.LoginAttemptService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class RateLimitServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-20T00:00:00Z"))

    @Test
    fun `고정 윈도 한도를 넘으면 429이고 윈도가 지나면 다시 허용한다`() {
        val limiter = InMemoryRateLimiter(clock)

        limiter.check("login", 2, Duration.ofMinutes(1))
        limiter.check("login", 2, Duration.ofMinutes(1))
        assertThatThrownBy { limiter.check("login", 2, Duration.ofMinutes(1)) }
            .isInstanceOf(ApiException::class.java)
            .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)

        clock.advance(Duration.ofMinutes(2))
        limiter.check("login", 2, Duration.ofMinutes(1))
        assertThat(limiter).isNotNull
    }

    @Test
    fun `로그인 실패가 한도에 도달하면 잠금 후 시간이 지나야 풀린다`() {
        val properties =
            AuthProperties().apply {
                login.maxFailures = 2
                login.window = Duration.ofMinutes(1)
                login.lockDuration = Duration.ofMinutes(5)
            }
        val attempts = LoginAttemptService(clock, properties)

        attempts.recordFailure("ip|user")
        attempts.recordFailure("ip|user")
        assertThatThrownBy { attempts.assertNotLocked("ip|user") }
            .isInstanceOf(ApiException::class.java)

        clock.advance(Duration.ofMinutes(6))
        attempts.assertNotLocked("ip|user")
    }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }

        override fun instant(): Instant = current

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }
}
