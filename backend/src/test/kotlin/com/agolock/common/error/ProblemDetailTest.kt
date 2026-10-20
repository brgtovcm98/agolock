package com.agolock.common.error

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

class ProblemDetailTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `도메인 오류를 problem+json으로 변환한다`() {
        val response =
            mockMvc
                .perform(get("/test/forbidden"))
                .andReturn()
                .response

        assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        assertThat(response.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        assertThat(response.contentAsString).contains("\"code\":\"PERMISSION_DENIED\"")
        assertThat(response.contentAsString).contains("\"field\":\"inventoryId\"")
    }

    @Test
    fun `검증 오류를 필드 오류 목록으로 변환한다`() {
        val response =
            mockMvc
                .perform(
                    post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":""}"""),
                ).andReturn()
                .response

        assertThat(response.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(response.contentAsString).contains("\"code\":\"VALIDATION_FAILED\"")
        assertThat(response.contentAsString).contains("\"field\":\"name\"")
    }

    @Test
    fun `예상하지 못한 예외의 내부 메시지를 노출하지 않는다`() {
        val response =
            mockMvc
                .perform(get("/test/unexpected"))
                .andReturn()
                .response

        assertThat(response.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(response.contentAsString).doesNotContain("secret detail")
        assertThat(response.contentAsString).contains("\"code\":\"INTERNAL_ERROR\"")
    }

    @RestController
    @RequestMapping("/test")
    class TestController {
        @GetMapping("/forbidden")
        fun forbidden(): String =
            throw ApiException(
                code = "PERMISSION_DENIED",
                status = HttpStatus.FORBIDDEN,
                message = "권한이 없습니다.",
                fieldErrors = listOf(ApiFieldError("inventoryId", "이 재고를 수정할 권한이 없습니다.")),
            )

        @GetMapping("/unexpected")
        fun unexpected(): String = error("secret detail")

        @PostMapping("/validate")
        fun validate(
            @Valid @RequestBody payload: Payload,
        ): String = payload.name
    }

    data class Payload(
        @field:NotBlank
        val name: String,
    )
}
