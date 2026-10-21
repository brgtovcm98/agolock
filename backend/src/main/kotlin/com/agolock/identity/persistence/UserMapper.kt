package com.agolock.identity.persistence

import com.agolock.identity.domain.AppUser
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import java.time.OffsetDateTime
import java.util.UUID

@Mapper
interface UserMapper {
    @Select(
        """
        SELECT id, email, password_hash, display_name, status::text AS status,
               created_at, updated_at, deleted_at, email_verified_at
        FROM app_user
        WHERE email = #{email} AND deleted_at IS NULL
        """,
    )
    fun findActiveByEmail(
        @Param("email") email: String,
    ): AppUser?

    @Select(
        """
        SELECT id, email, password_hash, display_name, status::text AS status,
               created_at, updated_at, deleted_at, email_verified_at
        FROM app_user
        WHERE id = #{id}
        """,
    )
    fun findById(
        @Param("id") id: UUID,
    ): AppUser?

    @Insert(
        """
        INSERT INTO app_user (
            id, email, password_hash, display_name, status,
            created_at, updated_at, deleted_at, email_verified_at
        ) VALUES (
            #{id}, #{email}, #{passwordHash}, #{displayName},
            CAST(#{status} AS user_status), #{createdAt}, #{updatedAt}, #{deletedAt}, #{emailVerifiedAt}
        )
        """,
    )
    fun insert(user: AppUser)

    @Update(
        """
        UPDATE app_user
        SET display_name = #{displayName}, updated_at = #{updatedAt}
        WHERE id = #{id} AND deleted_at IS NULL
        """,
    )
    fun updateDisplayName(
        @Param("id") id: UUID,
        @Param("displayName") displayName: String,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE app_user
        SET email_verified_at = #{now}, updated_at = #{now}
        WHERE id = #{id} AND deleted_at IS NULL
        """,
    )
    fun markEmailVerified(
        @Param("id") id: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    @Update(
        """
        UPDATE app_user
        SET password_hash = #{passwordHash}, updated_at = #{updatedAt}
        WHERE id = #{id} AND deleted_at IS NULL
        """,
    )
    fun updatePassword(
        @Param("id") id: UUID,
        @Param("passwordHash") passwordHash: String,
        @Param("updatedAt") updatedAt: OffsetDateTime,
    ): Int
}
