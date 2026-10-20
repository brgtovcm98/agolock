package com.agolock.inventory.domain

import java.time.OffsetDateTime
import java.util.UUID

data class Item(
    var id: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var name: String = "",
    var description: String? = null,
    var categoryId: UUID? = null,
    var priceAmount: Int? = null,
    var currency: String = "KRW",
    var version: Long = 0,
    var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    var updatedAt: OffsetDateTime = OffsetDateTime.MIN,
    var deletedAt: OffsetDateTime? = null,
)
