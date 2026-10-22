package com.agolock.hierarchy.domain

import java.util.UUID

/**
 * ACL 판정에 필요한 최소 위치 문맥입니다.
 *
 * Room은 roomId, Shelf는 roomId+shelfId, Box는 세 ID를 모두 채웁니다.
 * Space는 위치 ID가 모두 null인 최상위 문맥입니다.
 */
sealed interface AccessTarget {
    val spaceId: UUID
    val ownerUserId: UUID
    val roomId: UUID?
        get() = null
    val shelfId: UUID?
        get() = null
    val boxId: UUID?
        get() = null

    data class Space(
        override val spaceId: UUID,
        override val ownerUserId: UUID,
    ) : AccessTarget

    data class Room(
        override val spaceId: UUID,
        override val ownerUserId: UUID,
        override val roomId: UUID,
    ) : AccessTarget

    data class Shelf(
        override val spaceId: UUID,
        override val ownerUserId: UUID,
        override val roomId: UUID,
        override val shelfId: UUID,
    ) : AccessTarget

    data class Box(
        override val spaceId: UUID,
        override val ownerUserId: UUID,
        override val roomId: UUID,
        override val shelfId: UUID,
        override val boxId: UUID,
    ) : AccessTarget
}
