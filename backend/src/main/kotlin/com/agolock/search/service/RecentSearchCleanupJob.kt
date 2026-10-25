package com.agolock.search.service

import com.agolock.search.domain.RecentSearchRetention
import com.agolock.search.persistence.RecentSearchMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime

@Service
@Profile("worker")
class RecentSearchCleanupJob(
    private val mapper: RecentSearchMapper,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${agolock.search.recent-cleanup-interval-ms:3600000}")
    fun cleanup() {
        val deleted = mapper.deleteBefore(OffsetDateTime.now(clock).minusDays(RecentSearchRetention.DAYS))
        if (deleted > 0) log.info("최근 검색 만료 정리 deleted={}", deleted)
    }
}
