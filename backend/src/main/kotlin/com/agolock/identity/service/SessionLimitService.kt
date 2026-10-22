package com.agolock.identity.service

import com.agolock.common.config.AuthProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 사용자별 동시 세션 상한을 적용합니다.
 *
 * Spring Session JDBC 저장소를 직접 조회해 가장 오래된 세션부터 제거합니다.
 * `SpringSessionBackedSessionRegistry`는 SecurityContext 저장 전에는 인덱스가
 * 채워지지 않으므로, 수동 인증 흐름에서는 이 명시적 trim이 필요합니다.
 */
@Service
class SessionLimitService(
    private val sessionRepository: ObjectProvider<JdbcIndexedSessionRepository>,
    private val jdbcTemplateProvider: ObjectProvider<JdbcTemplate>,
    private val authProperties: AuthProperties,
) {
    fun trim(
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
                        WHERE PRINCIPAL_NAME = ? AND EXPIRY_TIME > ?
                        ORDER BY LAST_ACCESS_TIME ASC
                        """.trimIndent(),
                        String::class.java,
                        userId.toString(),
                        System.currentTimeMillis(),
                    )
                } else {
                    repository.findByPrincipalName(userId.toString()).keys.toList()
                }

            val currentIsPersisted = currentSessionId in sessionIds
            val overflow =
                if (currentIsPersisted) {
                    sessionIds.size - authProperties.session.max
                } else {
                    sessionIds.size - authProperties.session.max + 1
                }
            if (overflow > 0) {
                sessionIds
                    .filter { it != currentSessionId }
                    .take(overflow)
                    .forEach { repository.deleteById(it) }
            }
        }
    }
}
