@file:Suppress("MagicNumber", "ReturnCount")

package com.agolock.messaging.outbox

import com.agolock.common.id.UuidV7
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 메일 링크 토큰을 AES-GCM으로 암호화해 outbox payload와 분리 저장합니다.
 *
 * outbox에는 secretId만 남기고, 실제 토큰은 이 테이블에서만 복호화합니다.
 * 키는 `NOTIFICATION_SECRET_KEY`(base64 32바이트)로 주입합니다.
 */
@Service
class NotificationSecretService(
    private val mapper: NotificationSecretMapper,
    private val clock: Clock,
    @Value("\${agolock.notification.secret-key:}") secretKeyBase64: String,
) {
    private val secretKey: SecretKeySpec
    private val random = SecureRandom()

    init {
        val keyBytes = Base64.getDecoder().decode(secretKeyBase64)
        require(keyBytes.size == 32) { "NOTIFICATION_SECRET_KEY는 base64 32바이트여야 합니다." }
        secretKey = SecretKeySpec(keyBytes, "AES")
    }

    @Transactional
    fun create(
        spaceId: UUID,
        secretType: String,
        rawToken: String,
        expiresAt: OffsetDateTime,
    ): UUID {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(rawToken.toByteArray(Charsets.UTF_8))
        val id = UuidV7.generate(clock)
        mapper.insert(
            id,
            spaceId,
            secretType,
            ciphertext,
            nonce,
            expiresAt,
            OffsetDateTime.now(clock),
        )
        return id
    }

    fun resolve(secretId: UUID): String? {
        val secret = mapper.findById(secretId) ?: return null
        if (!secret.expiresAt.isAfter(OffsetDateTime.now(clock))) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, secret.nonce))
        return String(cipher.doFinal(secret.ciphertext), Charsets.UTF_8)
    }

    @Transactional
    fun deleteExpired() {
        mapper.deleteExpired(OffsetDateTime.now(clock))
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}
