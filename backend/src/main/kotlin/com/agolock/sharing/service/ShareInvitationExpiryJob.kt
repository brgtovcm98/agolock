package com.agolock.sharing.service

import com.agolock.sharing.persistence.ShareInvitationMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime

@Service
class ShareInvitationExpiryJob(
    private val invitationMapper: ShareInvitationMapper,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${agolock.share-invitation.expiry-interval-ms:300000}")
    fun expire() {
        val count = invitationMapper.expireStale(OffsetDateTime.now(clock))
        if (count > 0) log.info("만료 초대 정리 count={}", count)
    }
}
