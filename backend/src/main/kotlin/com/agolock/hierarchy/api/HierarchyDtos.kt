package com.agolock.hierarchy.api

import com.agolock.hierarchy.domain.Box
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.domain.Room
import com.agolock.hierarchy.domain.Shelf
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class CreateNodeRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 120)
    val name: String,
    @field:Size(max = 2000)
    val description: String? = null,
)

@JsonDeserialize(using = UpdateNodeRequestDeserializer::class)
class UpdateNodeRequest {
    @field:Size(min = 1, max = 120)
    var name: String? = null

    @field:Size(max = 2000)
    var description: String? = null
        private set

    @field:NotNull
    var version: Long? = null

    @get:JsonIgnore
    @get:Schema(hidden = true)
    var descriptionPresent: Boolean = false
        private set

    fun applyDescription(value: String?) {
        description = value
        descriptionPresent = true
    }
}

class UpdateNodeRequestDeserializer : StdDeserializer<UpdateNodeRequest>(UpdateNodeRequest::class.java) {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): UpdateNodeRequest {
        val node = parser.readValueAsTree<JsonNode>()
        val request = UpdateNodeRequest()
        node
            .get("name")
            ?.takeUnless(JsonNode::isNull)
            ?.asText()
            ?.let { request.name = it }
        if (node.has("description")) {
            request.applyDescription(node.get("description").takeUnless(JsonNode::isNull)?.asText())
        }
        request.version = node.get("version")?.takeUnless(JsonNode::isNull)?.asLong()
        return request
    }
}

data class MoveShelfRequest(
    @field:NotNull
    val roomId: UUID,
    @field:NotNull
    val version: Long,
)

data class MoveBoxRequest(
    @field:NotNull
    val shelfId: UUID,
    @field:NotNull
    val version: Long,
)

data class RoomResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val shelfCount: Int,
    val version: Long,
    val effectivePermission: Permission,
    val isShared: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class ShelfResponse(
    val id: UUID,
    val roomId: UUID,
    val name: String,
    val description: String?,
    val boxCount: Int,
    val version: Long,
    val effectivePermission: Permission,
    val isShared: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class BoxResponse(
    val id: UUID,
    val shelfId: UUID,
    val name: String,
    val description: String?,
    val version: Long,
    val effectivePermission: Permission,
    val isShared: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class RoomListResponse(
    val items: List<RoomResponse>,
    val nextCursor: String?,
)

data class ShelfListResponse(
    val items: List<ShelfResponse>,
    val nextCursor: String?,
)

data class BoxListResponse(
    val items: List<BoxResponse>,
    val nextCursor: String?,
)

fun Room.toResponse(
    permission: Permission,
    isShared: Boolean,
): RoomResponse =
    RoomResponse(
        id = id,
        name = name,
        description = description,
        shelfCount = shelfCount,
        version = version,
        effectivePermission = permission,
        isShared = isShared,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Shelf.toResponse(
    permission: Permission,
    isShared: Boolean,
): ShelfResponse =
    ShelfResponse(
        id = id,
        roomId = roomId,
        name = name,
        description = description,
        boxCount = boxCount,
        version = version,
        effectivePermission = permission,
        isShared = isShared,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Box.toResponse(
    permission: Permission,
    isShared: Boolean,
): BoxResponse =
    BoxResponse(
        id = id,
        shelfId = shelfId,
        name = name,
        description = description,
        version = version,
        effectivePermission = permission,
        isShared = isShared,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
