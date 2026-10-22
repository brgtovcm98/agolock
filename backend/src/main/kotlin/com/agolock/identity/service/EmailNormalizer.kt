package com.agolock.identity.service

import java.util.Locale

object EmailNormalizer {
    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
}
