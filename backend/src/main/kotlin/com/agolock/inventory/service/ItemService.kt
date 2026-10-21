@file:Suppress("ThrowsCount", "MagicNumber", "ComplexCondition", "TooManyFunctions")

package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.domain.Item
import com.agolock.inventory.persistence.CategoryMapper
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ItemService(
    private val itemMapper: ItemMapper,
    private val categoryMapper: CategoryMapper,
    private val inventoryMapper: InventoryMapper,
    private val accessService: AccessService,
    private val clock: Clock,
) {
    fun list(
        actor: Actor,
        categoryId: UUID?,
        q: String?,
        limit: Int?,
    ): List<Item> {
        accessService.requireOwner(actor, actor.spaceId)
        val query = q?.trim()?.take(200)?.takeIf(String::isNotEmpty)
        return itemMapper.list(actor.spaceId, categoryId, query, null, null, (limit ?: 100).coerceIn(1, 100))
    }

    fun get(
        actor: Actor,
        itemId: UUID,
    ): Item {
        accessService.requireOwner(actor, actor.spaceId)
        return requireItem(actor, itemId)
    }

    fun updateImpact(
        actor: Actor,
        itemId: UUID,
    ): Pair<Int, Int> {
        accessService.requireOwner(actor, actor.spaceId)
        requireItem(actor, itemId)
        val inventories = inventoryMapper.listAllByItem(actor.spaceId, itemId)
        val protectedCount =
            inventories.count {
                it.nameOverride != null ||
                    it.descriptionOverride != null ||
                    it.categoryIdOverride != null ||
                    it.priceAmountOverride != null
            }
        return inventories.size to protectedCount
    }

    @Transactional
    fun create(
        actor: Actor,
        command: CreateItemCommand,
    ): Item {
        accessService.requireOwner(actor, actor.spaceId)
        validateCategory(actor, command.categoryId)
        val name = command.name.trim()
        if (name.isEmpty() || name.length > 120) throw validation("Item 이름은 1~120자여야 합니다.")
        val now = OffsetDateTime.now(clock)
        val item =
            Item(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                name = name,
                description = command.description?.trim()?.takeIf(String::isNotEmpty),
                categoryId = command.categoryId,
                priceAmount = command.priceAmount,
                version = 0,
                createdAt = now,
                updatedAt = now,
            )
        itemMapper.insert(item)
        return item
    }

    @Transactional
    fun update(
        actor: Actor,
        itemId: UUID,
        command: UpdateItemCommand,
    ): Item {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireItem(actor, itemId)
        if (current.version != command.version) throw versionConflict()
        validateCategory(actor, command.categoryId)
        val name = command.name?.trim() ?: current.name
        if (name.isEmpty() || name.length > 120) throw validation("Item 이름은 1~120자여야 합니다.")
        val description =
            if (command.descriptionPresent) {
                command.description?.trim()?.takeIf(String::isNotEmpty)
            } else {
                current.description
            }
        val categoryId = if (command.categoryIdPresent) command.categoryId else current.categoryId
        val priceAmount = if (command.priceAmountPresent) command.priceAmount else current.priceAmount
        if (name == current.name &&
            description == current.description &&
            categoryId == current.categoryId &&
            priceAmount == current.priceAmount
        ) {
            return current
        }
        val updated =
            itemMapper.update(
                actor.spaceId,
                itemId,
                name,
                description,
                categoryId,
                priceAmount,
                command.version,
                OffsetDateTime.now(clock),
            )
        if (updated == 0) throw versionConflict()
        return requireItem(actor, itemId)
    }

    @Transactional
    fun delete(
        actor: Actor,
        itemId: UUID,
    ) {
        accessService.requireOwner(actor, actor.spaceId)
        requireItem(actor, itemId)
        if (inventoryMapper.countByItem(actor.spaceId, itemId) > 0) {
            throw conflict("ITEM_HAS_INVENTORIES", "Inventory가 있는 Item은 삭제할 수 없습니다.")
        }
        itemMapper.softDelete(actor.spaceId, itemId, OffsetDateTime.now(clock))
    }

    fun requireItem(
        actor: Actor,
        itemId: UUID,
    ): Item =
        itemMapper.findById(actor.spaceId, itemId)
            ?: throw ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Item을 찾을 수 없습니다.")

    private fun validateCategory(
        actor: Actor,
        categoryId: UUID?,
    ) {
        if (categoryId != null && categoryMapper.findById(actor.spaceId, categoryId) == null) {
            throw ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다.")
        }
    }

    private fun validation(message: String) = ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message)

    private fun versionConflict() = ApiException("VERSION_CONFLICT", HttpStatus.CONFLICT, "다른 변경이 먼저 저장되었습니다.")

    private fun conflict(
        code: String,
        message: String,
    ) = ApiException(code, HttpStatus.CONFLICT, message)
}
