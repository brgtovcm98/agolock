package com.agolock.common.logging

import com.agolock.common.id.UuidV7
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter(
    private val clock: Clock,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId =
            request
                .getHeader(HEADER)
                ?.takeIf(REQUEST_ID_PATTERN::matches)
                ?: UuidV7.generate(clock).toString()

        MDC.put(MDC_KEY, requestId)
        response.setHeader(HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "request_id"
        private val REQUEST_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$")
    }
}
