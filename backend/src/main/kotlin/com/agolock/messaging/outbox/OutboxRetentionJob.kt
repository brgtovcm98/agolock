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
    @Value("\${agolock.outbox.retention-days:30}") private val retentionDays: Long,
) {
    @Scheduled(fixedDelayString = "\${agolock.outbox.retention-interval-ms:3600000}")
    fun cleanup() {
        mapper.deleteTerminalBefore(OffsetDateTime.now(clock).minusDays(retentionDays))
    }
}
