package com.agolock.search.service

import java.util.Base64

object SearchCursorCodec {
    fun encode(offset: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString("o:$offset".toByteArray())

    fun decode(cursor: String?): Int {
        if (cursor.isNullOrBlank()) return 0
        val decoded = String(Base64.getUrlDecoder().decode(cursor))
        return decoded.removePrefix("o:").toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}
