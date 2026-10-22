package com.agolock.messaging.outbox

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime

@Service
@Profile("worker")
class OutboxRetentionJob(
    private val mapper: OutboxEventMapper,
    private val clock: Clock,
    @Value("\${agolock.outbox.retention.published-days:30}") private val publishedDays: Long,
    @Value("\${agolock.outbox.retention.failed-days:90}") private val failedDays: Long,
) {
    @Scheduled(fixedDelayString = "\${agolock.outbox.retention-interval-ms:3600000}")
    fun cleanup() {
        val now = OffsetDateTime.now(clock)
        mapper.reapExhausted(now, OutboxRelayService.MAX_ATTEMPTS)
        mapper.deletePublishedBefore(now.minusDays(publishedDays))
        mapper.deleteFailedBefore(now.minusDays(failedDays))
    }
}
