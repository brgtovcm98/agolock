package com.agolock.common.logging

import com.agolock.identity.security.AppUserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val AUTHENTICATED_FILTER_ORDER_OFFSET = 10

@Component
@Order(Ordered.LOWEST_PRECEDENCE - AUTHENTICATED_FILTER_ORDER_OFFSET)
class AuthenticatedRequestContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AppUserPrincipal
        if (principal != null) {
            MDC.put(USER_ID, principal.userId.toString())
            MDC.put(SPACE_ID, principal.spaceId.toString())
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(USER_ID)
            MDC.remove(SPACE_ID)
        }
    }

    private companion object {
        const val USER_ID = "user_id"
        const val SPACE_ID = "space_id"
    }
}
