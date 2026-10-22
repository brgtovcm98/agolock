package com.agolock.identity.service

import com.agolock.identity.domain.Space
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 인증 API의 facade입니다.
 *
 * 실제 가입/로그인/계정/비밀번호 재설정은 각 서비스가 담당하고,
 * 컨트롤러는 기존 AuthService 계약만 유지합니다.
 */
@Service
class AuthService(
    private val signupService: SignupService,
    private val loginService: LoginService,
    private val accountService: AccountService,
    private val passwordResetService: PasswordResetService,
) {
    fun signup(
        command: SignupCommand,
        clientKey: String,
    ): AuthResult = signupService.signup(command, clientKey)

    fun login(
        command: LoginCommand,
        clientKey: String,
    ): AuthResult = loginService.login(command, clientKey)

    fun me(userId: UUID): AuthResult = accountService.me(userId)

    fun updateDisplayName(
        userId: UUID,
        displayName: String,
    ): AuthResult = accountService.updateDisplayName(userId, displayName)

    fun updateSpace(
        userId: UUID,
        spaceId: UUID,
        name: String,
    ): Space = accountService.updateSpace(userId, spaceId, name)

    fun requestPasswordReset(
        emailInput: String,
        clientKey: String,
    ) {
        passwordResetService.request(emailInput, clientKey)
    }

    fun confirmPasswordReset(
        token: String,
        newPassword: String,
        clientKey: String,
    ) {
        passwordResetService.confirm(token, newPassword, clientKey)
    }

    companion object {
        const val PASSWORD_MIN_LENGTH = PasswordPolicy.MIN_LENGTH
        const val PASSWORD_MAX_LENGTH = PasswordPolicy.MAX_LENGTH
        const val DISPLAY_NAME_MAX_LENGTH = ProfilePolicy.DISPLAY_NAME_MAX_LENGTH
        const val SPACE_NAME_MAX_LENGTH = ProfilePolicy.SPACE_NAME_MAX_LENGTH
    }
}
