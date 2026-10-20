@file:Suppress("LongParameterList")

package com.agolock.common.audit

import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface AuditLogMapper {
    @Insert(
        """
        INSERT INTO audit_log (id, space_id, actor_user_id, action, resource_type, resource_id, metadata, created_at)
        VALUES (#{id}, #{spaceId}, #{actorUserId}, #{action}, #{resourceType}, #{resourceId}, CAST(#{metadata} AS jsonb), #{createdAt})
        """,
    )
    fun insert(
        @Param("id") id: UUID,
        @Param("spaceId") spaceId: UUID,
        @Param("actorUserId") actorUserId: UUID,
        @Param("action") action: String,
        @Param("resourceType") resourceType: String,
        @Param("resourceId") resourceId: UUID?,
        @Param("metadata") metadata: String,
        @Param("createdAt") createdAt: OffsetDateTime,
    )
}
