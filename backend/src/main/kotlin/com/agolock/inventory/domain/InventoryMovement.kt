package com.agolock.inventory.domain

import java.time.OffsetDateTime
import java.util.UUID

data class InventoryMovement(
    var id: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var inventoryId: UUID = UUID(0, 0),
    var fromRoomId: UUID? = null,
    var fromShelfId: UUID? = null,
    var fromBoxId: UUID? = null,
    var toRoomId: UUID? = null,
    var toShelfId: UUID? = null,
    var toBoxId: UUID? = null,
    var movedBy: UUID = UUID(0, 0),
    var movedAt: OffsetDateTime = OffsetDateTime.MIN,
)
