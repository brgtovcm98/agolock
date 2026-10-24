package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus

object InventoryQueryText {
    private const val MAX_LENGTH = 200

    fun normalize(value: String?): String? {
        if (value != null && value.length > MAX_LENGTH) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "검색어는 200자 이하여야 합니다.")
        }
        return value?.trim()?.takeIf(String::isNotEmpty)
    }
}
