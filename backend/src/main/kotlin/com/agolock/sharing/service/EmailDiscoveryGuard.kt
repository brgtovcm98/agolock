package com.agolock.sharing.service

import com.agolock.common.config.AuthProperties
import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.persistence.UserMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class EmailDiscoveryGuard(
    private val authProperties: AuthProperties,
    private val userMapper: UserMapper,
) {
    @Suppress("ThrowsCount")
    fun requireVerified(actor: Actor) {
        if (!authProperties.invitation.emailDiscoveryEnabled) {
            throw ApiException(
                "EMAIL_DISCOVERY_DISABLED",
                HttpStatus.FORBIDDEN,
                "메일의 초대 링크로 수락해 주세요.",
            )
        }
        val user =
            userMapper.findById(actor.userId)
                ?: throw ApiException("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")
        if (user.emailVerifiedAt == null) {
            throw ApiException("EMAIL_VERIFICATION_REQUIRED", HttpStatus.FORBIDDEN, "이메일 인증 후 받은 초대를 사용할 수 있습니다.")
        }
    }
}
