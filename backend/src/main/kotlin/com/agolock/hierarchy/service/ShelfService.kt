package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.common.persistence.InventoryWriteLocks
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.domain.PageResult
import com.agolock.hierarchy.domain.Shelf
import com.agolock.hierarchy.persistence.HierarchyCountMapper
import com.agolock.hierarchy.persistence.RoomMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Suppress("TooManyFunctions")
class ShelfService
    @Suppress("LongParameterList")
    constructor(
        private val shelfMapper: ShelfMapper,
        private val roomMapper: RoomMapper,
        private val accessService: AccessService,
        private val deleteGuard: HierarchyDeleteGuard,
        private val countMapper: HierarchyCountMapper,
        private val clock: Clock,
        private val locks: InventoryWriteLocks,
    ) {
        @Transactional(readOnly = true)
        fun list(
            actor: Actor,
            roomId: UUID,
            cursorRaw: String?,
            limitRaw: Int?,
        ): PageResult<Shelf> {
            val room = roomMapper.findByIdAny(roomId) ?: throw notFound("Room을 찾을 수 없습니다.")
            accessService.require(
                actor,
                accessService.target(room.spaceId, roomId = room.id),
                GrantLevel.VIEW,
            )
            val cursor = CursorCodec.decode(cursorRaw)
            val limit = CursorCodec.limit(limitRaw)
            val shelves = shelfMapper.listByRoom(room.spaceId, roomId, cursor?.name, cursor?.id, limit + 1)
            val shelfIds = shelves.map { it.id }
            val boxCounts =
                if (shelfIds.isEmpty()) {
                    emptyMap()
                } else {
                    countMapper.countBoxesByShelfIds(room.spaceId, shelfIds).associate { it.id to it.cnt }
                }
            val inventoryCounts =
                if (shelfIds.isEmpty()) {
                    emptyMap()
                } else {
                    countMapper.countInventoryByShelfIds(room.spaceId, shelfIds).associate { it.id to it.cnt }
                }
            val shelvesWithCounts =
                shelves.map {
                    it.copy(
                        boxCount = boxCounts[it.id] ?: 0,
                        inventoryCount = inventoryCounts[it.id] ?: 0,
                    )
                }
            val page = shelvesWithCounts.take(limit)
            val next =
                if (shelvesWithCounts.size > limit) {
                    page.lastOrNull()?.let { CursorCodec.encode(it.name, it.id) }
                } else {
                    null
                }
            return PageResult(page, next)
        }

        @Transactional(readOnly = true)
        fun get(
            actor: Actor,
            shelfId: UUID,
        ): Shelf {
            val shelf = shelfMapper.findByIdAny(shelfId) ?: throw notFound("Shelf를 찾을 수 없습니다.")
            accessService.require(
                actor,
                accessService.target(shelf.spaceId, shelf.roomId, shelf.id),
                GrantLevel.VIEW,
            )
            return shelf.copy(
                boxCount = shelfMapper.countBoxes(shelf.spaceId, shelf.id),
                inventoryCount = countMapper.countInventoryByShelf(shelf.spaceId, shelf.id),
            )
        }

        @Transactional
        fun create(
            actor: Actor,
            roomId: UUID,
            command: CreateNodeCommand,
        ): Shelf {
            accessService.requireOwner(actor, actor.spaceId)
            requireRoomForUpdate(actor, roomId)
            val now = OffsetDateTime.now(clock)
            val shelf =
                Shelf(
                    id = UuidV7.generate(clock),
                    spaceId = actor.spaceId,
                    roomId = roomId,
                    name = normalizedName(command.name),
                    description = normalizedDescription(command.description),
                    version = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            shelfMapper.insert(shelf)
            return shelf
        }

        @Transactional
        fun update(
            actor: Actor,
            shelfId: UUID,
            command: UpdateNodeCommand,
        ): Shelf {
            accessService.requireOwner(actor, actor.spaceId)
            val current = requireShelf(actor, shelfId)
            if (current.version != command.version) throw versionConflict()
            val updated =
                shelfMapper.update(
                    spaceId = actor.spaceId,
                    id = shelfId,
                    name = command.name?.let(::normalizedName) ?: current.name,
                    description =
                        if (command.descriptionPresent) {
                            normalizedDescription(command.description)
                        } else {
                            current.description
                        },
                    version = command.version,
                    updatedAt = OffsetDateTime.now(clock),
                )
            if (updated == 0) throw versionConflict()
            return get(actor, shelfId)
        }

        @Transactional
        @Suppress("ThrowsCount")
        fun move(
            actor: Actor,
            shelfId: UUID,
            command: MoveShelfCommand,
        ): Shelf {
            accessService.requireOwner(actor, actor.spaceId)
            val current = requireShelf(actor, shelfId)
            if (current.version != command.version) throw versionConflict()
            locks.lockPaths(
                listOf(
                    locks.path(actor.spaceId, null, shelfId, null),
                    locks.path(actor.spaceId, command.roomId, null, null),
                ),
            )
            val locked = requireShelfForUpdate(actor, shelfId)
            if (locked.version != command.version) throw versionConflict()
            if (locked.roomId == command.roomId) return get(actor, shelfId)
            val updated =
                shelfMapper.move(
                    actor.spaceId,
                    shelfId,
                    command.roomId,
                    command.version,
                    OffsetDateTime.now(clock),
                )
            if (updated == 0) throw versionConflict()
            return get(actor, shelfId)
        }

        @Transactional
        fun delete(
            actor: Actor,
            shelfId: UUID,
        ) {
            accessService.requireOwner(actor, actor.spaceId)
            locks.lockPaths(listOf(locks.path(actor.spaceId, null, shelfId, null)))
            requireShelfForUpdate(actor, shelfId)
            deleteGuard.assertShelfEmpty(actor.spaceId, shelfId)
            shelfMapper.softDelete(actor.spaceId, shelfId, OffsetDateTime.now(clock))
        }

        private fun requireRoomForUpdate(
            actor: Actor,
            roomId: UUID,
        ) {
            roomMapper.findByIdForUpdate(actor.spaceId, roomId)
                ?: throw ApiException(
                    code = "RESOURCE_NOT_FOUND",
                    status = HttpStatus.NOT_FOUND,
                    message = "Room을 찾을 수 없습니다.",
                )
        }

        private fun requireShelf(
            actor: Actor,
            shelfId: UUID,
        ): Shelf =
            shelfMapper.findById(actor.spaceId, shelfId)
                ?: throw ApiException(
                    code = "RESOURCE_NOT_FOUND",
                    status = HttpStatus.NOT_FOUND,
                    message = "Shelf를 찾을 수 없습니다.",
                )

        private fun requireShelfForUpdate(
            actor: Actor,
            shelfId: UUID,
        ): Shelf =
            shelfMapper.findByIdForUpdate(actor.spaceId, shelfId)
                ?: throw ApiException(
                    code = "RESOURCE_NOT_FOUND",
                    status = HttpStatus.NOT_FOUND,
                    message = "Shelf를 찾을 수 없습니다.",
                )

        private fun normalizedName(value: String): String {
            val name = value.trim()
            if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
                throw ApiException(
                    code = "VALIDATION_FAILED",
                    status = HttpStatus.BAD_REQUEST,
                    message = "이름은 1자 이상 ${MAX_NAME_LENGTH}자 이하여야 합니다.",
                )
            }
            return name
        }

        private fun normalizedDescription(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)

        private fun notFound(message: String) = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message)

        private fun versionConflict(): ApiException =
            ApiException(
                code = "VERSION_CONFLICT",
                status = HttpStatus.CONFLICT,
                message = "다른 변경이 먼저 저장되었습니다. 최신 값을 다시 확인해 주세요.",
            )

        private companion object {
            const val MAX_NAME_LENGTH = 120
        }
    }
