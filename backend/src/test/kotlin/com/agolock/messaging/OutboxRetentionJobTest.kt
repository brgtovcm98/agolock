package com.agolock.messaging

import com.agolock.messaging.outbox.OutboxEventMapper
import com.agolock.messaging.outbox.OutboxRetentionJob
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OutboxRetentionJobTest {
    @Test
    fun `PUBLISHED와 FAILED를 서로 다른 보관 기간으로 삭제한다`() {
        val now = OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC)
        val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC)
        val mapper = mock(OutboxEventMapper::class.java)
        val job = OutboxRetentionJob(mapper, clock, publishedDays = 30, failedDays = 90)

        job.cleanup()

        verify(mapper).reapExhausted(now, 5)
        verify(mapper).deletePublishedBefore(now.minusDays(30))
        verify(mapper).deleteFailedBefore(now.minusDays(90))
    }
}
