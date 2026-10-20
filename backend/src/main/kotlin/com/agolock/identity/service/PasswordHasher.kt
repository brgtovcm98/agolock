package com.agolock.identity.service

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordHasher {
    private val encoder: PasswordEncoder =
        DelegatingPasswordEncoder(
            "argon2",
            mapOf("argon2" to Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()),
        )

    fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    fun matches(
        rawPassword: String,
        encodedPassword: String,
    ): Boolean = encoder.matches(rawPassword, encodedPassword)
}
