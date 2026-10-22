@file:Suppress("LongParameterList")

package com.agolock.inventory.persistence

import com.agolock.inventory.domain.InventoryStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * `inventory_access` view + ACL + Item 조인으로 조회한 목록 row입니다.
 *
 * MyBatis setter 매핑을 위해 모든 프로퍼티를 `var`로 두고 안전한 기본값을 둡니다.
 */
data class InventoryListRow(
    var id: UUID = UUID(0, 0),
    var itemId: UUID = UUID(0, 0),
    var status: InventoryStatus = InventoryStatus.STORED,
    var version: Long = 0,
    var updatedAt: OffsetDateTime = OffsetDateTime.MIN,
    var createdAt: OffsetDateTime = OffsetDateTime.MIN,
    var nameOverride: String? = null,
    var descriptionOverride: String? = null,
    var categoryIdOverride: UUID? = null,
    var priceAmountOverride: Int? = null,
    var itemName: String = "",
    var itemDescription: String? = null,
    var itemCategoryId: UUID? = null,
    var itemPriceAmount: Int? = null,
    var displayRoomId: UUID? = null,
    var displayShelfId: UUID? = null,
    var displayBoxId: UUID? = null,
    var roomName: String? = null,
    var shelfName: String? = null,
    var boxName: String? = null,
    var effectivePermission: String = "NONE",
)
