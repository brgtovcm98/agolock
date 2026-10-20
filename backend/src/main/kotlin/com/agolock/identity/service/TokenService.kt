package com.agolock.identity.service

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Component
class TokenService {
    private val random = SecureRandom()

    fun createToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun createInviteCode(): String {
        val body = CharArray(INVITE_CODE_LENGTH) { INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)] }
        return "AGO-" + body.concatToString().chunked(INVITE_GROUP_SIZE).joinToString("-")
    }

    fun normalizeInviteCode(raw: String): String =
        raw
            .trim()
            .uppercase(java.util.Locale.ROOT)
            .replace("-", "")
            .replace(" ", "")

    fun hashInviteCode(raw: String): String = hashToken(normalizeInviteCode(raw))

    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    private companion object {
        const val TOKEN_BYTES = 32
        const val INVITE_CODE_LENGTH = 20
        const val INVITE_GROUP_SIZE = 4
        const val INVITE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }
}
