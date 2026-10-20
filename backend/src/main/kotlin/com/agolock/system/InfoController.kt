package com.agolock.system

import com.agolock.common.config.AppProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "system")
class InfoController(
    private val properties: AppProperties,
) {
    @GetMapping("/info")
    @Operation(
        summary = "애플리케이션 정보",
        description = "M0 부트스트랩의 이름, 버전, 실행 환경을 반환합니다.",
    )
    fun info(): InfoResponse =
        InfoResponse(
            name = "agolock",
            version = "0.1.0",
            environment = properties.environment,
        )
}

data class InfoResponse(
    val name: String,
    val version: String,
    val environment: String,
)
