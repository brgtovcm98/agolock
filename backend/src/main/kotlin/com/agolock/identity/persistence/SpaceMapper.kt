package com.agolock.identity.persistence

import com.agolock.identity.domain.Space
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface SpaceMapper {
    @Select(
        """
        SELECT id, owner_user_id, name, created_at, updated_at, deleted_at
        FROM space
        WHERE owner_user_id = #{ownerUserId} AND deleted_at IS NULL
        """,
    )
    fun findByOwnerUserId(
        @Param("ownerUserId") ownerUserId: UUID,
    ): Space?

    @Select(
        """
        SELECT id, owner_user_id, name, created_at, updated_at, deleted_at
        FROM space
        WHERE id = #{id} AND deleted_at IS NULL
        """,
    )
    fun findById(
        @Param("id") id: UUID,
    ): Space?

    @Insert(
        """
        INSERT INTO space (id, owner_user_id, name, created_at, updated_at, deleted_at)
        VALUES (#{id}, #{ownerUserId}, #{name}, #{createdAt}, #{updatedAt}, #{deletedAt})
        """,
    )
    fun insert(space: Space)

    @Update(
        """
        UPDATE space
        SET name = #{name}, updated_at = #{updatedAt}
        WHERE id = #{id} AND deleted_at IS NULL
        """,
    )
    fun updateName(
        @Param("id") id: UUID,
        @Param("name") name: String,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int
}
