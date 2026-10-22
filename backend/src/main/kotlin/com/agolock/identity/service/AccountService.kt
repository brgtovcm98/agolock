package com.agolock.identity.service

import com.agolock.common.error.ApiException
import com.agolock.identity.domain.Space
import com.agolock.identity.domain.UserStatus
import com.agolock.identity.persistence.SpaceMapper
import com.agolock.identity.persistence.UserMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AccountService(
    private val userMapper: UserMapper,
    private val spaceMapper: SpaceMapper,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun me(userId: UUID): AuthResult {
        val user =
            userMapper
                .findById(userId)
                ?.takeIf { it.deletedAt == null && it.status == UserStatus.ACTIVE }
                ?: throw unauthenticated()
        val space =
            spaceMapper.findByOwnerUserId(user.id)
                ?: throw unauthenticated()
        return AuthResult(user, space)
    }

    @Transactional
    fun updateDisplayName(
        userId: UUID,
        displayName: String,
    ): AuthResult {
        val normalized = displayName.trim()
        ProfilePolicy.validateDisplayName(normalized)
        val updated = userMapper.updateDisplayName(userId, normalized, OffsetDateTime.now(clock))
        if (updated == 0) {
            throw unauthenticated()
        }
        return me(userId)
    }

    @Transactional
    fun updateSpace(
        userId: UUID,
        spaceId: UUID,
        name: String,
    ): Space {
        val normalized = name.trim()
        ProfilePolicy.validateSpaceName(normalized)
        val space = spaceMapper.findById(spaceId)
        if (space == null || space.ownerUserId != userId) {
            throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "Space를 찾을 수 없습니다.",
            )
        }
        val now = OffsetDateTime.now(clock)
        spaceMapper.updateName(spaceId, normalized, now)
        return space.copy(name = normalized, updatedAt = now)
    }

    private fun unauthenticated(): ApiException =
        ApiException(
            code = "AUTHENTICATION_REQUIRED",
            status = HttpStatus.UNAUTHORIZED,
            message = "로그인이 필요합니다.",
        )
}
