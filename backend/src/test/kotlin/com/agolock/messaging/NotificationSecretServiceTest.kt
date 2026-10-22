package com.agolock.messaging

import com.agolock.messaging.outbox.NotificationSecret
import com.agolock.messaging.outbox.NotificationSecretMapper
import com.agolock.messaging.outbox.NotificationSecretService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class NotificationSecretServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC)
    private val key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

    @Test
    fun `토큰을 암호화해 저장하고 secretId로만 복호화한다`() {
        val mapper = InMemorySecretMapper()
        val service = NotificationSecretService(mapper, clock, key)
        val expiresAt = OffsetDateTime.now(clock).plusHours(1)

        val id = service.create(UUID.randomUUID(), "SHARE_INVITATION", "raw-token-value", expiresAt)

        assertThat(service.resolve(id)).isEqualTo("raw-token-value")
        assertThat(mapper.findById(id)!!.ciphertext).isNotEqualTo("raw-token-value".toByteArray())
    }

    @Test
    fun `만료된 secret은 복호화하지 않는다`() {
        val mapper = InMemorySecretMapper()
        val service = NotificationSecretService(mapper, clock, key)
        val id =
            service.create(
                UUID.randomUUID(),
                "SHARE_INVITATION",
                "raw-token-value",
                OffsetDateTime.now(clock).minusSeconds(1),
            )

        assertThat(service.resolve(id)).isNull()
    }

    @Test
    fun `키 길이가 32바이트가 아니면 기동에 실패한다`() {
        assertThrows<IllegalArgumentException> {
            NotificationSecretService(InMemorySecretMapper(), clock, "c2hvcnQ=")
        }
    }

    private class InMemorySecretMapper : NotificationSecretMapper {
        private val rows = mutableMapOf<UUID, NotificationSecret>()

        override fun insert(
            id: UUID,
            spaceId: UUID,
            secretType: String,
            ciphertext: ByteArray,
            nonce: ByteArray,
            expiresAt: OffsetDateTime,
            createdAt: OffsetDateTime,
        ) {
            rows[id] = NotificationSecret(id, spaceId, secretType, ciphertext, nonce, expiresAt, createdAt)
        }

        override fun findById(id: UUID): NotificationSecret? = rows[id]

        override fun deleteExpired(before: OffsetDateTime): Int {
            val expired = rows.values.filter { it.expiresAt < before }.map { it.id }
            expired.forEach(rows::remove)
            return expired.size
        }
    }
}
