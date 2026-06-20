package com.seu.seustock.configuration;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    name = "seustock.image-storage.type",
    havingValue = "minio",
    matchIfMissing = true)
public class MinioConfig {

  @Bean
  public MinioClient minioClient(
      @Value("${seustock.minio.endpoint}") String endpoint,
      @Value("${seustock.minio.access-key}") String accessKey,
      @Value("${seustock.minio.secret-key}") String secretKey) {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }
}
