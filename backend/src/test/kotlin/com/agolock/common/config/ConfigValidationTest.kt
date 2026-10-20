package com.agolock.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class ConfigValidationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration::class.java))
            .withUserConfiguration(PropertiesTestConfig::class.java)

    @Test
    fun `필수 설정이 없으면 컨텍스트가 실패한다`() {
        runner.run { context ->
            assertThat(context).hasFailed()
            val failureMessages =
                generateSequence(context.startupFailure) { it.cause }
                    .mapNotNull { it.message }
                    .joinToString("\n")
            assertThat(failureMessages).contains("storage.endpoint")
        }
    }

    @Test
    fun `필수 설정이 있으면 컨텍스트가 기동한다`() {
        runner
            .withPropertyValues(
                "agolock.environment=test",
                "agolock.public-base-url=http://localhost:5173",
                "agolock.storage.endpoint=http://localhost:9000",
                "agolock.storage.bucket=agolock-test",
                "agolock.storage.access-key=test",
                "agolock.storage.secret-key=test",
                "agolock.mail.host=localhost",
                "agolock.mail.port=1025",
            ).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(AppProperties::class.java).environment).isEqualTo("test")
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties::class)
    class PropertiesTestConfig
}
