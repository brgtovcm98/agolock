package com.agolock.common.config

import com.agolock.common.security.RestAccessDeniedHandler
import com.agolock.common.security.RestAuthenticationEntryPoint
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.session.HttpSessionEventPublisher

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig(
    private val authenticationEntryPoint: RestAuthenticationEntryPoint,
    private val accessDeniedHandler: RestAccessDeniedHandler,
    @Value("\${server.servlet.session.cookie.secure:false}") private val secureCookies: Boolean,
    @Value("\${springdoc.api-docs.enabled:false}") private val apiDocsEnabled: Boolean,
) {
    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun httpSessionEventPublisher(): HttpSessionEventPublisher = HttpSessionEventPublisher()

    @Bean
    fun csrfTokenRepository(): CookieCsrfTokenRepository =
        CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieCustomizer { cookie ->
                cookie.path("/").sameSite("Lax").secure(secureCookies)
            }
        }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        csrfTokenRepository: CookieCsrfTokenRepository,
    ): SecurityFilterChain {
        val csrfRequestHandler =
            CsrfTokenRequestAttributeHandler().apply {
                setCsrfRequestAttributeName(null)
            }

        http
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler)
            }.authorizeHttpRequests { authorize ->
                PUBLIC_ENDPOINTS.forEach { authorize.requestMatchers(it).permitAll() }
                if (apiDocsEnabled) {
                    authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                }
                authorize.requestMatchers("/actuator/**").denyAll()
                authorize.anyRequest().authenticated()
            }.requestCache { cache ->
                cache.disable()
            }.sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                session.sessionFixation { fixation -> fixation.migrateSession() }
            }.securityContext { context ->
                context.requireExplicitSave(true)
            }.exceptionHandling { exception ->
                exception
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }.logout { logout ->
                logout.disable()
            }.headers { headers ->
                headers.frameOptions { frame -> frame.deny() }
            }

        return http.build()
    }

    private companion object {
        val PUBLIC_ENDPOINTS =
            arrayOf(
                "/api/v1/auth/config",
                "/api/v1/auth/csrf",
                "/api/v1/auth/signup",
                "/api/v1/auth/login",
                "/api/v1/auth/password-reset/request",
                "/api/v1/auth/password-reset/confirm",
                "/api/v1/auth/email-verification/confirm",
                "/error",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
            )
    }
}
