package com.agolock.inventory.service

import com.agolock.common.cursor.TimeCursorCodec
import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.PageResult
import com.agolock.inventory.domain.Category
import com.agolock.inventory.persistence.InventoryContextQueryMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CategoryQueryService(
    private val mapper: InventoryContextQueryMapper,
    private val items: ItemQueryService,
) {
    fun list(
        actor: Actor,
        itemId: UUID?,
        limit: Int?,
        cursorRaw: String?,
    ): PageResult<Category> {
        val spaceId = itemId?.let { items.getReadable(actor, it).spaceId } ?: actor.spaceId
        val context = "categories:${actor.userId}:$spaceId:$itemId"
        val cursor = ContextCursor.decode(cursorRaw, context, CATEGORY_CURSOR_FIELDS)
        val size = TimeCursorCodec.limit(limit)
        val rows =
            mapper.categories(
                actor.spaceId,
                actor.userId,
                spaceId,
                null,
                cursor?.get(0)?.let(ContextCursor::number),
                cursor?.get(1),
                cursor?.get(2)?.let(ContextCursor::uuid),
                size + 1,
            )
        val page = rows.take(size)
        return PageResult(
            page,
            if (rows.size > size) {
                page.last().let {
                    ContextCursor.encode(context, listOf(it.sortOrder.toString(), it.name, it.id.toString()))
                }
            } else {
                null
            },
        )
    }

    fun requireSelectable(
        actor: Actor,
        spaceId: UUID,
        categoryId: UUID,
    ) {
        if (mapper.categories(actor.spaceId, actor.userId, spaceId, categoryId, null, null, null, 1).isEmpty()) {
            throw ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다.")
        }
    }

    private companion object {
        const val CATEGORY_CURSOR_FIELDS = 3
    }
}
