package com.agolock.identity.service

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus

object ProfilePolicy {
    const val DISPLAY_NAME_MAX_LENGTH = 80
    const val SPACE_NAME_MAX_LENGTH = 120

    fun validateDisplayName(displayName: String) {
        if (displayName.isEmpty() || displayName.length > DISPLAY_NAME_MAX_LENGTH) {
            throw ApiException(
                code = "VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                message = "표시 이름은 1자 이상 ${DISPLAY_NAME_MAX_LENGTH}자 이하여야 합니다.",
            )
        }
    }

    fun validateSpaceName(name: String) {
        if (name.isEmpty() || name.length > SPACE_NAME_MAX_LENGTH) {
            throw ApiException(
                code = "VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                message = "Space 이름은 1자 이상 ${SPACE_NAME_MAX_LENGTH}자 이하여야 합니다.",
            )
        }
    }
}
