@file:Suppress("ThrowsCount", "MagicNumber", "ComplexCondition", "TooManyFunctions", "MaxLineLength", "LongParameterList")

package com.agolock.inventory.service

import com.agolock.common.cursor.TimeCursorCodec
import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.domain.PageResult
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.domain.Item
import com.agolock.inventory.persistence.InventoryContextQueryMapper
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ItemQueryService(
    private val itemMapper: ItemMapper,
    private val inventoryMapper: InventoryMapper,
    private val accessService: AccessService,
    private val contextMapper: InventoryContextQueryMapper,
    private val support: InventoryAccessSupport,
) {
    fun list(
        actor: Actor,
        categoryId: UUID?,
        q: String?,
        limit: Int?,
        cursorRaw: String?,
        scope: String = "ALL",
        roomId: UUID? = null,
        shelfId: UUID? = null,
        boxId: UUID? = null,
    ): PageResult<Item> {
        val normalizedScope = scope.uppercase()
        if (normalizedScope !in setOf("OWNED", "SHARED", "ALL")) throw validation("scope가 올바르지 않습니다.")
        val targetSpaceId =
            if (listOfNotNull(roomId, shelfId, boxId).isEmpty()) {
                null
            } else {
                val location = support.resolveLocation(roomId, shelfId, boxId)
                val path = location.response
                accessService.require(
                    actor,
                    accessService.target(location.spaceId, path.roomId, path.shelfId, path.boxId),
                    GrantLevel.MANAGE,
                )
                location.spaceId
            }
        val query = InventoryQueryText.normalize(q)
        val context = "items:${actor.userId}:$normalizedScope:$categoryId:$query:$roomId:$shelfId:$boxId"
        val cursor = ContextCursor.decode(cursorRaw, context, 2)
        val size = TimeCursorCodec.limit(limit)
        val rows =
            contextMapper.items(
                actor.spaceId,
                actor.userId,
                normalizedScope,
                targetSpaceId,
                categoryId,
                query,
                cursor?.get(0)?.let(ContextCursor::time),
                cursor?.get(1)?.let(ContextCursor::uuid),
                size + 1,
            )
        val page = rows.take(size)
        return PageResult(
            page,
            if (rows.size > size) {
                page.last().let {
                    ContextCursor.encode(context, listOf(it.updatedAt.toString(), it.id.toString()))
                }
            } else {
                null
            },
        )
    }

    fun get(
        actor: Actor,
        itemId: UUID,
    ): Item {
        accessService.requireOwner(actor, actor.spaceId)
        return itemMapper.findById(actor.spaceId, itemId) ?: throw notFound("Item을 찾을 수 없습니다.")
    }

    fun getReadableWithPermission(
        actor: Actor,
        itemId: UUID,
    ): Pair<Item, EffectivePermission> {
        val item = itemMapper.findByIdAny(itemId) ?: throw notFound("Item을 찾을 수 없습니다.")
        val permission = permission(actor, item)
        if (!permission.atLeast(GrantLevel.VIEW)) {
            throw notFound("Item을 찾을 수 없습니다.")
        }
        return item to permission
    }

    fun getReadable(
        actor: Actor,
        itemId: UUID,
    ): Item = getReadableWithPermission(actor, itemId).first

    fun permissions(
        actor: Actor,
        items: List<Item>,
    ): Map<UUID, EffectivePermission> {
        val own = items.filter { it.spaceId == actor.spaceId }.associate { it.id to EffectivePermission.Owner }
        val shared = items.filter { it.spaceId != actor.spaceId }
        if (shared.isEmpty()) return own
        val ranks =
            inventoryMapper
                .maxGrantRankByItemIds(actor.spaceId, actor.userId, shared.map { it.id })
                .associate { it.itemId to it.grantRank }
        return own +
            shared.associate {
                it.id to
                    when (ranks[it.id]) {
                        3 -> EffectivePermission.Granted(GrantLevel.MANAGE)
                        2 -> EffectivePermission.Granted(GrantLevel.EDIT)
                        1 -> EffectivePermission.Granted(GrantLevel.VIEW)
                        else -> EffectivePermission.None
                    }
            }
    }

    fun permission(
        actor: Actor,
        item: Item,
    ): EffectivePermission {
        if (item.spaceId == actor.spaceId) {
            return accessService.effectivePermission(actor, accessService.target(item.spaceId))
        }
        return permissions(actor, listOf(item))[item.id] ?: EffectivePermission.None
    }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

    private fun validation(message: String) = ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message)
}
