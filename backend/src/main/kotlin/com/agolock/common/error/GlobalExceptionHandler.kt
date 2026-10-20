package com.agolock.common.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        exception: ApiException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = exception.status,
            code = exception.code,
            detail = exception.message,
            request = request,
            fieldErrors = exception.fieldErrors,
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val errors =
            exception.bindingResult.fieldErrors.map {
                ApiFieldError(it.field, it.defaultMessage ?: "값이 올바르지 않습니다.")
            }
        return problem(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_FAILED",
            detail = "요청 값을 확인해 주세요.",
            request = request,
            fieldErrors = errors,
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        exception: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val errors =
            exception.constraintViolations.map {
                ApiFieldError(it.propertyPath.toString(), it.message)
            }
        return problem(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_FAILED",
            detail = "요청 값을 확인해 주세요.",
            request = request,
            fieldErrors = errors,
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_FAILED",
            detail = "요청 본문을 읽을 수 없습니다.",
            request = request,
        )

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.NOT_FOUND,
            code = "RESOURCE_NOT_FOUND",
            detail = "요청한 리소스를 찾을 수 없습니다.",
            request = request,
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error(
            "처리하지 못한 예외 request_id={} path={}",
            MDC.get(REQUEST_ID_MDC_KEY),
            request.requestURI,
            exception,
        )
        return problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_ERROR",
            detail = "서버에서 요청을 처리하지 못했습니다.",
            request = request,
        )
    }

    private fun problem(
        status: HttpStatus,
        code: String,
        detail: String,
        request: HttpServletRequest,
        fieldErrors: List<ApiFieldError> = emptyList(),
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.title = titleOf(status)
        problem.type = URI.create("https://agolock.example/problems/${code.lowercase().replace('_', '-')}")
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("code", code)
        problem.setProperty("fieldErrors", fieldErrors)
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }

    private fun titleOf(status: HttpStatus): String =
        when (status) {
            HttpStatus.BAD_REQUEST -> "요청이 올바르지 않습니다"
            HttpStatus.UNAUTHORIZED -> "인증이 필요합니다"
            HttpStatus.FORBIDDEN -> "권한이 없습니다"
            HttpStatus.NOT_FOUND -> "리소스를 찾을 수 없습니다"
            HttpStatus.CONFLICT -> "요청이 충돌했습니다"
            HttpStatus.UNPROCESSABLE_ENTITY -> "요청을 처리할 수 없습니다"
            HttpStatus.TOO_MANY_REQUESTS -> "요청이 너무 많습니다"
            HttpStatus.SERVICE_UNAVAILABLE -> "일시적으로 사용할 수 없습니다"
            else -> "요청을 처리하지 못했습니다"
        }

    private companion object {
        const val REQUEST_ID_MDC_KEY = "request_id"
    }
}
