package com.agolock.inventory.domain

import java.time.OffsetDateTime
import java.util.UUID

data class Inventory(
    var id: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var itemId: UUID = UUID(0, 0),
    var roomId: UUID? = null,
    var shelfId: UUID? = null,
    var boxId: UUID? = null,
    var nameOverride: String? = null,
    var descriptionOverride: String? = null,
    var categoryIdOverride: UUID? = null,
    var priceAmountOverride: Int? = null,
    var status: InventoryStatus = InventoryStatus.STORED,
    var version: Long = 0,
    var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    var updatedAt: OffsetDateTime = OffsetDateTime.MIN,
    var deletedAt: OffsetDateTime? = null,
)
