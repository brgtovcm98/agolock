package com.agolock.inventory.service

import com.agolock.common.cursor.TimeCursorCodec
import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.domain.PageResult
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.LocationItemResponse
import com.agolock.inventory.persistence.InventoryContextQueryMapper
import com.agolock.inventory.persistence.InventoryMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class InventoryMoveTargetService(
    private val inventoryMapper: InventoryMapper,
    private val mapper: InventoryContextQueryMapper,
    private val accessService: AccessService,
    private val support: InventoryAccessSupport,
    private val items: ItemQueryService,
) {
    fun moveTargets(
        actor: Actor,
        inventoryId: UUID,
        q: String? = null,
        limit: Int? = null,
        cursorRaw: String? = null,
    ): PageResult<LocationItemResponse> {
        val inventory =
            inventoryMapper.findByIdAny(inventoryId)
                ?: throw ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Inventory를 찾을 수 없습니다.")
        accessService.require(actor, support.inventoryTarget(inventory), GrantLevel.MANAGE)
        return targets(actor, inventory.spaceId, "move:$inventoryId", TargetPageQuery(q, limit, cursorRaw))
    }

    fun inventoryTargets(
        actor: Actor,
        itemId: UUID,
        q: String?,
        limit: Int?,
        cursorRaw: String?,
    ): PageResult<LocationItemResponse> =
        targets(actor, items.getReadable(actor, itemId).spaceId, "item:$itemId", TargetPageQuery(q, limit, cursorRaw))

    private fun targets(
        actor: Actor,
        spaceId: UUID,
        resource: String,
        options: TargetPageQuery,
    ): PageResult<LocationItemResponse> {
        val query = InventoryQueryText.normalize(options.q)
        val context = "targets:${actor.userId}:$spaceId:$resource:$query"
        val cursor = ContextCursor.decode(options.cursorRaw, context, 2)
        val size = TimeCursorCodec.limit(options.limit)
        val rows =
            mapper.targets(
                actor.spaceId,
                actor.userId,
                spaceId,
                query,
                cursor?.get(0)?.let(ContextCursor::number),
                cursor?.get(1)?.let(ContextCursor::uuid),
                size + 1,
            )
        val page = rows.take(size)
        return PageResult(
            page.map { LocationItemResponse(it.type, it.id, it.path) },
            if (rows.size >
                size
            ) {
                page.last().let { ContextCursor.encode(context, listOf(it.typeRank.toString(), it.id.toString())) }
            } else {
                null
            },
        )
    }

    private data class TargetPageQuery(
        val q: String?,
        val limit: Int?,
        val cursorRaw: String?,
    )
}
