@file:Suppress("MaxLineLength")

package com.agolock.inventory.api

import com.agolock.common.idempotency.IdempotencyService
import com.agolock.hierarchy.api.toResponseEntity
import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.inventory.service.CategoryService
import com.agolock.inventory.service.CreateCategoryCommand
import com.agolock.inventory.service.UpdateCategoryCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "categories")
class CategoryController(
    private val categoryService: CategoryService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping
    @Operation(operationId = "listCategories", summary = "Category 목록")
    fun list(
        @AuthenticationPrincipal principal: AppUserPrincipal,
    ): CategoryListResponse = CategoryListResponse(categoryService.list(Actor.from(principal)).map { it.toResponse() })

    @PostMapping
    @Operation(operationId = "createCategory", summary = "Category 생성")
    fun create(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreateCategoryRequest,
    ): ResponseEntity<Any> {
        val actor = Actor.from(principal)
        val result =
            idempotencyService.execute(
                userId = actor.userId,
                key = idempotencyKey,
                method = "POST",
                path = "/api/v1/categories",
                request = request,
                responseType = CategoryResponse::class.java,
                locationOf = { category -> "/api/v1/categories/${category.id}" },
            ) {
                categoryService
                    .create(actor, CreateCategoryCommand(request.name, request.color, request.sortOrder))
                    .toResponse()
            }
        return result.toResponseEntity()
    }

    @PatchMapping("/{categoryId}")
    @Operation(operationId = "updateCategory", summary = "Category 수정")
    fun update(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable categoryId: UUID,
        @Valid @RequestBody request: UpdateCategoryRequest,
    ): CategoryResponse =
        categoryService
            .update(Actor.from(principal), categoryId, UpdateCategoryCommand(request.name, request.color, request.sortOrder))
            .toResponse()

    @PostMapping("/{categoryId}/merge")
    @Operation(operationId = "mergeCategory", summary = "Category 병합")
    fun merge(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable categoryId: UUID,
        @Valid @RequestBody request: MergeCategoryRequest,
    ): CategoryMergeResponse {
        val (items, inventories) =
            categoryService.merge(Actor.from(principal), categoryId, request.targetCategoryId!!)
        return CategoryMergeResponse(items, inventories)
    }

    @DeleteMapping("/{categoryId}")
    @Operation(operationId = "deleteCategory", summary = "Category 삭제")
    fun delete(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @PathVariable categoryId: UUID,
    ): ResponseEntity<Void> {
        categoryService.delete(Actor.from(principal), categoryId)
        return ResponseEntity.noContent().build()
    }
}
