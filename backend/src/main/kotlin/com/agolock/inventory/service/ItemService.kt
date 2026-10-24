@file:Suppress("ThrowsCount", "MagicNumber", "ComplexCondition", "TooManyFunctions", "MaxLineLength", "LongParameterList")

package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.common.persistence.InventoryWriteLocks
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.api.ItemFieldImpactResponse
import com.agolock.inventory.api.ItemUpdateImpactPreviewResponse
import com.agolock.inventory.domain.Item
import com.agolock.inventory.domain.ItemFieldPolicy
import com.agolock.inventory.persistence.CategoryMapper
import com.agolock.inventory.persistence.InventoryContextQueryMapper
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
    private val contextMapper: InventoryContextQueryMapper,
    private val queries: ItemQueryService,
    private val locks: InventoryWriteLocks,
) {
    fun getReadable(
        actor: Actor,
        itemId: UUID,
    ): Item = queries.getReadable(actor, itemId)

    fun updateImpact(
        actor: Actor,
        itemId: UUID,
        command: UpdateItemCommand,
    ): ItemUpdateImpactPreviewResponse {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireItem(actor, itemId)
        val proposal = proposal(actor, current, command)
        val fields =
            listOf("name", "description", "categoryId", "priceAmount").filter {
                when (it) {
                    "name" -> current.name != proposal.name
                    "description" -> current.description != proposal.description
                    "categoryId" -> current.categoryId != proposal.categoryId
                    else -> current.priceAmount != proposal.priceAmount
                }
            }
        val counts =
            contextMapper.impact(
                current.spaceId,
                itemId,
                "name" in fields,
                "description" in fields,
                "categoryId" in fields,
                "priceAmount" in fields,
            )
        val fieldCounts =
            mapOf(
                "name" to counts.nameAffected,
                "description" to counts.descriptionAffected,
                "categoryId" to counts.categoryAffected,
                "priceAmount" to counts.priceAffected,
            )
        return ItemUpdateImpactPreviewResponse(
            current.version,
            fields,
            counts.total,
            counts.affected,
            counts.total - counts.affected,
            fields.map {
                ItemFieldImpactResponse(
                    it,
                    fieldCounts.getValue(it),
                    counts.total - fieldCounts.getValue(it),
                )
            },
        )
    }

    @Transactional
    fun create(
        actor: Actor,
        command: CreateItemCommand,
    ): Item {
        accessService.requireOwner(actor, actor.spaceId)
        locks.lockCategories(actor.spaceId, listOfNotNull(command.categoryId))
        validateCategory(actor, command.categoryId)
        val name = ItemFieldPolicy.name(command.name)
        ItemFieldPolicy.description(command.description)
        ItemFieldPolicy.price(command.priceAmount)
        val now = OffsetDateTime.now(clock)
        val item =
            Item(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                name = name,
                description = ItemFieldPolicy.description(command.description),
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
        val snapshot = requireItem(actor, itemId)
        locks.lockCategories(actor.spaceId, listOfNotNull(snapshot.categoryId, command.categoryId))
        locks.lockItems(actor.spaceId, listOf(itemId))
        val current = requireItem(actor, itemId)
        if (snapshot.categoryId != current.categoryId) throw conflict("RESOURCE_CONTEXT_CHANGED", "카테고리가 변경되었습니다.")
        val proposed = proposal(actor, current, command)
        val name = proposed.name
        val description = proposed.description
        val categoryId = proposed.categoryId
        val priceAmount = proposed.priceAmount
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
        version: Long,
    ) {
        ItemFieldPolicy.version(version)
        accessService.requireOwner(actor, actor.spaceId)
        val snapshot = requireItem(actor, itemId)
        locks.lockCategories(actor.spaceId, listOfNotNull(snapshot.categoryId))
        locks.lockItems(actor.spaceId, listOf(itemId))
        val current = requireItem(actor, itemId)
        if (current.version != version) throw versionConflict()
        if (inventoryMapper.countByItem(actor.spaceId, itemId) > 0) {
            throw conflict("ITEM_HAS_INVENTORIES", "Inventory가 있는 Item은 삭제할 수 없습니다.")
        }
        if (itemMapper.softDelete(actor.spaceId, itemId, version, OffsetDateTime.now(clock)) == 0) throw versionConflict()
    }

    fun requireItem(
        actor: Actor,
        itemId: UUID,
    ): Item =
        itemMapper.findById(actor.spaceId, itemId)
            ?: throw ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "Item을 찾을 수 없습니다.")

    private fun proposal(
        actor: Actor,
        current: Item,
        command: UpdateItemCommand,
    ): Item {
        ItemFieldPolicy.version(command.version)
        if (current.version != command.version) throw versionConflict()
        if (command.namePresent && command.name == null) throw validation("Item 이름은 null일 수 없습니다.")
        val name = command.name?.let(ItemFieldPolicy::name) ?: current.name
        val description = if (command.descriptionPresent) ItemFieldPolicy.description(command.description) else current.description
        val categoryId = if (command.categoryIdPresent) command.categoryId else current.categoryId
        val price = if (command.priceAmountPresent) ItemFieldPolicy.price(command.priceAmount) else current.priceAmount
        validateCategory(actor, categoryId)
        return current.copy(name = name, description = description, categoryId = categoryId, priceAmount = price)
    }

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
