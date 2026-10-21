package com.agolock.media

import com.agolock.common.config.AppProperties
import com.agolock.media.storage.S3ImageStorageAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.time.Duration

class S3ImageStorageAdapterTest {
    @Test
    fun `presign URL은 bucket과 object key를 포함한다`() {
        val properties = AppProperties()
        properties.storage.endpoint = "http://localhost:9000"
        properties.storage.bucket = "agolock-test"
        properties.storage.accessKey = "test"
        properties.storage.secretKey = "test"
        properties.storage.region = "us-east-1"
        val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
        val client =
            S3Client
                .builder()
                .endpointOverride(URI.create(properties.storage.endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()
        val presigner =
            S3Presigner
                .builder()
                .endpointOverride(URI.create(properties.storage.endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()
        try {
            val adapter = S3ImageStorageAdapter(client, presigner, properties)
            val url = adapter.presignPut("spaces/s/item/i/original.webp", "image/webp", Duration.ofMinutes(5))
            assertThat(url.toString()).contains("agolock-test", "spaces/s/item/i/original.webp", "X-Amz-Signature")
        } finally {
            client.close()
            presigner.close()
        }
    }
}
