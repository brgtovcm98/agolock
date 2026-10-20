package com.agolock.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun agolockOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("AGO Lock API")
                    .version("0.1.0")
                    .description("M0 기반 부트스트랩의 HTTP API입니다. 도메인 API는 M1부터 추가됩니다."),
            ).servers(listOf(Server().url("/").description("Application root")))
}
