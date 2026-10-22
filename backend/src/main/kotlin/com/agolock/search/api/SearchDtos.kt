@file:Suppress("MaxLineLength")

package com.agolock.search.api

import com.agolock.inventory.domain.InventoryStatus
import java.time.OffsetDateTime
import java.util.UUID

data class SearchResponse(
    val items: List<SearchItemGroup>,
    val nextCursor: String?,
)

data class SearchItemGroup(
    val itemId: UUID,
    val itemName: String,
    val matchedInventoryCount: Int,
    val inventoryCount: Int,
    val hasMoreInventories: Boolean,
    val primaryImageId: UUID?,
    val effectiveCategory: SearchCategory?,
    val inventories: List<SearchInventory>,
)

data class SearchCategory(
    val id: UUID,
    val name: String,
    val color: String?,
)

data class SearchInventory(
    val inventoryId: UUID,
    val effectiveName: String,
    val status: InventoryStatus,
    val locationPath: String,
    val shared: Boolean,
    val ownerName: String?,
)

data class RecentSearchResponse(
    val id: UUID,
    val query: String,
    val lastSearchedAt: OffsetDateTime,
)

data class RecentSearchList(
    val items: List<RecentSearchResponse>,
)

data class FrequentItemResponse(
    val itemId: UUID,
    val itemName: String,
    val primaryImageId: UUID?,
    val viewCount: Long,
    val lastViewedAt: OffsetDateTime,
)

data class FrequentItemList(
    val items: List<FrequentItemResponse>,
)

data class SearchSuggestions(
    val recentSearches: RecentSearchList,
    val frequentItems: FrequentItemList,
    val recentlyAddedInventories: List<SearchInventory> = emptyList(),
    val recentlySharedResources: List<Any> = emptyList(),
)
