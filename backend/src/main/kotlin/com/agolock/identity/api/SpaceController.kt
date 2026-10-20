package com.agolock.identity.api

import com.agolock.identity.security.AppUserPrincipal
import com.agolock.identity.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/spaces")
@Tag(name = "auth")
class SpaceController(
    private val authService: AuthService,
) {
    @PatchMapping("/{spaceId}")
    @Operation(operationId = "updateSpace", summary = "Space 이름 수정")
    fun updateSpace(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable spaceId: UUID,
        @Valid @RequestBody request: UpdateSpaceRequest,
    ): SpaceResponse =
        authService
            .updateSpace(principal.userId, spaceId, request.name)
            .let { SpaceResponse(id = it.id, name = it.name, ownerUserId = it.ownerUserId) }
}
