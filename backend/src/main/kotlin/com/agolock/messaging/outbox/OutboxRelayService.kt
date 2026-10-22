@file:Suppress("TooGenericExceptionCaught", "MaxLineLength", "MagicNumber", "SwallowedException", "ThrowsCount")

package com.agolock.messaging.outbox

import com.agolock.common.id.UuidV7
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class OutboxRelayService(
    private val mapper: OutboxEventMapper,
    private val handlers: List<OutboxEventHandler>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val instanceId = UUID.randomUUID().toString()

    @Scheduled(fixedDelayString = "\${agolock.outbox.poll-interval-ms:5000}")
    fun relay() {
        repeat(MAX_BATCHES_PER_RUN) {
            val now = OffsetDateTime.now(clock)
            val events =
                mapper.claimBatch(
                    now = now,
                    lockedUntil = now.plusMinutes(LEASE_MINUTES),
                    instanceId = instanceId,
                    claimToken = UuidV7.generate(clock),
                    maxAttempts = MAX_ATTEMPTS,
                    limit = BATCH_SIZE,
                )
            if (events.isEmpty()) return
            events.forEach { process(it, now) }
        }
    }

    fun relayOnce(): Boolean {
        val now = OffsetDateTime.now(clock)
        val event =
            mapper
                .claimBatch(
                    now = now,
                    lockedUntil = now.plusMinutes(LEASE_MINUTES),
                    instanceId = instanceId,
                    claimToken = UuidV7.generate(clock),
                    maxAttempts = MAX_ATTEMPTS,
                    limit = 1,
                ).firstOrNull()
                ?: return false
        process(event, now)
        return true
    }

    private fun process(
        event: ClaimedOutboxEvent,
        now: OffsetDateTime,
    ) {
        try {
            val handler =
                handlers.firstOrNull { it.supports(event.eventType) }
                    ?: throw UnsupportedEventTypeException(event.eventType)
            handler.handle(event)
            if (mapper.markPublished(event.id, event.claimToken, now) == 0) {
                log.warn("Outbox claim 상실 id={} type={}", event.id, event.eventType)
            }
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
    }

    private class UnsupportedEventTypeException(
        eventType: String,
    ) : RuntimeException(eventType)

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val LEASE_MINUTES = 2L
        private const val BASE_BACKOFF_SECONDS = 30
        private const val MAX_BACKOFF_SECONDS = 1800
        private const val MAX_BATCHES_PER_RUN = 10
        private const val BATCH_SIZE = 20
    }
}
