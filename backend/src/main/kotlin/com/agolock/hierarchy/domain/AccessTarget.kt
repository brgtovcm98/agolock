package com.agolock.hierarchy.domain

import java.util.UUID

/**
 * ACL 판정에 필요한 최소 위치 문맥입니다.
 * Room은 roomId, Shelf는 roomId+shelfId, Box는 세 ID를 모두 채웁니다.
 */
data class AccessTarget(
    val spaceId: UUID,
    val ownerUserId: UUID,
    val roomId: UUID? = null,
    val shelfId: UUID? = null,
    val boxId: UUID? = null,
) {
    val key: String
        get() = "$spaceId:$roomId:$shelfId:$boxId"
}
