package com.agolock.inventory.service

import com.agolock.inventory.domain.InventoryLocation
import com.agolock.inventory.persistence.InventoryAccessMapper
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Inventory 위치를 `inventory_access` view로 해석합니다.
 *
 * 서비스마다 Shelf/Box를 다시 조회하지 않도록 위치 해석의 단일 진입점 역할을 합니다.
 */
@Service
class InventoryLocationResolver(
    private val mapper: InventoryAccessMapper,
) {
    fun resolve(inventoryId: UUID): InventoryLocation? = mapper.findById(inventoryId)
}
