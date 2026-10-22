package com.agolock.search

import com.agolock.hierarchy.domain.Actor
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import com.agolock.search.persistence.RecentSearchMapper
import com.agolock.search.persistence.SearchMapper
import com.agolock.search.persistence.SearchPageRow
import com.agolock.search.persistence.SearchRow
import com.agolock.search.persistence.UserItemViewMapper
import com.agolock.search.service.SearchAccessScope
import com.agolock.search.service.SearchAccessService
import com.agolock.search.service.SearchRequest
import com.agolock.search.service.SearchService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class SearchServiceHydrateTest {
    @Test
    fun `hydrate 상한에서 빠진 그룹은 해당 Item만 보충 조회한다`() {
        val spaceId = UUID.randomUUID()
        val actor = Actor(UUID.randomUUID(), spaceId)
        val firstItemId = UUID.randomUUID()
        val secondItemId = UUID.randomUUID()
        val now = OffsetDateTime.of(2026, 10, 23, 0, 0, 0, 0, ZoneOffset.UTC)
        val pageRows =
            listOf(
                SearchPageRow(firstItemId, now, 1),
                SearchPageRow(secondItemId, now.minusMinutes(1), 1),
            )
        val searchCalls = AtomicInteger()
        val mapper =
            mock(SearchMapper::class.java) { invocation: InvocationOnMock ->
                when (invocation.method.name) {
                    "searchPageUpdated" -> pageRows
                    "search" ->
                        if (searchCalls.getAndIncrement() == 0) {
                            listOf(searchRow(firstItemId, spaceId, now))
                        } else {
                            listOf(searchRow(secondItemId, spaceId, now.minusMinutes(1)))
                        }
                    else -> null
                }
            }
        val access = mock(SearchAccessService::class.java)
        `when`(access.resolve(actor)).thenReturn(
            SearchAccessScope(spaceId, emptySet(), emptySet(), emptySet(), emptySet()),
        )
        val service =
            SearchService(
                mapper,
                mock(RecentSearchMapper::class.java),
                mock(UserItemViewMapper::class.java),
                mock(ItemMapper::class.java),
                mock(InventoryMapper::class.java),
                access,
                Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC),
            )

        val response = service.search(actor, SearchRequest(sort = "UPDATED_DESC", limit = 2))

        assertThat(response.items.map { it.itemId }).containsExactly(firstItemId, secondItemId)
    }

    private fun searchRow(
        itemId: UUID,
        spaceId: UUID,
        updatedAt: OffsetDateTime,
    ): SearchRow =
        SearchRow(
            inventoryId = UUID.randomUUID(),
            itemId = itemId,
            spaceId = spaceId,
            itemName = "물건",
            effectiveName = "물건",
            effectiveDescription = null,
            categoryId = null,
            categoryName = null,
            categoryColor = null,
            status = InventoryStatus.STORED,
            roomName = "방",
            shelfName = null,
            boxName = null,
            primaryImageId = null,
            ownerName = null,
            updatedAt = updatedAt,
        )
}
