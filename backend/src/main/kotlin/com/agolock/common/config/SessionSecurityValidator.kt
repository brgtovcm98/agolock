package com.agolock.common.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SessionSecurityValidator(
    private val appProperties: AppProperties,
    @Value("\${server.servlet.session.cookie.secure:false}")
    private val cookieSecure: Boolean,
) {
    @PostConstruct
    fun validate() {
        if (appProperties.environment !in PROTECTED_ENVIRONMENTS) return
        check(cookieSecure) { "non-local 환경에서는 SESSION_COOKIE_SECURE=true가 필요합니다." }
        check(appProperties.publicBaseUrl.startsWith("https://")) {
            "non-local 환경에서는 PUBLIC_BASE_URL이 https여야 합니다."
        }
    }

    private companion object {
        val PROTECTED_ENVIRONMENTS = setOf("staging", "production")
    }
}
