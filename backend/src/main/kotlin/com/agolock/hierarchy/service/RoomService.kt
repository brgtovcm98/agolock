package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.PageResult
import com.agolock.hierarchy.domain.Room
import com.agolock.hierarchy.persistence.RoomMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class RoomService(
    private val roomMapper: RoomMapper,
    private val accessService: AccessService,
    private val deleteGuard: HierarchyDeleteGuard,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(
        actor: Actor,
        cursorRaw: String?,
        limitRaw: Int?,
    ): PageResult<Room> {
        accessService.requireView(actor, actor.spaceId)
        val cursor = CursorCodec.decode(cursorRaw)
        val limit = CursorCodec.limit(limitRaw)
        val rooms =
            roomMapper
                .list(actor.spaceId, cursor?.name, cursor?.id, limit + 1)
                .map { it.copy(shelfCount = roomMapper.countShelves(actor.spaceId, it.id)) }
        val page = rooms.take(limit)
        val next =
            if (rooms.size > limit) {
                page.lastOrNull()?.let { CursorCodec.encode(it.name, it.id) }
            } else {
                null
            }
        return PageResult(page, next)
    }

    @Transactional(readOnly = true)
    fun get(
        actor: Actor,
        roomId: UUID,
    ): Room {
        accessService.requireView(actor, actor.spaceId)
        val room = requireRoom(actor, roomId)
        return room.copy(shelfCount = roomMapper.countShelves(actor.spaceId, room.id))
    }

    @Transactional
    fun create(
        actor: Actor,
        command: CreateNodeCommand,
    ): Room {
        accessService.requireOwner(actor, actor.spaceId)
        val normalized = normalizedName(command.name)
        val now = OffsetDateTime.now(clock)
        val room =
            Room(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                name = normalized,
                description = normalizedDescription(command.description),
                version = 0,
                createdAt = now,
                updatedAt = now,
            )
        roomMapper.insert(room)
        return room
    }

    @Transactional
    fun update(
        actor: Actor,
        roomId: UUID,
        command: UpdateNodeCommand,
    ): Room {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireRoom(actor, roomId)
        if (current.version != command.version) throw versionConflict()
        val updated =
            roomMapper.update(
                spaceId = actor.spaceId,
                id = roomId,
                name = command.name?.let(::normalizedName) ?: current.name,
                description = command.description,
                version = command.version,
                updatedAt = OffsetDateTime.now(clock),
            )
        if (updated == 0) throw versionConflict()
        return get(actor, roomId)
    }

    @Transactional
    fun delete(
        actor: Actor,
        roomId: UUID,
    ) {
        accessService.requireOwner(actor, actor.spaceId)
        requireRoom(actor, roomId)
        deleteGuard.assertRoomEmpty(actor.spaceId, roomId)
        roomMapper.softDelete(actor.spaceId, roomId, OffsetDateTime.now(clock))
    }

    private fun requireRoom(
        actor: Actor,
        roomId: UUID,
    ): Room =
        roomMapper.findById(actor.spaceId, roomId)
            ?: throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "Room을 찾을 수 없습니다.",
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
