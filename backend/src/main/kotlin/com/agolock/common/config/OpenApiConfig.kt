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
                    .version("0.3.0")
                    .description("M1 Identity·Space와 M2 계층 API입니다. 전체 도메인 계약은 docs/generated/openapi.yaml을 따릅니다."),
            ).servers(listOf(Server().url("/").description("Application root")))
}
