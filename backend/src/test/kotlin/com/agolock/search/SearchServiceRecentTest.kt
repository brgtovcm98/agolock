package com.agolock.search

import com.agolock.hierarchy.domain.Actor
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import com.agolock.search.api.RecentSearchResponse
import com.agolock.search.domain.RecentSearchRetention
import com.agolock.search.persistence.RecentSearchMapper
import com.agolock.search.persistence.RecentSearchRow
import com.agolock.search.persistence.SearchMapper
import com.agolock.search.persistence.UserItemViewMapper
import com.agolock.search.service.SearchAccessService
import com.agolock.search.service.SearchService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SearchServiceRecentTest {
    private val now = OffsetDateTime.of(2026, 10, 25, 12, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `최근 검색은 30일 경계를 포함하는 cutoff로 조회한다`() {
        val userId = UUID.randomUUID()
        val row = RecentSearchRow(UUID.randomUUID(), "이어폰", "이어폰", now.minusDays(1))
        val mapper = mock(RecentSearchMapper::class.java)
        `when`(mapper.list(userId, cutoff())).thenReturn(listOf(row))
        val service = service(mapper)

        val response = service.recent(Actor(userId, UUID.randomUUID()))

        verify(mapper).list(userId, cutoff())
        assertThat(response.items).containsExactly(RecentSearchResponse(row.id, row.query, row.lastSearchedAt))
    }

    @Test
    fun `검색 제안의 최근 검색도 같은 cutoff를 사용한다`() {
        val userId = UUID.randomUUID()
        val mapper = mock(RecentSearchMapper::class.java)
        `when`(mapper.list(userId, cutoff())).thenReturn(emptyList())
        val service = service(mapper)

        service.suggestions(Actor(userId, UUID.randomUUID()))

        verify(mapper).list(userId, cutoff())
    }

    private fun cutoff(): OffsetDateTime = now.minusDays(RecentSearchRetention.DAYS)

    private fun service(mapper: RecentSearchMapper): SearchService =
        SearchService(
            mock(SearchMapper::class.java),
            mapper,
            mock(UserItemViewMapper::class.java),
            mock(ItemMapper::class.java),
            mock(InventoryMapper::class.java),
            mock(SearchAccessService::class.java),
            Clock.fixed(now.toInstant(), ZoneOffset.UTC),
        )
}
