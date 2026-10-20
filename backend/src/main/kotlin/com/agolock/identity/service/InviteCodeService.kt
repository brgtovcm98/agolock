package com.agolock.identity.service

import com.agolock.common.error.ApiException
import com.agolock.common.id.UuidV7
import com.agolock.identity.domain.InviteCode
import com.agolock.identity.persistence.InviteCodeMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class InviteCodeService(
    private val inviteCodeMapper: InviteCodeMapper,
    private val tokenService: TokenService,
    private val clock: Clock,
) {
    @Transactional
    fun create(
        label: String?,
        maxUses: Int,
        expiresAt: OffsetDateTime?,
        createdBy: UUID?,
    ): CreatedInviteCode {
        if (maxUses < 1) {
            throw ApiException(
                code = "VALIDATION_FAILED",
                status = HttpStatus.BAD_REQUEST,
                message = "초대코드 사용 횟수는 1 이상이어야 합니다.",
            )
        }
        val rawCode = tokenService.createInviteCode()
        val now = OffsetDateTime.now(clock)
        val inviteCode =
            InviteCode(
                id = UuidV7.generate(clock),
                codeHash = tokenService.hashInviteCode(rawCode),
                label = label?.trim()?.takeIf(String::isNotEmpty),
                maxUses = maxUses,
                usedCount = 0,
                expiresAt = expiresAt,
                revokedAt = null,
                createdBy = createdBy,
                createdAt = now,
            )
        inviteCodeMapper.insert(inviteCode)
        return CreatedInviteCode(
            inviteCodeId = inviteCode.id,
            rawCode = rawCode,
            maxUses = maxUses,
            expiresAt = expiresAt,
        )
    }

    @Transactional
    fun revoke(inviteCodeId: UUID) {
        val updated = inviteCodeMapper.revoke(inviteCodeId, OffsetDateTime.now(clock))
        if (updated == 0) {
            throw ApiException(
                code = "RESOURCE_NOT_FOUND",
                status = HttpStatus.NOT_FOUND,
                message = "초대코드를 찾을 수 없거나 이미 철회되었습니다.",
            )
        }
    }

    fun list(): List<InviteCode> = inviteCodeMapper.findAll()
}
