package com.agolock.media.storage

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component("s3HealthIndicator")
@ConditionalOnEnabledHealthIndicator("s3")
class S3HealthIndicator(
    private val storage: ImageStoragePort,
) : HealthIndicator {
    override fun health(): Health =
        runCatching { storage.healthCheck() }
            .map { Health.up().build() }
            .getOrElse { Health.down().build() }
}
