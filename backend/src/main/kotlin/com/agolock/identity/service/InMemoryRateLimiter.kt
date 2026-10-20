package com.agolock.identity.service

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque

@Component
class InMemoryRateLimiter(
    private val clock: Clock,
) {
    private val windows = mutableMapOf<String, ArrayDeque<Instant>>()

    fun check(
        key: String,
        limit: Int,
        window: Duration,
    ) {
        val now = clock.instant()
        val cutoff = now.minus(window)
        synchronized(windows) {
            val timestamps = windows.getOrPut(key) { ArrayDeque() }
            while (timestamps.isNotEmpty() && timestamps.first().isBefore(cutoff)) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= limit) {
                throw ApiException(
                    code = "RATE_LIMITED",
                    status = HttpStatus.TOO_MANY_REQUESTS,
                    message = "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                    retryAfterSeconds = window.seconds.coerceAtLeast(1),
                )
            }
            timestamps.addLast(now)
        }
    }
}
