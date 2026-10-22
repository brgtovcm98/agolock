package com.agolock.inventory.service

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.domain.toPermission
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.inventory.api.InventoryLocationResponse
import com.agolock.inventory.api.InventoryResponse
import com.agolock.inventory.domain.Inventory
import com.agolock.inventory.persistence.InventoryListRow
import com.agolock.inventory.persistence.ItemMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class InventoryResponseAssembler(
    private val itemMapper: ItemMapper,
    private val roomMapper: RoomMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
) {
    fun toResponse(
        inventory: Inventory,
        permission: EffectivePermission,
    ): InventoryResponse {
        val item =
            itemMapper.findById(inventory.spaceId, inventory.itemId)
                ?: throw notFound("Item을 찾을 수 없습니다.")
        return InventoryResponse(
            id = inventory.id,
            itemId = inventory.itemId,
            itemName = item.name,
            displayName = inventory.nameOverride ?: item.name,
            description = inventory.descriptionOverride ?: item.description,
            categoryId = inventory.categoryIdOverride ?: item.categoryId,
            priceAmount = inventory.priceAmountOverride ?: item.priceAmount,
            status = inventory.status,
            location = location(inventory.spaceId, inventory.roomId, inventory.shelfId, inventory.boxId),
            nameOverride = inventory.nameOverride,
            descriptionOverride = inventory.descriptionOverride,
            categoryIdOverride = inventory.categoryIdOverride,
            priceAmountOverride = inventory.priceAmountOverride,
            version = inventory.version,
            effectivePermission = permission.toPermission(),
            isShared = permission is EffectivePermission.Granted,
            createdAt = inventory.createdAt,
            updatedAt = inventory.updatedAt,
        )
    }

    fun toResponse(row: InventoryListRow): InventoryResponse {
        val type =
            when {
                row.displayBoxId != null -> "BOX"
                row.displayShelfId != null -> "SHELF"
                else -> "ROOM"
            }
        val locationName =
            when (type) {
                "BOX" -> row.boxName ?: "삭제된 Box"
                "SHELF" -> row.shelfName ?: "삭제된 Shelf"
                else -> row.roomName ?: "삭제된 Room"
            }
        return InventoryResponse(
            id = row.id,
            itemId = row.itemId,
            itemName = row.itemName,
            displayName = row.nameOverride ?: row.itemName,
            description = row.descriptionOverride ?: row.itemDescription,
            categoryId = row.categoryIdOverride ?: row.itemCategoryId,
            priceAmount = row.priceAmountOverride ?: row.itemPriceAmount,
            status = row.status,
            location =
                InventoryLocationResponse(
                    type = type,
                    roomId = row.displayRoomId,
                    shelfId = row.displayShelfId,
                    boxId = row.displayBoxId,
                    name = locationName,
                ),
            nameOverride = row.nameOverride,
            descriptionOverride = row.descriptionOverride,
            categoryIdOverride = row.categoryIdOverride,
            priceAmountOverride = row.priceAmountOverride,
            version = row.version,
            effectivePermission = Permission.valueOf(row.effectivePermission),
            isShared = row.effectivePermission != "OWNER",
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
        )
    }

    fun location(
        spaceId: UUID,
        roomId: UUID?,
        shelfId: UUID?,
        boxId: UUID?,
    ): InventoryLocationResponse =
        when {
            boxId != null -> {
                val box = boxMapper.findById(spaceId, boxId)
                val shelf = box?.let { shelfMapper.findById(spaceId, it.shelfId) }
                InventoryLocationResponse("BOX", shelf?.roomId, box?.shelfId, boxId, box?.name ?: "삭제된 Box")
            }
            shelfId != null -> {
                val shelf = shelfMapper.findById(spaceId, shelfId)
                InventoryLocationResponse("SHELF", shelf?.roomId, shelfId, null, shelf?.name ?: "삭제된 Shelf")
            }
            roomId != null -> {
                val room = roomMapper.findById(spaceId, roomId)
                InventoryLocationResponse("ROOM", roomId, null, null, room?.name ?: "삭제된 Room")
            }
            else -> InventoryLocationResponse("UNKNOWN", null, null, null, "위치 없음")
        }

    private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)
}
