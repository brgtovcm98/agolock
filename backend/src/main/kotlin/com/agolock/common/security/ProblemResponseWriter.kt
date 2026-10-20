package com.agolock.common.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import java.net.URI

@Component
class ProblemResponseWriter(
    private val objectMapper: ObjectMapper,
) {
    fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        detail: String,
    ) {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.title =
            when (status) {
                HttpStatus.UNAUTHORIZED -> "인증이 필요합니다"
                HttpStatus.FORBIDDEN -> "권한이 없습니다"
                HttpStatus.TOO_MANY_REQUESTS -> "요청이 너무 많습니다"
                else -> "요청을 처리할 수 없습니다"
            }
        problem.type = URI.create("https://agolock.example/problems/${code.lowercase().replace('_', '-')}")
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", code)
        problem.setProperty("fieldErrors", emptyList<Any>())

        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.writer, problem)
    }
}
