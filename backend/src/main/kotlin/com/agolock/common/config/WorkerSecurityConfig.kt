package com.agolock.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

/**
 * worker는 웹 요청을 받지 않지만, 공용 AuthService가 SessionService를 통해
 * SecurityContextRepository를 요구합니다. worker에는 저장소만 제공합니다.
 */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
class WorkerSecurityConfig {
    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()
}
