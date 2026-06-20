package com.seu.seustock.service;

import com.seu.seustock.mapper.ImageMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.UserDTO;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(
    name = "seustock.image-storage.type",
    havingValue = "minio",
    matchIfMissing = true)
public class MinioImageStorageService extends AbstractImageStorageService {

  private final MinioClient minioClient;

  @Value("${seustock.minio.bucket:seustock-images}")
  private String bucketName;

  private volatile boolean bucketReady;

  public MinioImageStorageService(
      ImageMapper imageMapper,
      UserMapper userMapper,
      ImageFileValidator imageFileValidator,
      MinioClient minioClient) {
    super(imageMapper, userMapper, imageFileValidator);
    this.minioClient = minioClient;
  }

  @Override
  protected String writeToPhysicalStorage(
      MultipartFile file,
      UserDTO owner,
      String contentType,
      String originalFilename,
      String extension)
      throws Exception {
    String objectKey = "users/%d/%s%s".formatted(owner.getId(), UUID.randomUUID(), extension);
    try (InputStream inputStream = file.getInputStream()) {
      ensureBucket();
      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(bucketName)
              .object(objectKey)
              .contentType(contentType)
              .stream(inputStream, file.getSize(), -1L)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("이미지 파일을 MinIO에 저장할 수 없습니다.", e);
    }
    return objectKey;
  }

  @Override
  public Resource load(ImageDTO image) {
    try {
      InputStream stream =
          minioClient.getObject(
              GetObjectArgs.builder().bucket(bucketName).object(image.getStoragePath()).build());
      return new InputStreamResource(stream);
    } catch (Exception e) {
      throw new NoSuchElementException("이미지 파일을 MinIO에서 찾을 수 없습니다.");
    }
  }

  private void ensureBucket() throws Exception {
    if (bucketReady) {
      return;
    }
    synchronized (this) {
      if (bucketReady) {
        return;
      }
      boolean exists =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
      }
      bucketReady = true;
    }
  }
}
