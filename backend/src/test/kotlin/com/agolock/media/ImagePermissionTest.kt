package com.agolock.media

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.persistence.BoxMapper
import com.agolock.hierarchy.persistence.ShelfMapper
import com.agolock.hierarchy.service.AccessService
import com.agolock.hierarchy.service.NoGrantLookup
import com.agolock.hierarchy.service.SpaceOwnership
import com.agolock.inventory.domain.Item
import com.agolock.inventory.persistence.InventoryMapper
import com.agolock.inventory.persistence.ItemMapper
import com.agolock.media.api.PresignImageRequest
import com.agolock.media.persistence.ImageMapper
import com.agolock.media.service.ImageService
import com.agolock.media.storage.ImageStoragePort
import com.agolock.media.storage.StoredObject
import com.agolock.messaging.outbox.OutboxService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ImagePermissionTest {
    @Test
    fun `Item 이미지 presign은 OWNER만 허용한다`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val spaceId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val item =
            Item(
                id = itemId,
                spaceId = spaceId,
                name = "물건",
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        val itemMapper = mock(ItemMapper::class.java)
        `when`(itemMapper.findByIdAny(itemId)).thenReturn(item)
        val actor = Actor(userId, spaceId)

        val ownerService = service(itemMapper, userId)
        val response =
            ownerService.presign(
                actor,
                PresignImageRequest("ITEM", itemId, "image/webp", 100),
            )
        assertThat(response.uploadUrl).isEqualTo("https://example.com/upload")

        val nonOwnerService = service(itemMapper, otherUserId)
        assertThatThrownBy {
            nonOwnerService.presign(
                actor,
                PresignImageRequest("ITEM", itemId, "image/webp", 100),
            )
        }.isInstanceOf(ApiException::class.java)
    }

    private fun service(
        itemMapper: ItemMapper,
        ownerUserId: UUID,
    ): ImageService {
        val access =
            AccessService(
                object : SpaceOwnership {
                    override fun ownerOf(spaceId: UUID): UUID = ownerUserId
                },
                NoGrantLookup(),
            )
        return ImageService(
            mock(ImageMapper::class.java),
            itemMapper,
            mock(InventoryMapper::class.java),
            mock(ShelfMapper::class.java),
            mock(BoxMapper::class.java),
            access,
            storage(),
            mock(OutboxService::class.java),
            Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC),
        )
    }

    private fun storage(): ImageStoragePort =
        object : ImageStoragePort {
            override fun healthCheck() = Unit

            override fun presignPut(
                objectKey: String,
                contentType: String,
                ttl: Duration,
            ): URI = URI.create("https://example.com/upload")

            override fun presignGet(
                objectKey: String,
                ttl: Duration,
            ): URI = URI.create("https://example.com/download")

            override fun head(objectKey: String): StoredObject? = null

            override fun getObject(objectKey: String): ByteArray = ByteArray(0)

            override fun putObject(
                objectKey: String,
                bytes: ByteArray,
                contentType: String,
            ) = Unit

            override fun delete(objectKey: String) = Unit
        }
}
