package com.agolock.media.storage

import java.net.URI
import java.time.Duration

interface ImageStoragePort {
    fun presignPut(
        objectKey: String,
        contentType: String,
        ttl: Duration,
    ): URI

    fun presignGet(
        objectKey: String,
        ttl: Duration,
    ): URI

    fun head(objectKey: String): StoredObject?

    fun delete(objectKey: String)
}

data class StoredObject(
    val sizeBytes: Long,
    val contentType: String?,
)
