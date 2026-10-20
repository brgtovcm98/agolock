package com.agolock.common.error

import org.springframework.http.HttpStatus

data class ApiFieldError(
    val field: String,
    val message: String,
)

open class ApiException(
    val code: String,
    val status: HttpStatus,
    override val message: String,
    val fieldErrors: List<ApiFieldError> = emptyList(),
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
