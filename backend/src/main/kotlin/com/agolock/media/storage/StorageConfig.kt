package com.agolock.media.storage

import com.agolock.common.config.AppProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class StorageConfig {
    @Bean
    fun s3Client(properties: AppProperties): S3Client =
        S3Client
            .builder()
            .endpointOverride(URI.create(properties.storage.endpoint))
            .region(Region.of(properties.storage.region))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .httpClient(UrlConnectionHttpClient.create())
            .build()

    @Bean
    fun s3Presigner(properties: AppProperties): S3Presigner =
        S3Presigner
            .builder()
            .endpointOverride(URI.create(properties.storage.publicEndpoint.ifBlank { properties.storage.endpoint }))
            .region(Region.of(properties.storage.region))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()

    private fun credentials(properties: AppProperties) =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.storage.accessKey, properties.storage.secretKey),
        )
}
