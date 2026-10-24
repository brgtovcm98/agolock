package com.agolock.inventory.domain

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus.BAD_REQUEST

/** HTTP 검증을 우회한 내부 호출에도 같은 필드 규칙을 적용합니다. */
object ItemFieldPolicy {
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_DESCRIPTION_LENGTH = 2000

    fun name(value: String): String =
        value.trim().also {
            if (it.isEmpty() || it.length > MAX_NAME_LENGTH) invalid("이름은 1~120자여야 합니다.")
        }

    fun description(value: String?): String? =
        value?.trim()?.also {
            if (it.length > MAX_DESCRIPTION_LENGTH) invalid("설명은 2000자 이하여야 합니다.")
        }

    fun price(value: Int?): Int? = value.also { if (it != null && it < 0) invalid("가격은 0원 이상이어야 합니다.") }

    fun version(value: Long) {
        if (value < 0) invalid("version은 0 이상이어야 합니다.")
    }

    private fun invalid(message: String): Nothing = throw ApiException("VALIDATION_FAILED", BAD_REQUEST, message)
}
