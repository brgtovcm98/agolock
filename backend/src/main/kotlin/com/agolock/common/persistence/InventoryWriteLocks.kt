package com.agolock.common.persistence

import com.agolock.common.error.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** All writers acquire Category, Item, Room, Shelf, Box, Inventory in this order. */
@Component
class InventoryWriteLocks(
    private val jdbc: JdbcTemplate,
) {
    fun lockCategories(
        spaceId: UUID,
        ids: Collection<UUID>,
    ) = lockRows("category", spaceId, ids)

    fun lockItems(
        spaceId: UUID,
        ids: Collection<UUID>,
    ) = lockRows("item", spaceId, ids)

    fun lockInventory(
        spaceId: UUID,
        id: UUID,
    ) = lockRows("inventory", spaceId, listOf(id))

    fun path(
        spaceId: UUID,
        roomId: UUID?,
        shelfId: UUID?,
        boxId: UUID?,
    ): LocationLockPath {
        val boxShelf = boxId?.let { parent("box", "shelf_id", spaceId, it) }
        val shelf = boxShelf ?: shelfId
        val shelfRoom = shelf?.let { parent("shelf", "room_id", spaceId, it) }
        val room = shelfRoom ?: roomId ?: throw notFound()
        parent("room", "id", spaceId, room)
        return LocationLockPath(spaceId, room, shelf, boxId)
    }

    fun lockPaths(paths: Collection<LocationLockPath>) {
        // A path may change while waiting for an ancestor lock. Never acquire a new
        // ancestor out of order: reject that snapshot and let the caller refresh.
        paths.groupBy { it.spaceId }.forEach { (spaceId, entries) ->
            lockRows("room", spaceId, entries.map { it.roomId })
        }
        paths.groupBy { it.spaceId }.forEach { (spaceId, entries) ->
            lockRows("shelf", spaceId, entries.mapNotNull { it.shelfId })
        }
        paths.groupBy { it.spaceId }.forEach { (spaceId, entries) ->
            lockRows("box", spaceId, entries.mapNotNull { it.boxId })
        }
        paths.forEach { expected ->
            val latest = path(expected.spaceId, expected.roomId, expected.shelfId, expected.boxId)
            if (latest != expected) throw contextChanged()
        }
    }

    fun lockCategoryReferences(
        spaceId: UUID,
        sourceId: UUID,
    ) {
        val items =
            jdbc.queryForList(
                "SELECT id FROM item WHERE space_id = ? AND category_id = ? AND deleted_at IS NULL",
                UUID::class.java,
                spaceId,
                sourceId,
            )
        lockItems(spaceId, items)
        val inventories =
            jdbc.queryForList(
                "SELECT id FROM inventory WHERE space_id = ? AND category_id_override = ? AND deleted_at IS NULL",
                UUID::class.java,
                spaceId,
                sourceId,
            )
        lockRows("inventory", spaceId, inventories)
    }

    private fun lockRows(
        table: String,
        spaceId: UUID,
        ids: Collection<UUID>,
    ) {
        ids.distinct().sortedBy(UUID::toString).forEach { id ->
            val rows =
                jdbc.queryForList(
                    "SELECT id FROM $table WHERE space_id = ? AND id = ? FOR UPDATE",
                    UUID::class.java,
                    spaceId,
                    id,
                )
            if (rows.isEmpty()) throw notFound()
        }
    }

    private fun parent(
        table: String,
        column: String,
        spaceId: UUID,
        id: UUID,
    ): UUID =
        jdbc
            .queryForList(
                "SELECT $column FROM $table WHERE space_id = ? AND id = ? AND deleted_at IS NULL",
                UUID::class.java,
                spaceId,
                id,
            ).firstOrNull() ?: throw notFound()

    private fun notFound() = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다.")

    private fun contextChanged() =
        ApiException(
            "RESOURCE_CONTEXT_CHANGED",
            HttpStatus.CONFLICT,
            "위치 경로가 변경되었습니다. 다시 확인해 주세요.",
        )
}

data class LocationLockPath(
    val spaceId: UUID,
    val roomId: UUID,
    val shelfId: UUID?,
    val boxId: UUID?,
)
