package com.agolock.search.service

import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.search.domain.SearchAccessScope
import com.agolock.sharing.persistence.ShareMapper
import org.springframework.stereotype.Service

@Service
class SearchAccessService(
    private val shareMapper: ShareMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
) {
    fun resolve(actor: Actor): SearchAccessScope {
        val shares = shareMapper.listActiveByGrantee(actor.userId)
        val sharedSpaceIds = shares.map { it.spaceId }.toSet()
        val directRoomIds = shares.mapNotNull { it.roomId }.toSet()
        val directShelfIds = shares.mapNotNull { it.shelfId }.toSet()

        val shelvesUnderRooms =
            if (directRoomIds.isEmpty()) {
                emptyList()
            } else {
                shelfMapper.listIdsByRoomIds(directRoomIds.toList())
            }
        val allShelfIds = directShelfIds + shelvesUnderRooms
        val boxes = if (allShelfIds.isEmpty()) emptyList() else boxMapper.listIdsByShelfIds(allShelfIds.toList())

        return SearchAccessScope(
            ownSpaceId = actor.spaceId,
            sharedSpaceIds = sharedSpaceIds,
            roomIds = directRoomIds,
            shelfIds = allShelfIds,
            boxIds = boxes.toSet(),
        )
    }
}
