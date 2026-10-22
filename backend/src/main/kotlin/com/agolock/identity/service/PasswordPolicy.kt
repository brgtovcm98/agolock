package com.agolock.identity.service

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus

object PasswordPolicy {
    const val MIN_LENGTH = 10
    const val MAX_LENGTH = 128

    fun validate(password: String) {
        if (password.length !in MIN_LENGTH..MAX_LENGTH) {
            throw ApiException(
                code = "VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                message = "비밀번호는 ${MIN_LENGTH}자 이상 ${MAX_LENGTH}자 이하여야 합니다.",
            )
        }
    }
}
