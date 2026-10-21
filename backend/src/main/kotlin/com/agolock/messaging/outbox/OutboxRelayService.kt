@file:Suppress("TooGenericExceptionCaught", "ReturnCount", "MaxLineLength", "MagicNumber")

package com.agolock.messaging.outbox

import com.agolock.identity.mail.MailPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class OutboxRelayService(
    private val mapper: OutboxEventMapper,
    private val objectMapper: ObjectMapper,
    private val mailPort: MailPort,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${agolock.outbox.poll-interval-ms:5000}")
    @Transactional
    fun relay() {
        val now = OffsetDateTime.now(clock)
        mapper.findPending(now, 20).forEach { event ->
            try {
                deliver(event)
                mapper.markPublished(event.id, now)
            } catch (exception: Exception) {
                val retryAt = now.plusSeconds((event.attempts + 1).toLong() * 30L)
                mapper.markFailed(event.id, exception.javaClass.simpleName, retryAt)
                log.warn("Outbox 발송 실패 id={} type={} reason={}", event.id, event.eventType, exception.javaClass.simpleName)
            }
        }
    }

    private fun deliver(event: OutboxEvent) {
        if (event.eventType != "notification.requested") return
        val payload = objectMapper.readTree(event.payload)
        val email = payload["recipientEmail"]?.asText() ?: return
        val token = payload["token"]?.asText() ?: return
        val resourceType = payload["resourceType"]?.asText() ?: "RESOURCE"
        val expiresAt = payload["expiresAt"]?.asText()?.let(OffsetDateTime::parse) ?: OffsetDateTime.now(clock).plusDays(7)
        mailPort.sendShareInvitation(email, resourceType, token, expiresAt)
    }
}
