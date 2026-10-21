@file:Suppress("LongParameterList")

package com.agolock.media.persistence

import com.agolock.media.domain.Image
import com.agolock.media.domain.ImageStatus
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
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

    @Select("SELECT * FROM image WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): Image?

    @Select("SELECT * FROM image WHERE object_key = #{objectKey} AND deleted_at IS NULL")
    fun findByObjectKey(
        @Param("objectKey") objectKey: String,
    ): Image?

    @Select(
        """
        SELECT COUNT(*) FROM image
        WHERE deleted_at IS NULL AND status <> 'DELETED'
          AND ((item_id = #{itemId} AND #{itemId} IS NOT NULL) OR (inventory_id = #{inventoryId} AND #{inventoryId} IS NOT NULL))
        """,
    )
    fun countActiveByTarget(
        @Param("itemId") itemId: UUID?,
        @Param("inventoryId") inventoryId: UUID?,
    ): Int

    @Update(
        """
        UPDATE image
        SET status = CAST(#{status} AS image_status), mime_type = #{mimeType},
            size_bytes = #{sizeBytes}, upload_completed_at = #{now}, updated_at = #{now}
        WHERE id = #{id} AND status = 'UPLOADING'
        """,
    )
    fun completeUpload(
        @Param("id") id: UUID,
        @Param("status") status: ImageStatus,
        @Param("mimeType") mimeType: String,
        @Param("sizeBytes") sizeBytes: Long,
        @Param("now") now: OffsetDateTime,
    ): Int
}
