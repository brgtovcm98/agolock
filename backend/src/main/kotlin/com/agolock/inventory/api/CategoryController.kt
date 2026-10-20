@file:Suppress("MaxLineLength")

package com.agolock.inventory.api

import com.agolock.hierarchy.domain.Actor
import com.agolock.identity.security.AppUserPrincipal
import com.agolock.inventory.service.CategoryService
import com.agolock.inventory.service.CreateCategoryCommand
import com.agolock.inventory.service.UpdateCategoryCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "categories")
class CategoryController(
    private val categoryService: CategoryService,
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
        @Valid @RequestBody request: CreateCategoryRequest,
    ): ResponseEntity<CategoryResponse> {
        val category =
            categoryService.create(
                Actor.from(principal),
                CreateCategoryCommand(request.name, request.color, request.sortOrder),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(category.toResponse())
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
