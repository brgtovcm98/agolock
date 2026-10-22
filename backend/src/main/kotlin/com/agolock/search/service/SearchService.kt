@file:Suppress("LongParameterList", "TooManyFunctions", "MagicNumber", "ThrowsCount", "MaxLineLength", "LongMethod")

package com.agolock.search.service

import com.agolock.common.cursor.TimeCursorCodec
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
        val limit = request.limit.coerceIn(1, 50)
        val statuses = request.statuses?.takeIf { it.isNotEmpty() } ?: listOf(InventoryStatus.IN_USE, InventoryStatus.STORED)
        val q = normalizeQuery(request.q).takeIf { it.isNotBlank() }
        val sort = request.sort.uppercase()
        // q가 없으면 relevance 정렬은 의미가 없고, ORDER BY의 null 바인딩이 PostgreSQL 42P18을 만든다.
        val effectiveSort =
            if (q == null && sort == "RELEVANCE") {
                "UPDATED_DESC"
            } else {
                sort
            }
        val query =
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
                sort = effectiveSort,
                limit = limit,
            )

        val response =
            when (effectiveSort) {
                "UPDATED_DESC" -> searchUpdated(query, request.cursor, access.ownSpaceId, limit)
                "RELEVANCE" -> {
                    // RELEVANCE는 안정적인 keyset 정렬 키가 없으므로 첫 페이지만 허용합니다.
                    if (!request.cursor.isNullOrBlank()) {
                        throw ApiException(
                            "CURSOR_NOT_SUPPORTED_FOR_SORT",
                            HttpStatus.BAD_REQUEST,
                            "RELEVANCE 정렬은 첫 페이지만 지원합니다.",
                        )
                    }
                    searchOffset(query, null, access.ownSpaceId, limit).copy(nextCursor = null)
                }
                else -> searchOffset(query, request.cursor, access.ownSpaceId, limit)
            }

        if (q != null && request.cursor.isNullOrBlank()) {
            recentSearchMapper.upsert(
                UuidV7.generate(clock),
                actor.userId,
                request.q!!.trim(),
                q,
                OffsetDateTime.now(clock),
            )
        }
        return response
    }

    /**
     * `UPDATED_DESC`는 Item 그룹의 `max(inventory.updated_at)`과 `item_id`로 keyset 페이지를 만듭니다.
     * 그룹 Inventory는 기존 search SQL로 한 번에 재조회하고, 페이지 순서와 정확한 집계 수를 덮어씁니다.
     */
    private fun searchUpdated(
        query: SearchQuery,
        rawCursor: String?,
        ownSpaceId: UUID,
        limit: Int,
    ): SearchResponse {
        val cursor = TimeCursorCodec.decode(rawCursor)
        val rows = searchMapper.searchPageUpdated(query.copy(limit = limit + 1), cursor?.updatedAt, cursor?.id)
        val pageRows = rows.take(limit)
        if (pageRows.isEmpty()) return SearchResponse(emptyList(), null)

        val hydrateQuery =
            query.copy(
                q = null,
                roomFilterId = null,
                shelfFilterId = null,
                boxFilterId = null,
                categoryId = null,
                ownerId = null,
                hasImage = null,
            )
        val hydrateRows =
            searchMapper.search(
                hydrateQuery.copy(
                    itemIds = pageRows.map { it.itemId },
                    limit = HYDRATE_LIMIT,
                ),
            )
        val groups = groupRows(hydrateRows, ownSpaceId).toMutableList()
        // 500행 상한에 밀려 빠진 그룹은 해당 Item만 5행으로 보충해 페이지에서 조용히 사라지지 않게 한다.
        pageRows
            .filter { row -> groups.none { it.itemId == row.itemId } }
            .forEach { row ->
                searchMapper
                    .search(
                        hydrateQuery.copy(
                            itemIds = listOf(row.itemId),
                            limit = MAX_INVENTORIES_PER_GROUP,
                        ),
                    ).let { groupRows(it, ownSpaceId).firstOrNull() }
                    ?.let(groups::add)
            }
        val page =
            pageRows.mapNotNull { row ->
                groups.firstOrNull { it.itemId == row.itemId }?.copy(
                    inventoryCount = row.inventoryCount,
                    matchedInventoryCount = row.inventoryCount,
                    hasMoreInventories = row.inventoryCount > MAX_INVENTORIES_PER_GROUP,
                )
            }
        // limit+1번째 행은 다음 페이지 존재 여부만 판단하고, 커서는 이번 페이지의 마지막 행을 가리킵니다.
        val nextCursor =
            if (rows.size > limit) {
                pageRows.lastOrNull()?.let { TimeCursorCodec.encode(it.updatedAt, it.itemId) }
            } else {
                null
            }
        return SearchResponse(page, nextCursor)
    }

    private fun searchOffset(
        query: SearchQuery,
        rawCursor: String?,
        ownSpaceId: UUID,
        limit: Int,
    ): SearchResponse {
        val offset = SearchCursorCodec.decode(rawCursor)
        val rows = searchMapper.search(query.copy(limit = minOf(HYDRATE_LIMIT, (offset + limit) * 10)))
        val groups = groupRows(rows, ownSpaceId)
        val page = groups.drop(offset).take(limit)
        val nextCursor = if (groups.size > offset + limit) SearchCursorCodec.encode(offset + page.size) else null
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
            userItemViewMapper.list(actor.userId, actor.spaceId, since).map {
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
        const val HYDRATE_LIMIT = 500
        const val MAX_INVENTORIES_PER_GROUP = 5
        val SORTS = setOf("RELEVANCE", "UPDATED_DESC", "NAME_ASC", "VIEWED_DESC", "STATUS_ASC")
    }
}
