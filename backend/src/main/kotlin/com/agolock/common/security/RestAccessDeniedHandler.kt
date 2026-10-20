package com.agolock.common.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.csrf.InvalidCsrfTokenException
import org.springframework.security.web.csrf.MissingCsrfTokenException
import org.springframework.stereotype.Component

@Component
class RestAccessDeniedHandler(
    private val problemResponseWriter: ProblemResponseWriter,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        val csrfFailure =
            accessDeniedException is InvalidCsrfTokenException ||
                accessDeniedException is MissingCsrfTokenException
        problemResponseWriter.write(
            request = request,
            response = response,
            status = HttpStatus.FORBIDDEN,
            code = if (csrfFailure) "CSRF_INVALID" else "PERMISSION_DENIED",
            detail =
                if (csrfFailure) {
                    "CSRF 토큰이 유효하지 않습니다."
                } else {
                    "이 작업을 수행할 권한이 없습니다."
                },
        )
    }
}
