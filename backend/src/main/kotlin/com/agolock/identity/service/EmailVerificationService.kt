@file:Suppress("MaxLineLength", "MagicNumber")

package com.agolock.identity.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.domain.EmailVerificationToken
import com.agolock.identity.mail.MailPort
import com.agolock.identity.persistence.EmailVerificationTokenMapper
import com.agolock.identity.persistence.UserMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class EmailVerificationService(
    private val tokenMapper: EmailVerificationTokenMapper,
    private val userMapper: UserMapper,
    private val tokenService: TokenService,
    private val mailPort: MailPort,
    private val clock: Clock,
) {
    @Transactional
    fun request(actor: Actor) {
        val user = userMapper.findById(actor.userId) ?: throw unauthenticated()
        if (user.emailVerifiedAt != null) return
        val now = OffsetDateTime.now(clock)
        tokenMapper.invalidateUnused(user.id, now)
        val rawToken = tokenService.createToken()
        tokenMapper.insert(
            EmailVerificationToken(
                UuidV7.generate(clock),
                user.id,
                tokenService.hashToken(rawToken),
                now.plusHours(24),
                null,
                now,
            ),
        )
        runCatching { mailPort.sendEmailVerification(user.email, user.displayName, rawToken) }
    }

    @Transactional
    fun confirm(token: String) {
        val now = OffsetDateTime.now(clock)
        val verification =
            tokenMapper.findByTokenHashForUpdate(tokenService.hashToken(token))
                ?: throw invalid()
        if (verification.usedAt != null || !verification.expiresAt.isAfter(now)) throw invalid()
        tokenMapper.consume(verification.id, now)
        userMapper.markEmailVerified(verification.userId, now)
    }

    private fun invalid() = ApiException("EMAIL_VERIFICATION_TOKEN_INVALID", HttpStatus.UNPROCESSABLE_ENTITY, "이메일 인증 토큰이 올바르지 않습니다.")

    private fun unauthenticated() = ApiException("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")
}
