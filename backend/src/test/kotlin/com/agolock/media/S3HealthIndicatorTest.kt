package com.agolock.media

import com.agolock.media.storage.ImageStoragePort
import com.agolock.media.storage.S3HealthIndicator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doThrow
import org.springframework.boot.actuate.health.Status

class S3HealthIndicatorTest {
    @Test
    fun `S3 health가 성공하면 UP이다`() {
        val storage = mock(ImageStoragePort::class.java)
        val indicator = S3HealthIndicator(storage)

        assertThat(indicator.health().status).isEqualTo(Status.UP)
    }

    @Test
    fun `S3 health가 실패하면 DOWN이다`() {
        val storage = mock(ImageStoragePort::class.java)
        doThrow(IllegalStateException("s3 down")).`when`(storage).healthCheck()

        assertThat(S3HealthIndicator(storage).health().status).isEqualTo(Status.DOWN)
    }
}
