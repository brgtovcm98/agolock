package com.agolock.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.Environment

class ProfileConfigTest {
    // AutoConfiguration을 등록하지 않으므로 DB/Rabbit/S3 없이 프로파일 설정만 검증한다.
    private val runner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())

    @Test
    fun `prod 프로파일은 운영 환경과 보안 기본값을 적용한다`() {
        runner
            .withPropertyValues("spring.profiles.active=prod")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertSecureProfile(context.environment, "production")
            }
    }

    @Test
    fun `staging 프로파일은 스테이징 환경과 보안 기본값을 적용한다`() {
        runner
            .withPropertyValues("spring.profiles.active=staging")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertSecureProfile(context.environment, "staging")
            }
    }

    @Test
    fun `보안 설정은 환경 변수로 재정의할 수 있다`() {
        runner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "SESSION_COOKIE_SECURE=false",
                "FORWARD_HEADERS_STRATEGY=none",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.environment.getProperty("server.servlet.session.cookie.secure"))
                    .isEqualTo("false")
                assertThat(context.environment.getProperty("server.forward-headers-strategy"))
                    .isEqualTo("none")
            }
    }

    private fun assertSecureProfile(
        environment: Environment,
        expectedEnvironment: String,
    ) {
        assertThat(environment.getProperty("agolock.environment")).isEqualTo(expectedEnvironment)
        assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true")
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework")
        assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false")
    }
}
