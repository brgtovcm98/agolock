package com.agolock.media

import com.agolock.common.config.AppProperties
import com.agolock.media.storage.S3ImageStorageAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

@Testcontainers
class S3ImageStorageAdapterIT {
    @Test
    fun `presigned PUT으로 업로드한 객체를 조회하고 삭제한다`() {
        val properties = AppProperties()
        properties.storage.endpoint = "http://${MINIO.host}:${MINIO.getMappedPort(9000)}"
        properties.storage.bucket = "agolock-test"
        properties.storage.accessKey = "agolock"
        properties.storage.secretKey = "agolock-local-secret"
        properties.storage.region = "us-east-1"
        val credentials =
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.storage.accessKey, properties.storage.secretKey),
            )
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
            client.createBucket(CreateBucketRequest.builder().bucket(properties.storage.bucket).build())
            val adapter = S3ImageStorageAdapter(client, presigner, properties)
            val key = "spaces/${UUID.randomUUID()}/original.webp"
            val body = "RIFF0000WEBP".toByteArray()
            val url = adapter.presignPut(key, "image/webp", Duration.ofMinutes(5))
            val response =
                HttpClient.newHttpClient().send(
                    HttpRequest
                        .newBuilder(url)
                        .header("Content-Type", "image/webp")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertThat(response.statusCode()).isEqualTo(200)
            val stored = adapter.head(key)
            assertThat(stored).isNotNull
            assertThat(stored!!.sizeBytes).isEqualTo(body.size.toLong())
            assertThat(adapter.getObject(key)).isEqualTo(body)
            adapter.delete(key)
            assertThat(adapter.head(key)).isNull()
        } finally {
            client.close()
            presigner.close()
        }
    }

    companion object {
        @Container
        @JvmStatic
        val MINIO: GenericContainer<*> =
            GenericContainer("bitnamilegacy/minio:2025.7.23-debian-12-r5")
                .withEnv("MINIO_ROOT_USER", "agolock")
                .withEnv("MINIO_ROOT_PASSWORD", "agolock-local-secret")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000))
    }
}
