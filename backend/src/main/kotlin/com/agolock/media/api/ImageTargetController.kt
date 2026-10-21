@file:Suppress("MaxLineLength", "MagicNumber")

package com.agolock.media.api

import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.media.service.ImageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "images")
class ImageTargetController(
    private val imageService: ImageService,
) {
    @GetMapping("/items/{itemId}/images")
    @Operation(operationId = "listItemImages", summary = "Item 이미지 목록")
    fun itemImages(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable itemId: UUID,
    ): ImageListResponse = ImageListResponse(imageService.listByItem(Actor.from(principal), itemId))

    @GetMapping("/inventories/{inventoryId}/images")
    @Operation(operationId = "listInventoryImages", summary = "Inventory 이미지 목록")
    fun inventoryImages(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable inventoryId: UUID,
    ): ImageListResponse = ImageListResponse(imageService.listByInventory(Actor.from(principal), inventoryId))

    @GetMapping("/images/{imageId}/url")
    @Operation(operationId = "getImageUrl", summary = "이미지 다운로드 URL")
    fun url(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable imageId: UUID,
    ): ImageUrlResponse = ImageUrlResponse(imageService.url(Actor.from(principal), imageId), OffsetDateTime.now().plusMinutes(5))

    @PostMapping("/images/{imageId}/primary")
    @Operation(operationId = "setPrimaryImage", summary = "대표 이미지 지정")
    fun primary(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable imageId: UUID,
    ): ImageResponse = imageService.primary(Actor.from(principal), imageId)

    @DeleteMapping("/images/{imageId}")
    @Operation(operationId = "deleteImage", summary = "이미지 삭제")
    fun delete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable imageId: UUID,
    ): ResponseEntity<Void> {
        imageService.delete(Actor.from(principal), imageId)
        return ResponseEntity.noContent().build()
    }
}
