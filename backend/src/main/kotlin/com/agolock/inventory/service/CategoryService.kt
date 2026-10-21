@file:Suppress("ThrowsCount", "MagicNumber")

package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.domain.Category
import com.agolock.inventory.persistence.CategoryMapper
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID

@Service
class CategoryService(
    private val categoryMapper: CategoryMapper,
    private val itemMapper: ItemMapper,
    private val inventoryMapper: InventoryMapper,
    private val accessService: AccessService,
    private val clock: Clock,
) {
    fun list(actor: Actor): List<Category> {
        accessService.requireOwner(actor, actor.spaceId)
        return categoryMapper.list(actor.spaceId, 100)
    }

    @Transactional
    fun create(
        actor: Actor,
        command: CreateCategoryCommand,
    ): Category {
        accessService.requireOwner(actor, actor.spaceId)
        val name = command.name.trim()
        if (name.isEmpty() || name.length > 80) throw validation("카테고리 이름은 1~80자여야 합니다.")
        val normalized = normalize(name)
        if (categoryMapper.findActiveByNormalizedName(actor.spaceId, normalized) != null) {
            throw conflict("CATEGORY_ALREADY_EXISTS", "같은 이름의 카테고리가 이미 있습니다.")
        }
        val now = OffsetDateTime.now(clock)
        val category =
            Category(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                name = name,
                normalizedName = normalized,
                color = command.color?.trim()?.takeIf(String::isNotEmpty),
                sortOrder = command.sortOrder,
                createdAt = now,
                updatedAt = now,
            )
        categoryMapper.insert(category)
        return category
    }

    @Transactional
    fun update(
        actor: Actor,
        categoryId: UUID,
        command: UpdateCategoryCommand,
    ): Category {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireCategory(actor, categoryId)
        val name = command.name?.trim() ?: current.name
        if (name.isEmpty() || name.length > 80) throw validation("카테고리 이름은 1~80자여야 합니다.")
        val normalized = normalize(name)
        val duplicate = categoryMapper.findActiveByNormalizedName(actor.spaceId, normalized)
        if (duplicate != null && duplicate.id != categoryId) {
            throw conflict("CATEGORY_ALREADY_EXISTS", "같은 이름의 카테고리가 이미 있습니다.")
        }
        val updated =
            categoryMapper.update(
                actor.spaceId,
                categoryId,
                name,
                normalized,
                command.color?.trim()?.takeIf(String::isNotEmpty) ?: current.color,
                command.sortOrder ?: current.sortOrder,
                OffsetDateTime.now(clock),
            )
        if (updated == 0) throw conflict("VERSION_CONFLICT", "카테고리를 수정하지 못했습니다.")
        return requireCategory(actor, categoryId)
    }

    @Transactional
    fun merge(
        actor: Actor,
        sourceId: UUID,
        targetId: UUID,
    ): Pair<Int, Int> {
        accessService.requireOwner(actor, actor.spaceId)
        if (sourceId == targetId) throw validation("같은 카테고리로 병합할 수 없습니다.")
        requireCategory(actor, sourceId)
        requireCategory(actor, targetId)
        val now = OffsetDateTime.now(clock)
        val items = itemMapper.reassignCategory(actor.spaceId, sourceId, targetId, now)
        val inventories = inventoryMapper.reassignCategoryOverride(actor.spaceId, sourceId, targetId, now)
        categoryMapper.softDelete(actor.spaceId, sourceId, now)
        return items to inventories
    }

    @Transactional
    fun delete(
        actor: Actor,
        categoryId: UUID,
    ) {
        accessService.requireOwner(actor, actor.spaceId)
        requireCategory(actor, categoryId)
        if (itemMapper.listByCategory(actor.spaceId, categoryId).isNotEmpty() ||
            inventoryMapper.countByCategoryOverride(actor.spaceId, categoryId) > 0
        ) {
            throw conflict("CATEGORY_IN_USE", "Item 또는 Inventory가 사용 중인 카테고리는 삭제할 수 없습니다.")
        }
        categoryMapper.softDelete(actor.spaceId, categoryId, OffsetDateTime.now(clock))
    }

    private fun requireCategory(
        actor: Actor,
        id: UUID,
    ): Category =
        categoryMapper.findById(actor.spaceId, id)
            ?: throw ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다.")

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun validation(message: String) = ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message)

    private fun conflict(
        code: String,
        message: String,
    ) = ApiException(code, HttpStatus.CONFLICT, message)
}
