package com.agolock.identity.api

import com.agolock.common.config.AuthProperties
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.identity.service.AuthService
import com.agolock.identity.service.LoginCommand
import com.agolock.identity.service.SessionService
import com.agolock.identity.service.SignupCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
class AuthController(
    private val authService: AuthService,
    private val sessionService: SessionService,
    private val authProperties: AuthProperties,
) {
    @GetMapping("/config")
    @Operation(operationId = "getAuthConfig", summary = "가입·로그인 설정")
    fun config(): AuthConfigResponse =
        AuthConfigResponse(
            inviteRequired = authProperties.signup.inviteRequired,
            passwordMinLength = AuthService.PASSWORD_MIN_LENGTH,
            passwordMaxLength = AuthService.PASSWORD_MAX_LENGTH,
        )

    @GetMapping("/csrf")
    @Operation(operationId = "getCsrfToken", summary = "CSRF 토큰 발급")
    fun csrf(token: CsrfToken): CsrfTokenResponse = CsrfTokenResponse(token.token)

    @PostMapping("/signup")
    @Operation(operationId = "signup", summary = "회원가입")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<AuthMeResponse> {
        val result =
            authService.signup(
                SignupCommand(
                    email = request.email,
                    password = request.password,
                    displayName = request.displayName,
                    inviteCode = request.inviteCode,
                ),
                clientKey(httpRequest),
            )
        sessionService.establish(result, httpRequest, httpResponse)
        return ResponseEntity.status(HttpStatus.CREATED).body(result.toAuthMeResponse())
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "로그인")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthMeResponse {
        val result =
            authService.login(
                LoginCommand(email = request.email, password = request.password),
                clientKey(httpRequest),
            )
        sessionService.establish(result, httpRequest, httpResponse)
        return result.toAuthMeResponse()
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "로그아웃")
    fun logout(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<Void> {
        sessionService.logout(principal.userId, httpRequest, httpResponse)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    @Operation(operationId = "getMe", summary = "현재 사용자와 Space")
    fun me(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): AuthMeResponse = authService.me(principal.userId).toAuthMeResponse()

    @PatchMapping("/me")
    @Operation(operationId = "updateMe", summary = "표시 이름 수정")
    fun updateMe(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: UpdateMeRequest,
    ): UserResponse = authService.updateDisplayName(principal.userId, request.displayName).user.toResponse()

    @PostMapping("/password-reset/request")
    @Operation(operationId = "requestPasswordReset", summary = "비밀번호 재설정 요청")
    fun requestPasswordReset(
        @Valid @RequestBody request: PasswordResetRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Void> {
        authService.requestPasswordReset(request.email, clientKey(httpRequest))
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/password-reset/confirm")
    @Operation(operationId = "confirmPasswordReset", summary = "비밀번호 재설정 확인")
    fun confirmPasswordReset(
        @Valid @RequestBody request: PasswordResetConfirmRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Void> {
        authService.confirmPasswordReset(request.token, request.newPassword, clientKey(httpRequest))
        return ResponseEntity.noContent().build()
    }

    private fun clientKey(request: HttpServletRequest): String = request.remoteAddr ?: "unknown"
}

private fun com.agolock.identity.domain.AppUser.toResponse(): UserResponse =
    UserResponse(
        id = id,
        email = email,
        displayName = displayName,
        status = status,
    )

private fun com.agolock.identity.service.AuthResult.toResponse(): UserResponse = user.toResponse()
