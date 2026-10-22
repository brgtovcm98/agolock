package com.agolock.search.api

import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.search.service.SearchRequest
import com.agolock.search.service.SearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "search", description = "검색과 추천")
class SearchController(
    private val searchService: SearchService,
) {
    @GetMapping("/search")
    @Operation(operationId = "search", summary = "재고 검색")
    @Suppress("LongParameterList")
    fun search(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: List<InventoryStatus>?,
        @RequestParam(required = false) roomId: UUID?,
        @RequestParam(required = false) shelfId: UUID?,
        @RequestParam(required = false) boxId: UUID?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(defaultValue = "ALL") scope: String,
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false) hasImage: Boolean?,
        @RequestParam(defaultValue = "RELEVANCE") sort: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): SearchResponse =
        searchService.search(
            Actor.from(principal),
            SearchRequest(
                q = q,
                statuses = status,
                roomId = roomId,
                shelfId = shelfId,
                boxId = boxId,
                categoryId = categoryId,
                scope = scope,
                ownerId = ownerId,
                hasImage = hasImage,
                sort = sort,
                limit = limit,
                cursor = cursor,
            ),
        )

    @GetMapping("/search/recent")
    @Operation(operationId = "listRecentSearches", summary = "최근 검색어")
    fun recent(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): RecentSearchList = searchService.recent(Actor.from(principal))

    @DeleteMapping("/search/recent")
    @Operation(operationId = "clearRecentSearches", summary = "최근 검색어 전체 삭제")
    fun clearRecent(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): ResponseEntity<Void> {
        searchService.clearRecent(Actor.from(principal))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/search/frequent")
    @Operation(operationId = "listFrequentItems", summary = "자주 본 물건")
    fun frequent(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): FrequentItemList = searchService.frequent(Actor.from(principal))

    @GetMapping("/search/suggestions")
    @Operation(operationId = "listSearchSuggestions", summary = "검색 추천")
    fun suggestions(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): SearchSuggestions = searchService.suggestions(Actor.from(principal))

    @PostMapping("/items/{itemId}/view")
    @Operation(operationId = "recordItemView", summary = "물건 조회 기록")
    fun recordView(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable itemId: UUID,
    ): ResponseEntity<Void> {
        searchService.recordView(Actor.from(principal), itemId)
        return ResponseEntity.noContent().build()
    }
}
