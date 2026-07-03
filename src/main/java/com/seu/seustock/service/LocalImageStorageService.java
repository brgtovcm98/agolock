package com.seu.seustock.service;

import com.seu.seustock.mapper.ImageMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "seustock.image-storage.type", havingValue = "local")
public class LocalImageStorageService extends AbstractImageStorageService {

  @Value("${seustock.upload-dir:uploads/images}")
  private String uploadDir;

  public LocalImageStorageService(
      ImageMapper imageMapper,
      UserMapper userMapper,
      ImageFileValidator imageFileValidator,
      MessageSource messageSource) {
    super(imageMapper, userMapper, imageFileValidator, messageSource);
  }

  @Override
  protected String writeToPhysicalStorage(
      MultipartFile file,
      UserDTO owner,
      String contentType,
      String originalFilename,
      String extension)
      throws Exception {
    String storedFilename = UUID.randomUUID() + extension;
    Path uploadPath = Path.of(uploadDir).toAbsolutePath().normalize();
    Path storedPath = uploadPath.resolve(storedFilename).normalize();

    if (!storedPath.startsWith(uploadPath)) {
      throw new IllegalArgumentException(getMessage("error.image.invalidPath"));
    }

    try {
      Files.createDirectories(uploadPath);
      file.transferTo(storedPath);
    } catch (IOException e) {
      throw new IllegalStateException(getMessage("error.image.saveFailed"), e);
    }

    return storedFilename;
  }

  @Override
  public Resource load(ImageDTO image) {
    Path uploadPath = Path.of(uploadDir).toAbsolutePath().normalize();
    Path path = uploadPath.resolve(image.getStoragePath()).normalize();
    if (!path.startsWith(uploadPath)) {
      throw new IllegalArgumentException(getMessage("error.image.invalidPath"));
    }
    Resource resource = new FileSystemResource(path);
    if (!resource.exists() || !resource.isReadable()) {
      throw new NoSuchElementException(getMessage("error.image.notFound"));
    }
    return resource;
  }
}
