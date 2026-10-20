package com.agolock.identity.service

import com.agolock.common.config.AuthProperties
import com.agolock.identity.security.AppUserPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class SessionService(
    private val securityContextRepository: SecurityContextRepository,
    private val sessionRepository: ObjectProvider<JdbcIndexedSessionRepository>,
    private val jdbcTemplateProvider: ObjectProvider<JdbcTemplate>,
    private val authProperties: AuthProperties,
) {
    fun establish(
        authResult: AuthResult,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        request.getSession(true)
        request.changeSessionId()
        trimOldSessions(authResult.user.id, request.getSession().id)

        val principal = AppUserPrincipal.from(authResult)
        val authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.authorities,
            )
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }

    private fun trimOldSessions(
        userId: UUID,
        currentSessionId: String,
    ) {
        sessionRepository.ifAvailable { repository ->
            val jdbcTemplate = jdbcTemplateProvider.getIfAvailable()
            val sessionIds =
                if (jdbcTemplate != null) {
                    jdbcTemplate.queryForList(
                        """
                        SELECT SESSION_ID
                        FROM SPRING_SESSION
                        WHERE PRINCIPAL_NAME = ?
                        ORDER BY LAST_ACCESS_TIME ASC
                        """.trimIndent(),
                        String::class.java,
                        userId.toString(),
                    )
                } else {
                    repository.findByPrincipalName(userId.toString()).keys.toList()
                }

            val overflow = sessionIds.size - authProperties.session.max + 1
            if (overflow > 0) {
                sessionIds
                    .filter { it != currentSessionId }
                    .take(overflow)
                    .forEach { repository.deleteById(it) }
            }
        }
    }

    fun invalidateAllForUser(userId: UUID) {
        sessionRepository.ifAvailable { repository ->
            repository.findByPrincipalName(userId.toString()).keys.forEach(repository::deleteById)
        }
    }

    fun logout(
        userId: UUID,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        invalidateAllForUser(userId)
        runCatching { request.getSession(false)?.invalidate() }
        SecurityContextHolder.clearContext()
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            clearSessionCookie(request.isSecure).toString(),
        )
    }

    private fun clearSessionCookie(secure: Boolean): ResponseCookie =
        ResponseCookie
            .from("SESSION", "")
            .path("/")
            .maxAge(Duration.ZERO)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .build()
}
