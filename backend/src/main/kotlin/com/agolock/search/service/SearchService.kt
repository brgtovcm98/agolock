@file:Suppress("LongParameterList", "TooManyFunctions", "MagicNumber", "ThrowsCount", "MaxLineLength")

package com.agolock.search.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import com.agolock.search.api.FrequentItemList
import com.agolock.search.api.FrequentItemResponse
import com.agolock.search.api.RecentSearchList
import com.agolock.search.api.RecentSearchResponse
import com.agolock.search.api.SearchCategory
import com.agolock.search.api.SearchInventory
import com.agolock.search.api.SearchItemGroup
import com.agolock.search.api.SearchResponse
import com.agolock.search.api.SearchSuggestions
import com.agolock.search.persistence.RecentSearchMapper
import com.agolock.search.persistence.SearchMapper
import com.agolock.search.persistence.SearchQuery
import com.agolock.search.persistence.SearchRow
import com.agolock.search.persistence.UserItemViewMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

data class SearchRequest(
    val q: String? = null,
    val statuses: List<InventoryStatus>? = null,
    val roomId: UUID? = null,
    val shelfId: UUID? = null,
    val boxId: UUID? = null,
    val categoryId: UUID? = null,
    val scope: String = "ALL",
    val ownerId: UUID? = null,
    val hasImage: Boolean? = null,
    val sort: String = "RELEVANCE",
    val limit: Int = 20,
    val cursor: String? = null,
)

@Service
class SearchService(
    private val searchMapper: SearchMapper,
    private val recentSearchMapper: RecentSearchMapper,
    private val userItemViewMapper: UserItemViewMapper,
    private val itemMapper: ItemMapper,
    private val inventoryMapper: InventoryMapper,
    private val accessService: SearchAccessService,
    private val clock: Clock,
) {
    @Transactional
    fun search(
        actor: Actor,
        request: SearchRequest,
    ): SearchResponse {
        validate(request)
        val access = accessService.resolve(actor)
        val scope = request.scope.uppercase()
        val includeOwned = scope == "ALL" || scope == "OWNED"
        val includeShared = scope == "ALL" || scope == "SHARED"
        val offset = SearchCursorCodec.decode(request.cursor)
        val limit = request.limit.coerceIn(1, 50)
        val statuses = request.statuses?.takeIf { it.isNotEmpty() } ?: listOf(InventoryStatus.IN_USE, InventoryStatus.STORED)
        val q = normalizeQuery(request.q).takeIf { it.isNotBlank() }

        val rows =
            searchMapper.search(
                SearchQuery(
                    ownSpaceId = access.ownSpaceId,
                    actorUserId = actor.userId,
                    includeOwned = includeOwned,
                    includeShared = includeShared,
                    sharedSpaceIds = access.sharedSpaceIds.toList(),
                    roomIds = access.roomIds.toList(),
                    shelfIds = access.shelfIds.toList(),
                    boxIds = access.boxIds.toList(),
                    q = q,
                    statuses = statuses,
                    roomFilterId = request.roomId,
                    shelfFilterId = request.shelfId,
                    boxFilterId = request.boxId,
                    categoryId = request.categoryId,
                    ownerId = request.ownerId,
                    hasImage = request.hasImage,
                    sort = request.sort.uppercase(),
                    limit = minOf(500, (offset + limit) * 10),
                ),
            )

        val groups = groupRows(rows, access.ownSpaceId)
        val page = groups.drop(offset).take(limit)
        val nextCursor = if (groups.size > offset + limit) SearchCursorCodec.encode(offset + page.size) else null

        if (q != null && request.cursor.isNullOrBlank()) {
            recentSearchMapper.upsert(
                UuidV7.generate(clock),
                actor.userId,
                request.q!!.trim(),
                q,
                OffsetDateTime.now(clock),
            )
        }
        return SearchResponse(page, nextCursor)
    }

    fun recent(actor: Actor): RecentSearchList =
        RecentSearchList(
            recentSearchMapper.list(actor.userId).map {
                RecentSearchResponse(it.id, it.query, it.lastSearchedAt)
            },
        )

    @Transactional
    fun clearRecent(actor: Actor) {
        recentSearchMapper.clear(actor.userId)
    }

    fun frequent(actor: Actor): FrequentItemList {
        val since = OffsetDateTime.now(clock).minusDays(90)
        return FrequentItemList(
            userItemViewMapper.list(actor.userId, since).map {
                FrequentItemResponse(it.itemId, it.itemName, it.primaryImageId, it.viewCount, it.lastViewedAt)
            },
        )
    }

    fun suggestions(actor: Actor): SearchSuggestions =
        SearchSuggestions(
            recentSearches = recent(actor),
            frequentItems = frequent(actor),
        )

    @Transactional
    fun recordView(
        actor: Actor,
        itemId: UUID,
    ) {
        val item = itemMapper.findByIdAny(itemId) ?: throw notFound()
        val access = accessService.resolve(actor)
        val accessible =
            item.spaceId == actor.spaceId ||
                inventoryMapper.listAllByItem(item.spaceId, item.id).any {
                    it.roomId in access.roomIds || it.shelfId in access.shelfIds || it.boxId in access.boxIds
                }
        if (!accessible) throw notFound()
        val now = OffsetDateTime.now(clock)
        userItemViewMapper.record(actor.userId, itemId, item.spaceId, now, now.minusMinutes(30))
    }

    private fun groupRows(
        rows: List<SearchRow>,
        ownSpaceId: UUID,
    ): List<SearchItemGroup> {
        val grouped = linkedMapOf<UUID, MutableList<SearchRow>>()
        rows.forEach { grouped.getOrPut(it.itemId) { mutableListOf() }.add(it) }
        return grouped.map { (itemId, inventories) ->
            val first = inventories.first()
            SearchItemGroup(
                itemId = itemId,
                itemName = first.itemName,
                matchedInventoryCount = inventories.size,
                inventoryCount = inventories.size,
                hasMoreInventories = inventories.size > 5,
                primaryImageId = inventories.firstNotNullOfOrNull { it.primaryImageId },
                effectiveCategory =
                    first.categoryId?.let { SearchCategory(it, first.categoryName ?: "", first.categoryColor) },
                inventories =
                    inventories.take(5).map {
                        SearchInventory(
                            inventoryId = it.inventoryId,
                            effectiveName = it.effectiveName,
                            status = it.status,
                            locationPath = listOfNotNull(it.roomName, it.shelfName, it.boxName).joinToString(" > "),
                            shared = it.spaceId != ownSpaceId,
                            ownerName = if (it.spaceId != ownSpaceId) it.ownerName else null,
                        )
                    },
            )
        }
    }

    private fun validate(request: SearchRequest) {
        if (listOfNotNull(request.roomId, request.shelfId, request.boxId).size > 1) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "위치 필터는 하나만 지정할 수 있습니다.")
        }
        if (request.scope.uppercase() !in setOf("ALL", "OWNED", "SHARED")) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "scope가 올바르지 않습니다.")
        }
        if (request.sort.uppercase() !in SORTS) {
            throw ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "sort가 올바르지 않습니다.")
        }
    }

    private fun normalizeQuery(value: String?): String =
        Normalizer
            .normalize(value ?: "", Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()

    private fun notFound() = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "물건을 찾을 수 없습니다.")

    private companion object {
        val SORTS = setOf("RELEVANCE", "UPDATED_DESC", "NAME_ASC", "VIEWED_DESC", "STATUS_ASC")
    }
}
