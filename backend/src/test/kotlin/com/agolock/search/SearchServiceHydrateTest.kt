package com.agolock.search

import com.agolock.hierarchy.domain.Actor
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import com.agolock.search.domain.SearchAccessScope
import com.agolock.search.persistence.RecentSearchMapper
import com.agolock.search.persistence.SearchMapper
import com.agolock.search.persistence.SearchRow
import com.agolock.search.persistence.UserItemViewMapper
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
    fun `그룹 페이지는 한 번 조회하고 모든 그룹과 정확한 개수를 보존한다`() {
        val spaceId = UUID.randomUUID()
        val actor = Actor(UUID.randomUUID(), spaceId)
        val firstItemId = UUID.randomUUID()
        val secondItemId = UUID.randomUUID()
        val now = OffsetDateTime.of(2026, 10, 23, 0, 0, 0, 0, ZoneOffset.UTC)
        val searchCalls = AtomicInteger()
        val mapper =
            mock(SearchMapper::class.java) { invocation: InvocationOnMock ->
                when (invocation.method.name) {
                    "search" -> {
                        searchCalls.incrementAndGet()
                        listOf(searchRow(firstItemId, spaceId, now, 601), searchRow(secondItemId, spaceId, now, 1))
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
        assertThat(response.items.first().inventoryCount).isEqualTo(601)
        assertThat(response.items.first().hasMoreInventories).isTrue()
        assertThat(searchCalls.get()).isEqualTo(1)
    }

    private fun searchRow(
        itemId: UUID,
        spaceId: UUID,
        updatedAt: OffsetDateTime,
        inventoryCount: Int,
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
            ownerName = null,
            updatedAt = updatedAt,
            inventoryCount = inventoryCount,
            sortKey = updatedAt.toString(),
            groupUpdatedAt = updatedAt,
            groupPrimaryImageId = null,
        )
}
