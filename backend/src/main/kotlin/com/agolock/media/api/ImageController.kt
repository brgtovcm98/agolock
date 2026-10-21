package com.agolock.media.api

import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.media.service.ImageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "images")
class ImageController(
    private val imageService: ImageService,
) {
    @PostMapping("/presign")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "presignImage", summary = "이미지 업로드 URL 발급")
    fun presign(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: PresignImageRequest,
    ): PresignImageResponse = imageService.presign(Actor.from(principal), request)

    @PostMapping("/{imageId}/complete")
    @Operation(operationId = "completeImage", summary = "이미지 업로드 완료")
    fun complete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable imageId: UUID,
        @Valid @RequestBody request: CompleteImageRequest,
    ): ImageResponse = imageService.complete(Actor.from(principal), imageId, request)
}
