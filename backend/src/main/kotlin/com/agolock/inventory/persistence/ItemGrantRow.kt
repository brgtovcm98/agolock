package com.agolock.inventory.persistence

import java.util.UUID

data class ItemGrantRow(
    var itemId: UUID = UUID(0, 0),
    var grantRank: Int? = null,
)
