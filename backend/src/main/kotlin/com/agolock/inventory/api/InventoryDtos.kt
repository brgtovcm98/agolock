@file:Suppress("MaxLineLength")

package com.agolock.inventory.api

import com.agolock.inventory.domain.Category
import com.agolock.inventory.domain.InventoryMovement
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.domain.Item
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class CreateCategoryRequest(
    @field:NotBlank @field:Size(max = 80) val name: String,
    @field:Size(max = 32) val color: String? = null,
    @field:Min(0) val sortOrder: Int = 0,
)

data class UpdateCategoryRequest(
    @field:Size(min = 1, max = 80) val name: String? = null,
    @field:Size(max = 32) val color: String? = null,
    @field:Min(0) val sortOrder: Int? = null,
)

data class CategoryResponse(
    val id: UUID,
    val name: String,
    val color: String?,
    val sortOrder: Int,
)

data class CreateItemRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:Size(max = 2000) val description: String? = null,
    val categoryId: UUID? = null,
    @field:Min(0) val priceAmount: Int? = null,
)

data class UpdateItemRequest(
    @field:Size(min = 1, max = 120) val name: String? = null,
    @field:Size(max = 2000) val description: String? = null,
    val categoryId: UUID? = null,
    @field:Min(0) val priceAmount: Int? = null,
    @field:NotNull val version: Long? = null,
)

data class ItemResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val categoryId: UUID?,
    val priceAmount: Int?,
    val currency: String,
    val version: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class InventoryLocationResponse(
    val type: String,
    val roomId: UUID?,
    val shelfId: UUID?,
    val boxId: UUID?,
    val name: String,
)

data class InventoryResponse(
    val id: UUID,
    val itemId: UUID,
    val itemName: String,
    val displayName: String,
    val description: String?,
    val categoryId: UUID?,
    val priceAmount: Int?,
    val status: InventoryStatus,
    val location: InventoryLocationResponse,
    val nameOverride: String?,
    val descriptionOverride: String?,
    val categoryIdOverride: UUID?,
    val priceAmountOverride: Int?,
    val version: Long,
    val effectivePermission: String,
    val isShared: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class CreateInventoryRequest(
    @field:NotNull val itemId: UUID,
    val roomId: UUID? = null,
    val shelfId: UUID? = null,
    val boxId: UUID? = null,
    val status: InventoryStatus = InventoryStatus.STORED,
)

data class UpdateInventoryRequest(
    @field:Size(max = 120) val nameOverride: String? = null,
    @field:Size(max = 2000) val descriptionOverride: String? = null,
    val categoryIdOverride: UUID? = null,
    @field:Min(0) val priceAmountOverride: Int? = null,
    @field:NotNull val status: InventoryStatus? = null,
    val confirmDisposal: Boolean = false,
    @field:NotNull val version: Long? = null,
)

data class MoveInventoryRequest(
    val roomId: UUID? = null,
    val shelfId: UUID? = null,
    val boxId: UUID? = null,
    @field:NotNull val version: Long? = null,
)

data class MovementResponse(
    val id: UUID,
    val inventoryId: UUID,
    val from: InventoryLocationResponse,
    val to: InventoryLocationResponse,
    val movedAt: OffsetDateTime,
)

data class InventoryListResponse(
    val items: List<InventoryResponse>,
    val nextCursor: String? = null,
)

data class ItemListResponse(
    val items: List<ItemResponse>,
    val nextCursor: String? = null,
)

data class CategoryListResponse(
    val items: List<CategoryResponse>,
)

fun Category.toResponse(): CategoryResponse = CategoryResponse(id, name, color, sortOrder)

fun Item.toResponse(): ItemResponse = ItemResponse(id, name, description, categoryId, priceAmount, currency, version, createdAt, updatedAt)

fun InventoryMovement.toResponse(locationLoader: (UUID?, UUID?, UUID?) -> InventoryLocationResponse): MovementResponse =
    MovementResponse(
        id = id,
        inventoryId = inventoryId,
        from = locationLoader(fromRoomId, fromShelfId, fromBoxId),
        to = locationLoader(toRoomId, toShelfId, toBoxId),
        movedAt = movedAt,
    )

data class LocationItemResponse(
    val type: String,
    val id: UUID,
    val path: String,
)

data class LocationListResponse(
    val items: List<LocationItemResponse>,
)

data class MergeCategoryRequest(
    @field:NotNull val targetCategoryId: UUID? = null,
)

data class ItemUpdateImpactResponse(
    val affectedInventoryCount: Int,
    val protectedByOverrideCount: Int,
)
