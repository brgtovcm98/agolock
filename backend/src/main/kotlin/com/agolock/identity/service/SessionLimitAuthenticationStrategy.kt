package com.agolock.identity.service

import com.agolock.identity.security.AppUserPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.stereotype.Service

/**
 * 세션 수 제한과 세션 ID 회전을 하나의 SessionAuthenticationStrategy로 묶습니다.
 *
 * Spring Security의 기본 ConcurrentSessionControl은 Spring Session JDBC 인덱스가
 * SecurityContext 저장 후에 갱신되는 수동 인증 흐름에서 세션 수를 즉시 세지 못합니다.
 * 따라서 JDBC를 직접 조회하는 SessionLimitService로 먼저 trim한 뒤 세션 ID를 회전합니다.
 */
@Service
class SessionLimitAuthenticationStrategy(
    private val sessionLimitService: SessionLimitService,
) : SessionAuthenticationStrategy {
    private val fixation = ChangeSessionIdAuthenticationStrategy()

    override fun onAuthentication(
        authentication: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val session = request.getSession(true)
        val principal = authentication.principal
        if (principal is AppUserPrincipal) {
            sessionLimitService.trim(principal.userId, session.id)
        }
        fixation.onAuthentication(authentication, request, response)
    }
}
