package com.agolock.media

import com.agolock.common.config.AppProperties
import com.agolock.media.storage.StorageConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

class StorageConfigTest {
    @Test
    fun `presigned URL은 public endpoint를 사용한다`() {
        val properties =
            AppProperties().apply {
                storage.endpoint = "http://minio:9000"
                storage.publicEndpoint = "https://s3.staging.example.com"
                storage.bucket = "agolock-staging"
                storage.accessKey = "key"
                storage.secretKey = "secret"
            }
        val presigner = StorageConfig().s3Presigner(properties)

        val url =
            presigner
                .presignPutObject(
                    PutObjectPresignRequest
                        .builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest { request ->
                            request.bucket("agolock-staging").key("spaces/s/item/i/original.webp")
                        }.build(),
                ).url()

        assertThat(url.host).isEqualTo("s3.staging.example.com")
    }
}
