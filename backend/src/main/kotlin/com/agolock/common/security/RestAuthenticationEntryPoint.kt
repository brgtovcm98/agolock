package com.agolock.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class RestAuthenticationEntryPoint(
    private val problemResponseWriter: ProblemResponseWriter,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        problemResponseWriter.write(
            request = request,
            response = response,
            status = HttpStatus.UNAUTHORIZED,
            code = "AUTHENTICATION_REQUIRED",
            detail = "로그인이 필요합니다.",
        )
    }
}
