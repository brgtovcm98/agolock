package com.agolock.messaging.outbox

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@Profile("worker")
class NotificationSecretCleanupJob(
    private val notificationSecretService: NotificationSecretService,
) {
    @Scheduled(fixedDelayString = "\${agolock.notification.cleanup-interval-ms:3600000}")
    fun cleanup() {
        notificationSecretService.deleteExpired()
    }
}
