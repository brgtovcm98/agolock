@file:Suppress("LongParameterList", "ThrowsCount", "MaxLineLength", "MagicNumber", "TooManyFunctions", "CyclomaticComplexMethod")

package com.agolock.media.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.service.AccessService
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import com.agolock.inventory.service.InventoryLocationResolver
import com.agolock.media.api.CompleteImageRequest
import com.agolock.media.api.ImageResponse
import com.agolock.media.api.PresignImageRequest
import com.agolock.media.api.PresignImageResponse
import com.agolock.media.domain.Image
import com.agolock.media.domain.ImageStatus
import com.agolock.media.persistence.ImageMapper
import com.agolock.media.storage.ImageStoragePort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.HexFormat

@Service
class ImageService(
    private val imageMapper: ImageMapper,
    private val itemMapper: ItemMapper,
    private val inventoryMapper: InventoryMapper,
    private val locationResolver: InventoryLocationResolver,
    private val accessService: AccessService,
    private val storage: ImageStoragePort,
    private val uploadTransaction: ImageUploadTransaction,
    private val clock: Clock,
) {
    @Transactional
    fun presign(
        actor: Actor,
        request: PresignImageRequest,
    ): PresignImageResponse {
        val target =
            resolveMutationTarget(
                actor,
                request.targetType,
                request.targetId,
            )
        if (request.contentType != "image/webp") throw validation("이미지는 image/webp만 업로드할 수 있습니다.")
        if (request.sizeBytes !in 1..MAX_IMAGE_BYTES) throw validation("이미지 크기는 1~10MB여야 합니다.")
        val now = OffsetDateTime.now(clock)
        if (imageMapper.countActiveByTarget(target.itemId, target.inventoryId, now) >= MAX_IMAGES_PER_TARGET) {
            throw ApiException("IMAGE_LIMIT_EXCEEDED", HttpStatus.CONFLICT, "이미지는 대상별 10장까지 저장할 수 있습니다.")
        }
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

    fun complete(
        actor: Actor,
        imageId: java.util.UUID,
        request: CompleteImageRequest,
    ): ImageResponse {
        val image = imageMapper.findById(imageId) ?: throw notFound()
        val targetType = if (image.itemId != null) "ITEM" else "INVENTORY"
        val targetId = image.itemId ?: image.inventoryId ?: throw notFound()
        resolveMutationTarget(actor, targetType, targetId)
        if (image.objectKey != request.objectKey) throw validation("객체 키가 일치하지 않습니다.")
        if (image.status in COMPLETED_STATUSES) {
            return image.toResponse()
        }
        if (image.status != ImageStatus.UPLOADING) throw ApiException("IMAGE_INVALID_STATE", HttpStatus.CONFLICT, "업로드 대기 상태가 아닙니다.")
        if (!image.uploadExpiresAt.isAfter(OffsetDateTime.now(clock))) {
            throw ApiException("IMAGE_UPLOAD_EXPIRED", HttpStatus.CONFLICT, "업로드 시간이 만료되었습니다.")
        }
        val stored =
            storage.head(request.objectKey) ?: throw ApiException("IMAGE_OBJECT_NOT_FOUND", HttpStatus.NOT_FOUND, "업로드 객체를 찾을 수 없습니다.")
        if (stored.sizeBytes > MAX_IMAGE_BYTES) throw validation("이미지 크기는 10MB 이하여야 합니다.")
        val bytes = storage.getObject(request.objectKey)
        if (bytes.size.toLong() != stored.sizeBytes) throw validation("업로드 크기가 일치하지 않습니다.")
        if (bytes.size < 12 ||
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) != "RIFF" ||
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) != "WEBP"
        ) {
            throw validation("WebP 이미지 형식이 아닙니다.")
        }
        val sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val completed =
            uploadTransaction.complete(
                image.id,
                request.objectKey,
                stored.contentType ?: "image/webp",
                stored.sizeBytes,
                sha256,
                OffsetDateTime.now(clock),
            )
        return ImageResponse(
            completed.id,
            ImageStatus.PENDING,
            completed.itemId,
            completed.inventoryId,
            stored.contentType,
            stored.sizeBytes,
            false,
            completed.createdAt,
        )
    }

    fun listByItem(
        actor: Actor,
        itemId: java.util.UUID,
    ): List<ImageResponse> {
        resolveTarget(actor, "ITEM", itemId, GrantLevel.VIEW)
        return imageMapper.listByItem(itemId).map { it.toResponse() }
    }

    fun listByInventory(
        actor: Actor,
        inventoryId: java.util.UUID,
    ): List<ImageResponse> {
        resolveTarget(actor, "INVENTORY", inventoryId, GrantLevel.VIEW)
        return imageMapper.listByInventory(inventoryId).map { it.toResponse() }
    }

    fun url(
        actor: Actor,
        imageId: java.util.UUID,
    ): String {
        val image = imageMapper.findById(imageId) ?: throw notFound()
        val targetId = image.itemId ?: image.inventoryId ?: throw notFound()
        resolveTarget(actor, if (image.itemId != null) "ITEM" else "INVENTORY", targetId, GrantLevel.VIEW)
        if (image.status != ImageStatus.READY) throw ApiException("IMAGE_NOT_READY", HttpStatus.CONFLICT, "처리된 이미지가 아닙니다.")
        return storage.presignGet(image.objectKey, Duration.ofMinutes(5)).toString()
    }

    @Transactional
    fun primary(
        actor: Actor,
        imageId: java.util.UUID,
    ): ImageResponse {
        val image = imageMapper.findByIdForUpdate(imageId) ?: throw notFound()
        val targetId = image.itemId ?: image.inventoryId ?: throw notFound()
        resolveMutationTarget(
            actor,
            if (image.itemId != null) "ITEM" else "INVENTORY",
            targetId,
        )
        if (image.status != ImageStatus.READY) throw ApiException("IMAGE_NOT_READY", HttpStatus.CONFLICT, "처리된 이미지가 아닙니다.")
        val now = OffsetDateTime.now(clock)
        if (image.itemId !=
            null
        ) {
            imageMapper.clearPrimaryByItem(image.itemId, now)
        } else {
            imageMapper.clearPrimaryByInventory(image.inventoryId!!, now)
        }
        if (imageMapper.markPrimary(imageId, now) == 0) throw ApiException("IMAGE_NOT_READY", HttpStatus.CONFLICT, "대표 이미지로 설정할 수 없습니다.")
        return image.copy(isPrimary = true).toResponse()
    }

    @Transactional
    fun delete(
        actor: Actor,
        imageId: java.util.UUID,
    ) {
        val image = imageMapper.findByIdForUpdate(imageId) ?: throw notFound()
        val targetId = image.itemId ?: image.inventoryId ?: throw notFound()
        resolveMutationTarget(
            actor,
            if (image.itemId != null) "ITEM" else "INVENTORY",
            targetId,
        )
        imageMapper.softDelete(imageId, OffsetDateTime.now(clock))
    }

    private fun Image.toResponse() = ImageResponse(id, status, itemId, inventoryId, mimeType, sizeBytes, isPrimary, createdAt)

    private data class Target(
        val type: String,
        val id: java.util.UUID,
        val spaceId: java.util.UUID,
        val itemId: java.util.UUID?,
        val inventoryId: java.util.UUID?,
        val accessTarget: AccessTarget,
    )

    private fun resolveTarget(
        actor: Actor,
        targetType: String,
        targetId: java.util.UUID,
        minimum: GrantLevel,
    ): Target {
        val target = loadTarget(targetType, targetId)
        accessService.require(actor, target.accessTarget, minimum)
        return target
    }

    /**
     * 변경 작업의 권한을 적용합니다.
     * ITEM은 소유자만, INVENTORY는 EDIT 이상의 Grant를 요구합니다.
     */
    private fun resolveMutationTarget(
        actor: Actor,
        targetType: String,
        targetId: java.util.UUID,
    ): Target {
        val target = loadTarget(targetType, targetId)
        if (targetType == "ITEM") {
            accessService.requireOwner(actor, target.spaceId)
        } else {
            accessService.require(actor, target.accessTarget, GrantLevel.EDIT)
        }
        return target
    }

    private fun loadTarget(
        targetType: String,
        targetId: java.util.UUID,
    ): Target =
        when (targetType) {
            "ITEM" -> {
                val item = itemMapper.findByIdAny(targetId) ?: throw notFound()
                Target(
                    "item",
                    item.id,
                    item.spaceId,
                    item.id,
                    null,
                    accessService.target(item.spaceId),
                )
            }
            "INVENTORY" -> {
                val inventory = inventoryMapper.findByIdAny(targetId) ?: throw notFound()
                val location = locationResolver.resolve(inventory.id) ?: throw notFound()
                Target(
                    "inventory",
                    inventory.id,
                    location.spaceId,
                    null,
                    inventory.id,
                    accessService.target(
                        location.spaceId,
                        location.effectiveRoomId,
                        location.effectiveShelfId,
                        location.boxId,
                    ),
                )
            }
            else -> throw validation("targetType은 ITEM 또는 INVENTORY여야 합니다.")
        }

    private fun validation(message: String) = ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message)

    private fun notFound() = ApiException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.")

    private companion object {
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val MAX_IMAGES_PER_TARGET = 10
        val COMPLETED_STATUSES = setOf(ImageStatus.PENDING, ImageStatus.PROCESSING, ImageStatus.READY)
    }
}
