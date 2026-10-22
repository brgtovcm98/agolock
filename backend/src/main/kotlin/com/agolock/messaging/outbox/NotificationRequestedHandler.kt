package com.agolock.messaging.outbox

import com.agolock.identity.mail.MailPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Component
class NotificationRequestedHandler(
    private val objectMapper: ObjectMapper,
    private val mailPort: MailPort,
    private val notificationSecretService: NotificationSecretService,
    private val clock: Clock,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == "notification.requested"

    override fun handle(event: ClaimedOutboxEvent) {
        val payload = objectMapper.readTree(event.payload)
        val email = payload["recipientEmail"]?.asText() ?: throw IllegalArgumentException("recipientEmail 누락")
        val secretId = payload["secretId"]?.asText()?.let(UUID::fromString)
        val token =
            if (secretId != null) {
                notificationSecretService.resolve(secretId)
                    ?: throw IllegalArgumentException("notification secret을 복호화할 수 없습니다.")
            } else {
                // V22 이전에 생성된 outbox 이벤트는 평문 token을 가진다. 배포 중에만 허용한다.
                payload["token"]?.asText() ?: throw IllegalArgumentException("token 누락")
            }
        val resourceType = payload["resourceType"]?.asText() ?: "RESOURCE"
        val expiresAt =
            payload["expiresAt"]?.asText()?.let(OffsetDateTime::parse)
                ?: OffsetDateTime.now(clock).plusDays(DEFAULT_TTL_DAYS)
        mailPort.sendShareInvitation(email, resourceType, token, expiresAt)
    }

    private companion object {
        const val DEFAULT_TTL_DAYS = 7L
    }
}
