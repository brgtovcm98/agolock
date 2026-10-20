package com.agolock.common.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * M0에서 필요한 최소 애플리케이션 설정입니다.
 *
 * S3와 메일 값은 아직 기능에서 사용하지 않지만, 이후 마일스톤에서 설정 누락이
 * 늦게 발견되지 않도록 기동 시점에 검증합니다.
 */
@ConfigurationProperties(prefix = "agolock")
@Validated
class AppProperties {
    @field:NotBlank
    var environment: String = "local"

    @field:NotBlank
    var publicBaseUrl: String = "http://localhost:5173"

    @field:Valid
    var storage: Storage = Storage()

    @field:Valid
    var mail: Mail = Mail()

    class Storage {
        @field:NotBlank
        var endpoint: String = ""

        @field:NotBlank
        var bucket: String = ""

        @field:NotBlank
        var accessKey: String = ""

        @field:NotBlank
        var secretKey: String = ""
    }

    class Mail {
        @field:NotBlank
        var host: String = ""

        @field:Min(MIN_PORT)
        @field:Max(MAX_PORT)
        var port: Int = DEFAULT_SMTP_PORT
    }

    private companion object {
        const val MIN_PORT = 1L
        const val MAX_PORT = 65535L
        const val DEFAULT_SMTP_PORT = 1025
    }
}
