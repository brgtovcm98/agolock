package com.agolock.search

import com.agolock.search.persistence.RecentSearchMapper
import com.agolock.search.service.RecentSearchCleanupJob
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RecentSearchCleanupJobTest {
    @Test
    fun `30일이 지난 최근 검색만 삭제한다`() {
        val now = OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC)
        val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC)
        val mapper = mock(RecentSearchMapper::class.java)
        val job = RecentSearchCleanupJob(mapper, clock)

        job.cleanup()

        verify(mapper).deleteBefore(now.minusDays(30))
    }
}
