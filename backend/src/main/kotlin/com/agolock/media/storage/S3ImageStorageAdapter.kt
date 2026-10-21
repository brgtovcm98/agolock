@file:Suppress("SwallowedException")

package com.agolock.media.storage

import com.agolock.common.config.AppProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

@Component
class S3ImageStorageAdapter(
    private val s3Client: S3Client,
    private val presigner: S3Presigner,
    private val properties: AppProperties,
) : ImageStoragePort {
    override fun presignPut(
        objectKey: String,
        contentType: String,
        ttl: Duration,
    ): URI {
        val request =
            PutObjectRequest
                .builder()
                .bucket(properties.storage.bucket)
                .key(objectKey)
                .contentType(contentType)
                .build()
        return presigner
            .presignPutObject(
                PutObjectPresignRequest
                    .builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(request)
                    .build(),
            ).url()
            .toURI()
    }

    override fun head(objectKey: String): StoredObject? =
        try {
            val response =
                s3Client.headObject(
                    HeadObjectRequest
                        .builder()
                        .bucket(properties.storage.bucket)
                        .key(objectKey)
                        .build(),
                )
            StoredObject(response.contentLength(), response.contentType())
        } catch (exception: NoSuchKeyException) {
            null
        }

    override fun delete(objectKey: String) {
        s3Client.deleteObject(
            DeleteObjectRequest
                .builder()
                .bucket(properties.storage.bucket)
                .key(objectKey)
                .build(),
        )
    }
}
