@file:Suppress("LongParameterList", "TooManyFunctions", "MaxLineLength")

package com.agolock.media.persistence

import com.agolock.media.domain.Image
import com.agolock.media.domain.ImageStatus
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface ImageMapper {
    @Insert(
        """
        INSERT INTO image (
            id, space_id, item_id, inventory_id, object_key, declared_mime_type,
            declared_size_bytes, status, upload_expires_at, processing_attempts,
            created_by, created_at, updated_at
        ) VALUES (
            #{id}, #{spaceId}, #{itemId}, #{inventoryId}, #{objectKey}, #{contentType},
            #{sizeBytes}, CAST('UPLOADING' AS image_status), #{uploadExpiresAt}, 0,
            #{createdBy}, #{createdAt}, #{createdAt}
        )
        """,
    )
    fun insertUploading(
        @Param("id") id: UUID,
        @Param("spaceId") spaceId: UUID,
        @Param("itemId") itemId: UUID?,
        @Param("inventoryId") inventoryId: UUID?,
        @Param("objectKey") objectKey: String,
        @Param("contentType") contentType: String,
        @Param("sizeBytes") sizeBytes: Int,
        @Param("uploadExpiresAt") uploadExpiresAt: OffsetDateTime,
        @Param("createdBy") createdBy: UUID,
        @Param("createdAt") createdAt: OffsetDateTime,
    )

    @Select(
        """
        SELECT id, space_id, item_id, inventory_id, object_key, thumbnail_key,
               status, upload_expires_at, created_at, mime_type, size_bytes, is_primary
        FROM image
        WHERE id = #{id} AND deleted_at IS NULL
        """,
    )
    fun findById(
        @Param("id") id: UUID,
    ): Image?

    @Select(
        """
        SELECT id, space_id, item_id, inventory_id, object_key, thumbnail_key,
               status, upload_expires_at, created_at, mime_type, size_bytes, is_primary
        FROM image
        WHERE item_id = #{itemId} AND deleted_at IS NULL AND status <> 'DELETED'
        ORDER BY created_at DESC, id DESC
        """,
    )
    fun listByItem(
        @Param("itemId") itemId: UUID,
    ): List<Image>

    @Select(
        """
        SELECT id, space_id, item_id, inventory_id, object_key, thumbnail_key,
               status, upload_expires_at, created_at, mime_type, size_bytes, is_primary
        FROM image
        WHERE inventory_id = #{inventoryId} AND deleted_at IS NULL AND status <> 'DELETED'
        ORDER BY created_at DESC, id DESC
        """,
    )
    fun listByInventory(
        @Param("inventoryId") inventoryId: UUID,
    ): List<Image>

    @Update("UPDATE image SET is_primary = false, updated_at = #{now} WHERE item_id = #{itemId} AND deleted_at IS NULL")
    fun clearPrimaryByItem(
        @Param("itemId") itemId: UUID,
        @Param("now") now: OffsetDateTime,
    )

    @Update("UPDATE image SET is_primary = false, updated_at = #{now} WHERE inventory_id = #{inventoryId} AND deleted_at IS NULL")
    fun clearPrimaryByInventory(
        @Param("inventoryId") inventoryId: UUID,
        @Param("now") now: OffsetDateTime,
    )

    @Update("UPDATE image SET is_primary = true, updated_at = #{now} WHERE id = #{id} AND status = 'READY' AND deleted_at IS NULL")
    fun markPrimary(
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        "UPDATE image SET status = 'DELETED', is_primary = false, deleted_at = #{now}, updated_at = #{now} WHERE id = #{id} AND deleted_at IS NULL",
    )
    fun softDelete(
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Select(
        """
        SELECT id, space_id, item_id, inventory_id, object_key, thumbnail_key,
               status, upload_expires_at, created_at, mime_type, size_bytes, is_primary
        FROM image
        WHERE id = #{id} AND deleted_at IS NULL
        FOR UPDATE
        """,
    )
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): Image?

    @Select(
        """
        SELECT id, space_id, item_id, inventory_id, object_key, thumbnail_key,
               status, upload_expires_at, created_at, mime_type, size_bytes, is_primary
        FROM image
        WHERE object_key = #{objectKey} AND deleted_at IS NULL
        """,
    )
    fun findByObjectKey(
        @Param("objectKey") objectKey: String,
    ): Image?

    @Select(
        """
        <script>
        SELECT COUNT(*) FROM image
        WHERE deleted_at IS NULL
        <choose>
          <when test="itemId != null">AND item_id = #{itemId}</when>
          <when test="inventoryId != null">AND inventory_id = #{inventoryId}</when>
          <otherwise>AND 1 = 0</otherwise>
        </choose>
          AND (status IN ('PENDING', 'PROCESSING', 'READY')
               OR (status = 'UPLOADING' AND upload_expires_at &gt; #{now}))
        </script>
        """,
    )
    fun countActiveByTarget(
        @Param("itemId") itemId: UUID?,
        @Param("inventoryId") inventoryId: UUID?,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE image
        SET status = 'PROCESSING', processing_attempts = processing_attempts + 1, updated_at = #{now}
        WHERE id = #{id} AND status IN ('PENDING', 'FAILED') AND processing_attempts < 5
        """,
    )
    fun claimForProcessing(
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE image
        SET status = 'READY', mime_type = #{mimeType}, size_bytes = #{sizeBytes},
            width = #{width}, height = #{height}, sha256 = #{sha256},
            thumbnail_key = #{thumbnailKey}, processed_at = #{now}, updated_at = #{now}
        WHERE id = #{id} AND status = 'PROCESSING'
        """,
    )
    fun markReady(
        @Param("id") id: UUID,
        @Param("mimeType") mimeType: String,
        @Param("sizeBytes") sizeBytes: Long,
        @Param("width") width: Int,
        @Param("height") height: Int,
        @Param("sha256") sha256: String,
        @Param("thumbnailKey") thumbnailKey: String,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Select(
        """
        SELECT id, space_id, item_id, inventory_id, object_key, thumbnail_key,
               status, upload_expires_at, created_at, mime_type, size_bytes, is_primary
        FROM image
        WHERE deleted_at IS NULL
          AND status IN ('UPLOADING', 'DRAFT', 'FAILED')
          AND upload_expires_at <= #{now}
        ORDER BY upload_expires_at ASC
        LIMIT #{limit}
        """,
    )
    fun findExpiredUploads(
        @Param("now") now: OffsetDateTime,
        @Param("limit") limit: Int,
    ): List<Image>

    @Select(
        """
        SELECT id, space_id, item_id, inventory_id, object_key, thumbnail_key,
               status, upload_expires_at, created_at, mime_type, size_bytes, is_primary
        FROM image
        WHERE deleted_at IS NOT NULL
          AND status = 'DELETED'
          AND deleted_at <= #{deletedBefore}
        ORDER BY deleted_at ASC
        LIMIT #{limit}
        """,
    )
    fun findExpiredDeleted(
        @Param("deletedBefore") deletedBefore: OffsetDateTime,
        @Param("limit") limit: Int,
    ): List<Image>

    @Update(
        """
        UPDATE image
        SET status = 'FAILED', last_error = 'PROCESSING_TIMEOUT', updated_at = #{now}
        WHERE status = 'PROCESSING' AND updated_at < #{before} AND processing_attempts >= 5
        """,
    )
    fun failStuckProcessing(
        @Param("before") before: OffsetDateTime,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Select(
        value = [
            """
            WITH stale AS (
              SELECT id FROM image
              WHERE status = 'PROCESSING' AND deleted_at IS NULL
                AND updated_at < #{before} AND processing_attempts < 5
              ORDER BY updated_at, id LIMIT 100 FOR UPDATE SKIP LOCKED
            )
            UPDATE image SET status = 'PENDING', last_error = 'PROCESSING_TIMEOUT', updated_at = #{now}
            FROM stale WHERE image.id = stale.id
            RETURNING image.id, image.space_id, image.item_id, image.inventory_id,
                      image.object_key, image.thumbnail_key, image.status, image.upload_expires_at,
                      image.created_at, image.mime_type, image.size_bytes, image.is_primary
            """,
        ],
        affectData = true,
    )
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    fun requeueStuckProcessing(
        @Param("before") before: OffsetDateTime,
        @Param("now") now: OffsetDateTime,
    ): List<Image>

    @Delete("DELETE FROM image WHERE id = #{id}")
    fun deleteRow(
        @Param("id") id: UUID,
    ): Int

    @Update("UPDATE image SET status = 'FAILED', last_error = #{error}, updated_at = #{now} WHERE id = #{id} AND status = 'PROCESSING'")
    fun markProcessingFailed(
        @Param("id") id: UUID,
        @Param("error") error: String,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE image
        SET status = CAST(#{status} AS image_status), mime_type = #{mimeType},
            size_bytes = #{sizeBytes}, sha256 = #{sha256},
            upload_completed_at = #{now}, updated_at = #{now}
        WHERE id = #{id} AND status = 'UPLOADING'
        """,
    )
    fun completeUpload(
        @Param("id") id: UUID,
        @Param("status") status: ImageStatus,
        @Param("mimeType") mimeType: String,
        @Param("sizeBytes") sizeBytes: Long,
        @Param("sha256") sha256: String,
        @Param("now") now: OffsetDateTime,
    ): Int
}
