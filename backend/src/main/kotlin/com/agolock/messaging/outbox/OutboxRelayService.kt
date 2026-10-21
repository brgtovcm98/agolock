@file:Suppress("TooGenericExceptionCaught", "MaxLineLength", "MagicNumber", "SwallowedException", "ThrowsCount")

package com.agolock.messaging.outbox

import com.agolock.common.id.UuidV7
import com.agolock.identity.mail.MailPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OutboxRelayService(
    private val mapper: OutboxEventMapper,
    private val objectMapper: ObjectMapper,
    private val mailPort: MailPort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val instanceId = UUID.randomUUID().toString()

    @Scheduled(fixedDelayString = "\${agolock.outbox.poll-interval-ms:5000}")
    fun relay() {
        repeat(MAX_BATCHES_PER_RUN) {
            if (!relayOnce()) return
        }
    }

    fun relayOnce(): Boolean {
        val now = OffsetDateTime.now(clock)
        val event =
            mapper.claim(
                now = now,
                lockedUntil = now.plusMinutes(LEASE_MINUTES),
                instanceId = instanceId,
                claimToken = UuidV7.generate(clock),
                maxAttempts = MAX_ATTEMPTS,
            ) ?: return false
        try {
            deliver(event)
            mapper.markPublished(event.id, event.claimToken, now)
        } catch (exception: UnsupportedEventTypeException) {
            mapper.markTerminalFailure(event.id, event.claimToken, "UNSUPPORTED_EVENT_TYPE")
            log.warn("Outbox 미지원 이벤트 id={} type={}", event.id, event.eventType)
        } catch (exception: Exception) {
            if (event.attempts >= MAX_ATTEMPTS) {
                mapper.markTerminalFailure(event.id, event.claimToken, exception.javaClass.simpleName)
            } else {
                val backoffSeconds = (BASE_BACKOFF_SECONDS shl (event.attempts - 1)).coerceAtMost(MAX_BACKOFF_SECONDS)
                mapper.markRetryableFailure(
                    event.id,
                    event.claimToken,
                    exception.javaClass.simpleName,
                    now.plusSeconds(backoffSeconds.toLong()),
                )
            }
            log.warn("Outbox 발송 실패 id={} type={} reason={}", event.id, event.eventType, exception.javaClass.simpleName)
        }
        return true
    }

    private fun deliver(event: ClaimedOutboxEvent) {
        if (event.eventType != "notification.requested") throw UnsupportedEventTypeException(event.eventType)
        val payload = objectMapper.readTree(event.payload)
        val email = payload["recipientEmail"]?.asText() ?: throw IllegalArgumentException("recipientEmail 누락")
        val token = payload["token"]?.asText() ?: throw IllegalArgumentException("token 누락")
        val resourceType = payload["resourceType"]?.asText() ?: "RESOURCE"
        val expiresAt = payload["expiresAt"]?.asText()?.let(OffsetDateTime::parse) ?: OffsetDateTime.now(clock).plusDays(7)
        mailPort.sendShareInvitation(email, resourceType, token, expiresAt)
    }

    private class UnsupportedEventTypeException(
        eventType: String,
    ) : RuntimeException(eventType)

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val LEASE_MINUTES = 2L
        const val BASE_BACKOFF_SECONDS = 30
        const val MAX_BACKOFF_SECONDS = 1800
        const val MAX_BATCHES_PER_RUN = 10
    }
}
