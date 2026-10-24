package com.agolock.inventory.service

import com.agolock.inventory.domain.InventoryStatus
import java.util.UUID

data class CreateCategoryCommand(
    val name: String,
    val color: String?,
    val sortOrder: Int,
)

data class UpdateCategoryCommand(
    val name: String?,
    val color: String?,
    val sortOrder: Int?,
    val colorPresent: Boolean = false,
)

data class CreateItemCommand(
    val name: String,
    val description: String?,
    val categoryId: UUID?,
    val priceAmount: Int?,
)

data class UpdateItemCommand(
    val name: String?,
    val description: String?,
    val categoryId: UUID?,
    val priceAmount: Int?,
    val descriptionPresent: Boolean,
    val categoryIdPresent: Boolean,
    val priceAmountPresent: Boolean,
    val version: Long,
    val namePresent: Boolean = false,
)

data class NewItemCommand(
    val name: String,
    val description: String?,
    val categoryId: UUID?,
    val priceAmount: Int?,
)

data class CreateInventoryCommand(
    val itemId: UUID?,
    val newItem: NewItemCommand?,
    val roomId: UUID?,
    val shelfId: UUID?,
    val boxId: UUID?,
    val status: InventoryStatus,
)

data class UpdateInventoryCommand(
    val nameOverride: String?,
    val descriptionOverride: String?,
    val categoryIdOverride: UUID?,
    val priceAmountOverride: Int?,
    val nameOverridePresent: Boolean,
    val descriptionOverridePresent: Boolean,
    val categoryIdOverridePresent: Boolean,
    val priceAmountOverridePresent: Boolean,
    val statusPresent: Boolean,
    val status: InventoryStatus?,
    val confirmDisposal: Boolean,
    val version: Long,
)

data class MoveInventoryCommand(
    val roomId: UUID?,
    val shelfId: UUID?,
    val boxId: UUID?,
    val version: Long,
)
