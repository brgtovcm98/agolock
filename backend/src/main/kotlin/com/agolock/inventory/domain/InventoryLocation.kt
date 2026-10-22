package com.agolock.inventory.domain

import java.util.UUID

/**
 * `inventory_access` view에서 읽은 위치 해석 결과 row DTO입니다.
 *
 * 도메인 엔티티가 아니며, MyBatis가 setter로 채울 수 있도록 모든 프로퍼티를 `var`로 둡니다.
 * 위치 판정에 필요한 8개 컬럼만 보관합니다.
 */
data class InventoryLocation(
    var inventoryId: UUID = UUID(0, 0),
    var spaceId: UUID = UUID(0, 0),
    var itemId: UUID = UUID(0, 0),
    var boxId: UUID? = null,
    var status: InventoryStatus = InventoryStatus.STORED,
    var effectiveRoomId: UUID? = null,
    var effectiveShelfId: UUID? = null,
    var locationType: String = "ROOM",
)
