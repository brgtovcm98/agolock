@file:Suppress("MaxLineLength")

package com.agolock.inventory.api

import com.agolock.common.json.JsonPatchReaders
import com.agolock.hierarchy.domain.Permission
import com.agolock.inventory.domain.Category
import com.agolock.inventory.domain.InventoryMovement
import com.agolock.inventory.domain.InventoryStatus
import com.agolock.inventory.domain.Item
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class CreateCategoryRequest(
    @field:NotBlank @field:Size(max = 80) val name: String,
    @field:Size(max = 32) val color: String? = null,
    @field:Min(0) val sortOrder: Int = 0,
)

@JsonDeserialize(using = UpdateCategoryRequestDeserializer::class)
class UpdateCategoryRequest {
    @field:Size(min = 1, max = 80)
    var name: String? = null

    @field:Size(max = 32)
    var color: String? = null

    @field:Min(0)
    var sortOrder: Int? = null

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var colorPresent: Boolean = false
}

class UpdateCategoryRequestDeserializer : StdDeserializer<UpdateCategoryRequest>(UpdateCategoryRequest::class.java) {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): UpdateCategoryRequest {
        val node = parser.readValueAsTree<JsonNode>()
        if (!node.isObject) throw JsonMappingException.from(parser, "PATCH는 객체여야 합니다.")
        return UpdateCategoryRequest().also { request ->
            if (node.has("name")) request.name = JsonPatchReaders.text(parser, node.get("name"), "name")
            if (node.has("color")) {
                request.color = JsonPatchReaders.optionalText(parser, node.get("color"), "color")
                request.colorPresent = true
            }
            if (node.has("sortOrder")) request.sortOrder = parseIntField(parser, node.get("sortOrder"), "sortOrder")
        }
    }
}

@JsonInclude(JsonInclude.Include.ALWAYS)
data class CategoryResponse(
    val id: UUID,
    val name: String,
    val color: String?,
    val sortOrder: Int,
)

data class CreateItemRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:Size(max = 2000) val description: String? = null,
    val categoryId: UUID? = null,
    @field:Min(0) val priceAmount: Int? = null,
)

@JsonDeserialize(using = UpdateItemRequestDeserializer::class)
class UpdateItemRequest {
    @field:Size(min = 1, max = 120)
    var name: String? = null
        private set

    @field:Size(max = 2000)
    var description: String? = null
        private set

    var categoryId: UUID? = null
        private set

    @field:Min(0)
    var priceAmount: Int? = null
        private set

    @field:NotNull
    @field:Min(0)
    var version: Long? = null

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var descriptionPresent: Boolean = false
        private set

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var categoryIdPresent: Boolean = false
        private set

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var priceAmountPresent: Boolean = false
        private set

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var namePresent: Boolean = false
        private set

    fun applyName(value: String?) {
        name = value
        namePresent = true
    }

    fun applyDescription(value: String?) {
        description = value
        descriptionPresent = true
    }

    fun applyCategoryId(value: UUID?) {
        categoryId = value
        categoryIdPresent = true
    }

    fun applyPriceAmount(value: Int?) {
        priceAmount = value
        priceAmountPresent = true
    }
}

private fun parseUuid(
    parser: JsonParser,
    value: String,
    field: String,
): UUID =
    try {
        UUID.fromString(value)
    } catch (exception: IllegalArgumentException) {
        throw JsonMappingException.from(parser, "$field 형식이 올바르지 않습니다.", exception)
    }

private fun parseStatus(
    parser: JsonParser,
    value: String,
): InventoryStatus =
    try {
        InventoryStatus.valueOf(value)
    } catch (exception: IllegalArgumentException) {
        throw JsonMappingException.from(parser, "status 값이 올바르지 않습니다.", exception)
    }

private fun parseIntField(
    parser: JsonParser,
    value: JsonNode,
    field: String,
): Int {
    if (!value.isIntegralNumber || !value.canConvertToInt()) {
        throw JsonMappingException.from(parser, "$field 값은 정수여야 합니다.")
    }
    return value.intValue()
}

private fun parseVersion(
    parser: JsonParser,
    node: JsonNode,
): Long? {
    val value = node.get("version")?.takeUnless(JsonNode::isNull) ?: return null
    if (!value.isIntegralNumber || !value.canConvertToLong()) {
        throw JsonMappingException.from(parser, "version은 정수여야 합니다.")
    }
    return value.longValue()
}

class UpdateItemRequestDeserializer : StdDeserializer<UpdateItemRequest>(UpdateItemRequest::class.java) {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): UpdateItemRequest {
        val node = parser.readValueAsTree<JsonNode>()
        if (!node.isObject) throw JsonMappingException.from(parser, "PATCH는 객체여야 합니다.")
        val request = UpdateItemRequest()
        if (node.has("name")) {
            request.applyName(JsonPatchReaders.text(parser, node.get("name"), "name"))
        }
        if (node.has("description")) {
            request.applyDescription(JsonPatchReaders.optionalText(parser, node.get("description"), "description"))
        }
        if (node.has("categoryId")) {
            request.applyCategoryId(
                node
                    .get("categoryId")
                    .takeUnless(JsonNode::isNull)
                    ?.asText()
                    ?.let { parseUuid(parser, it, "categoryId") },
            )
        }
        if (node.has("priceAmount")) {
            request.applyPriceAmount(
                node
                    .get("priceAmount")
                    .takeUnless(JsonNode::isNull)
                    ?.let { parseIntField(parser, it, "priceAmount") },
            )
        }
        request.version = parseVersion(parser, node)
        return request
    }
}

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ItemResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val categoryId: UUID?,
    val priceAmount: Int?,
    val currency: String,
    val version: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val effectivePermission: Permission = Permission.OWNER,
    val isShared: Boolean = false,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class InventoryLocationResponse(
    val type: String,
    val roomId: UUID?,
    val shelfId: UUID?,
    val boxId: UUID?,
    val name: String,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class InventoryResponse(
    val id: UUID,
    val itemId: UUID,
    val itemName: String,
    val displayName: String,
    val description: String?,
    val categoryId: UUID?,
    val priceAmount: Int?,
    val status: InventoryStatus,
    val location: InventoryLocationResponse,
    val nameOverride: String?,
    val descriptionOverride: String?,
    val categoryIdOverride: UUID?,
    val priceAmountOverride: Int?,
    val version: Long,
    val effectivePermission: Permission,
    val isShared: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class NewItemInput(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:Size(max = 2000) val description: String? = null,
    val categoryId: UUID? = null,
    @field:Min(0) val priceAmount: Int? = null,
)

data class CreateInventoryRequest(
    val itemId: UUID? = null,
    @field:Valid val newItem: NewItemInput? = null,
    val roomId: UUID? = null,
    val shelfId: UUID? = null,
    val boxId: UUID? = null,
    val status: InventoryStatus = InventoryStatus.STORED,
)

@JsonDeserialize(using = UpdateInventoryRequestDeserializer::class)
class UpdateInventoryRequest {
    @field:Size(max = 120)
    var nameOverride: String? = null
        private set

    @field:Size(max = 2000)
    var descriptionOverride: String? = null
        private set

    var categoryIdOverride: UUID? = null
        private set

    @field:Min(0)
    var priceAmountOverride: Int? = null
        private set

    var status: InventoryStatus? = null

    var confirmDisposal: Boolean = false

    @field:NotNull
    @field:Min(0)
    var version: Long? = null

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var nameOverridePresent: Boolean = false
        private set

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var descriptionOverridePresent: Boolean = false
        private set

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var categoryIdOverridePresent: Boolean = false
        private set

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var priceAmountOverridePresent: Boolean = false
        private set

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var statusPresent: Boolean = false
        private set

    fun applyStatus(value: InventoryStatus?) {
        status = value
        statusPresent = true
    }

    fun applyNameOverride(value: String?) {
        nameOverride = value
        nameOverridePresent = true
    }

    fun applyDescriptionOverride(value: String?) {
        descriptionOverride = value
        descriptionOverridePresent = true
    }

    fun applyCategoryIdOverride(value: UUID?) {
        categoryIdOverride = value
        categoryIdOverridePresent = true
    }

    fun applyPriceAmountOverride(value: Int?) {
        priceAmountOverride = value
        priceAmountOverridePresent = true
    }
}

class UpdateInventoryRequestDeserializer : StdDeserializer<UpdateInventoryRequest>(UpdateInventoryRequest::class.java) {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): UpdateInventoryRequest {
        val node = parser.readValueAsTree<JsonNode>()
        if (!node.isObject) throw JsonMappingException.from(parser, "PATCH는 객체여야 합니다.")
        val request = UpdateInventoryRequest()
        if (node.has("nameOverride")) {
            request.applyNameOverride(JsonPatchReaders.optionalText(parser, node.get("nameOverride"), "nameOverride"))
        }
        if (node.has("descriptionOverride")) {
            request.applyDescriptionOverride(
                JsonPatchReaders.optionalText(parser, node.get("descriptionOverride"), "descriptionOverride"),
            )
        }
        if (node.has("categoryIdOverride")) {
            request.applyCategoryIdOverride(
                node
                    .get("categoryIdOverride")
                    .takeUnless(JsonNode::isNull)
                    ?.asText()
                    ?.let { parseUuid(parser, it, "categoryIdOverride") },
            )
        }
        if (node.has("priceAmountOverride")) {
            request.applyPriceAmountOverride(
                node
                    .get("priceAmountOverride")
                    .takeUnless(JsonNode::isNull)
                    ?.let { parseIntField(parser, it, "priceAmountOverride") },
            )
        }
        if (node.has("status")) {
            request.applyStatus(
                node
                    .get("status")
                    .takeUnless(JsonNode::isNull)
                    ?.asText()
                    ?.let { parseStatus(parser, it) },
            )
        }
        if (node.has("confirmDisposal")) {
            request.confirmDisposal = JsonPatchReaders.boolean(parser, node.get("confirmDisposal"), "confirmDisposal")
        }
        request.version = parseVersion(parser, node)
        return request
    }
}

data class MoveInventoryRequest(
    val roomId: UUID? = null,
    val shelfId: UUID? = null,
    val boxId: UUID? = null,
    @field:NotNull @field:Min(0) val version: Long? = null,
)

data class MovementResponse(
    val id: UUID,
    val inventoryId: UUID,
    val from: InventoryLocationResponse,
    val to: InventoryLocationResponse,
    val movedAt: OffsetDateTime,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class InventoryListResponse(
    val items: List<InventoryResponse>,
    val nextCursor: String? = null,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ItemListResponse(
    val items: List<ItemResponse>,
    val nextCursor: String? = null,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class CategoryListResponse(
    val items: List<CategoryResponse>,
    val nextCursor: String? = null,
)

fun Category.toResponse(): CategoryResponse = CategoryResponse(id, name, color, sortOrder)

fun Item.toResponse(
    permission: Permission = Permission.OWNER,
    isShared: Boolean = false,
): ItemResponse =
    ItemResponse(id, name, description, categoryId, priceAmount, currency, version, createdAt, updatedAt, permission, isShared)

fun InventoryMovement.toResponse(locationLoader: (UUID?, UUID?, UUID?) -> InventoryLocationResponse): MovementResponse =
    MovementResponse(
        id = id,
        inventoryId = inventoryId,
        from = locationLoader(fromRoomId, fromShelfId, fromBoxId),
        to = locationLoader(toRoomId, toShelfId, toBoxId),
        movedAt = movedAt,
    )

data class LocationItemResponse(
    val type: String,
    val id: UUID,
    val path: String,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class LocationListResponse(
    val items: List<LocationItemResponse>,
    val nextCursor: String? = null,
)

data class MergeCategoryRequest(
    @field:NotNull val targetCategoryId: UUID? = null,
)

data class CategoryMergeResponse(
    val mergedItemCount: Int,
    val mergedInventoryCount: Int,
)

data class ItemUpdateImpactResponse(
    val affectedInventoryCount: Int,
    val protectedByOverrideCount: Int,
)
