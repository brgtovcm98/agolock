package com.agolock.hierarchy.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Box
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.domain.PageResult
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.inventory.persistence.InventoryMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Suppress("TooManyFunctions")
class BoxService(
    private val boxMapper: BoxMapper,
    private val shelfMapper: ShelfMapper,
    private val accessService: AccessService,
    private val inventoryMapper: InventoryMapper,
    private val deleteGuard: HierarchyDeleteGuard,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun list(
        actor: Actor,
        shelfId: UUID,
        cursorRaw: String?,
        limitRaw: Int?,
    ): PageResult<Box> {
        val shelf = shelfMapper.findByIdAny(shelfId) ?: throw notFound("Shelf를 찾을 수 없습니다.")
        accessService.require(
            actor,
            accessService.target(shelf.spaceId, shelf.roomId, shelf.id),
            GrantLevel.VIEW,
        )
        val cursor = CursorCodec.decode(cursorRaw)
        val limit = CursorCodec.limit(limitRaw)
        val boxes =
            boxMapper
                .listByShelf(shelf.spaceId, shelfId, cursor?.name, cursor?.id, limit + 1)
                .map { it.copy(inventoryCount = inventoryMapper.countByBox(shelf.spaceId, it.id)) }
        val page = boxes.take(limit)
        val next =
            if (boxes.size > limit) {
                page.lastOrNull()?.let { CursorCodec.encode(it.name, it.id) }
            } else {
                null
            }
        return PageResult(page, next)
    }

    @Transactional(readOnly = true)
    fun roomIdOfShelf(shelfId: UUID): UUID? = shelfMapper.findByIdAny(shelfId)?.roomId

    @Transactional(readOnly = true)
    fun get(
        actor: Actor,
        boxId: UUID,
    ): Box = getDetail(actor, boxId).box

    @Transactional(readOnly = true)
    fun getDetail(
        actor: Actor,
        boxId: UUID,
    ): BoxDetail {
        val box = boxMapper.findByIdAny(boxId) ?: throw notFound("Box를 찾을 수 없습니다.")
        val shelf = shelfMapper.findById(box.spaceId, box.shelfId)
        val permission =
            accessService.require(
                actor,
                accessService.target(box.spaceId, shelf?.roomId, box.shelfId, box.id),
                GrantLevel.VIEW,
            )
        return BoxDetail(
            box.copy(inventoryCount = inventoryMapper.countByBox(box.spaceId, box.id)),
            permission,
        )
    }

    @Transactional
    fun create(
        actor: Actor,
        shelfId: UUID,
        command: CreateNodeCommand,
    ): Box {
        accessService.requireOwner(actor, actor.spaceId)
        requireShelfForUpdate(actor, shelfId)
        val now = OffsetDateTime.now(clock)
        val box =
            Box(
                id = UuidV7.generate(clock),
                spaceId = actor.spaceId,
                shelfId = shelfId,
                name = normalizedName(command.name),
                description = normalizedDescription(command.description),
                version = 0,
                createdAt = now,
                updatedAt = now,
            )
        boxMapper.insert(box)
        return box
    }

    @Transactional
    fun update(
        actor: Actor,
        boxId: UUID,
        command: UpdateNodeCommand,
    ): Box {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireBox(actor, boxId)
        if (current.version != command.version) throw versionConflict()
        val updated =
            boxMapper.update(
                spaceId = actor.spaceId,
                id = boxId,
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
        return get(actor, boxId)
    }

    @Transactional
    @Suppress("ThrowsCount")
    fun move(
        actor: Actor,
        boxId: UUID,
        command: MoveBoxCommand,
    ): Box {
        accessService.requireOwner(actor, actor.spaceId)
        val current = requireBox(actor, boxId)
        if (current.version != command.version) throw versionConflict()
        requireShelfForUpdate(actor, command.shelfId)
        val locked = requireBoxForUpdate(actor, boxId)
        if (locked.version != command.version) throw versionConflict()
        if (locked.shelfId == command.shelfId) return get(actor, boxId)
        val updated = boxMapper.move(actor.spaceId, boxId, command.shelfId, command.version, OffsetDateTime.now(clock))
        if (updated == 0) throw versionConflict()
        return get(actor, boxId)
    }

    @Transactional
    fun delete(
        actor: Actor,
        boxId: UUID,
    ) {
        accessService.requireOwner(actor, actor.spaceId)
        requireBoxForUpdate(actor, boxId)
        deleteGuard.assertBoxEmpty(actor.spaceId, boxId)
        boxMapper.softDelete(actor.spaceId, boxId, OffsetDateTime.now(clock))
    }

    private fun requireShelfForUpdate(
        actor: Actor,
        shelfId: UUID,
    ) {
        shelfMapper.findByIdForUpdate(actor.spaceId, shelfId)
            ?: throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "Shelf를 찾을 수 없습니다.",
            )
    }

    private fun requireBoxForUpdate(
        actor: Actor,
        boxId: UUID,
    ): Box =
        boxMapper.findByIdForUpdate(actor.spaceId, boxId)
            ?: throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "Box를 찾을 수 없습니다.",
            )

    private fun requireBox(
        actor: Actor,
        boxId: UUID,
    ): Box =
        boxMapper.findById(actor.spaceId, boxId)
            ?: throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "Box를 찾을 수 없습니다.",
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

data class BoxDetail(
    val box: Box,
    val permission: EffectivePermission,
)
