package com.agolock.search.domain

import java.util.UUID

data class SearchAccessScope(
    val ownSpaceId: UUID,
    val sharedSpaceIds: Set<UUID>,
    val roomIds: Set<UUID>,
    val shelfIds: Set<UUID>,
    val boxIds: Set<UUID>,
)
