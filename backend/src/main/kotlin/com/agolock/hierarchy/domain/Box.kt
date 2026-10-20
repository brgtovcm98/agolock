package com.agolock.hierarchy.domain

import java.time.OffsetDateTime
import java.util.UUID

data class Box(
    var id: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var shelfId: UUID = UUID(0, 0),
    var name: String = "",
    var description: String? = null,
    var version: Long = 0,
    var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    var updatedAt: OffsetDateTime = OffsetDateTime.MIN,
    var deletedAt: OffsetDateTime? = null,
    var inventoryCount: Int = 0,
)
