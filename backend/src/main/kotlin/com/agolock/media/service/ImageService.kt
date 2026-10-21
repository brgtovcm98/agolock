@file:Suppress("LongParameterList", "ThrowsCount", "MaxLineLength", "MagicNumber")

package com.agolock.media.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import com.agolock.media.api.CompleteImageRequest
import com.agolock.media.api.ImageResponse
import com.agolock.media.api.PresignImageRequest
import com.agolock.media.api.PresignImageResponse
import com.agolock.media.domain.ImageStatus
import com.agolock.media.persistence.ImageMapper
import com.agolock.media.storage.ImageStoragePort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime

@Service
class ImageService(
    private val imageMapper: ImageMapper,
    private val itemMapper: ItemMapper,
    private val inventoryMapper: InventoryMapper,
    private val shelfMapper: ShelfMapper,
    private val boxMapper: BoxMapper,
    private val accessService: AccessService,
    private val storage: ImageStoragePort,
    private val clock: Clock,
) {
    @Transactional
    fun presign(
        actor: Actor,
        request: PresignImageRequest,
    ): PresignImageResponse {
        val target = resolveTarget(actor, request.targetType, request.targetId, Permission.EDIT)
        if (request.contentType != "image/webp") throw validation("이미지는 image/webp만 업로드할 수 있습니다.")
        if (request.sizeBytes !in 1..MAX_IMAGE_BYTES) throw validation("이미지 크기는 1~10MB여야 합니다.")
        if (imageMapper.countActiveByTarget(target.itemId, target.inventoryId) >= MAX_IMAGES_PER_TARGET) {
            throw ApiException("IMAGE_LIMIT_EXCEEDED", HttpStatus.CONFLICT, "이미지는 대상별 10장까지 저장할 수 있습니다.")
        }
        val now = OffsetDateTime.now(clock)
        val imageId = UuidV7.generate(clock)
        val objectKey = "spaces/${target.spaceId}/${target.type}/${target.id}/$imageId/original.webp"
        val uploadExpiresAt = now.plusHours(24)
        imageMapper.insertUploading(
            imageId,
            target.spaceId,
            target.itemId,
            target.inventoryId,
            objectKey,
            request.contentType,
            request.sizeBytes,
            uploadExpiresAt,
            actor.userId,
            now,
        )
        val url = storage.presignPut(objectKey, request.contentType, Duration.ofMinutes(5))
        return PresignImageResponse(imageId, url.toString(), objectKey, now.plusMinutes(5), uploadExpiresAt)
    }

    @Transactional
    fun complete(
        actor: Actor,
        imageId: java.util.UUID,
        request: CompleteImageRequest,
    ): ImageResponse {
        val image = imageMapper.findByIdForUpdate(imageId) ?: throw notFound()
        val targetType = if (image.itemId != null) "ITEM" else "INVENTORY"
        val targetId = image.itemId ?: image.inventoryId ?: throw notFound()
        resolveTarget(actor, targetType, targetId, Permission.EDIT)
        if (image.objectKey != request.objectKey) throw validation("객체 키가 일치하지 않습니다.")
        if (image.status != ImageStatus.UPLOADING) throw ApiException("IMAGE_INVALID_STATE", HttpStatus.CONFLICT, "업로드 대기 상태가 아닙니다.")
        if (!image.uploadExpiresAt.isAfter(OffsetDateTime.now(clock))) {
            throw ApiException("IMAGE_UPLOAD_EXPIRED", HttpStatus.CONFLICT, "업로드 시간이 만료되었습니다.")
        }
        val stored =
            storage.head(request.objectKey) ?: throw ApiException("IMAGE_OBJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "업로드 객체를 찾을 수 없습니다.")
        if (stored.sizeBytes > MAX_IMAGE_BYTES) throw validation("이미지 크기는 10MB 이하여야 합니다.")
        val now = OffsetDateTime.now(clock)
        if (imageMapper.completeUpload(image.id, ImageStatus.PENDING, stored.contentType ?: "image/webp", stored.sizeBytes, now) == 0) {
            throw ApiException("IMAGE_INVALID_STATE", HttpStatus.CONFLICT, "이미지 상태를 변경할 수 없습니다.")
        }
        return ImageResponse(
            image.id,
            ImageStatus.PENDING,
            image.itemId,
            image.inventoryId,
            stored.contentType,
            stored.sizeBytes,
            image.createdAt,
        )
    }

    private data class Target(
        val type: String,
        val id: java.util.UUID,
        val spaceId: java.util.UUID,
        val itemId: java.util.UUID?,
        val inventoryId: java.util.UUID?,
    )

    private fun resolveTarget(
        actor: Actor,
        targetType: String,
        targetId: java.util.UUID,
        minimum: Permission,
    ): Target =
        when (targetType) {
            "ITEM" -> {
                val item = itemMapper.findByIdAny(targetId) ?: throw notFound()
                accessService.require(actor, accessService.target(item.spaceId), minimum)
                Target("item", item.id, item.spaceId, item.id, null)
            }
            "INVENTORY" -> {
                val inventory = inventoryMapper.findByIdAny(targetId) ?: throw notFound()
                val shelf = inventory.shelfId?.let { shelfMapper.findByIdAny(it) }
                val boxShelf = inventory.boxId?.let { boxId -> boxMapper.findByIdAny(boxId)?.let { shelfMapper.findByIdAny(it.shelfId) } }
                val roomId = inventory.roomId ?: shelf?.roomId ?: boxShelf?.roomId
                accessService.require(
                    actor,
                    accessService.target(inventory.spaceId, roomId, inventory.shelfId, inventory.boxId),
                    minimum,
                )
                Target("inventory", inventory.id, inventory.spaceId, null, inventory.id)
            }
            else -> throw validation("targetType은 ITEM 또는 INVENTORY여야 합니다.")
        }

    private fun validation(message: String) = ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message)

    private fun notFound() = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.")

    private companion object {
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val MAX_IMAGES_PER_TARGET = 10
    }
}
