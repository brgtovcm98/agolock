package com.agolock.inventory.domain

import java.time.OffsetDateTime
import java.util.UUID

data class Category(
    var id: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var name: String = "",
    var normalizedName: String = "",
    var color: String? = null,
    var sortOrder: Int = 0,
    var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    var updatedAt: OffsetDateTime = OffsetDateTime.MIN,
    var deletedAt: OffsetDateTime? = null,
)
