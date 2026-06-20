package com.seu.seustock.service;

import com.seu.seustock.mapper.ImageMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public abstract class AbstractImageStorageService implements ImageStorageService {

  private static final Logger log = LoggerFactory.getLogger(AbstractImageStorageService.class);

  protected final ImageMapper imageMapper;
  protected final UserMapper userMapper;
  private final ImageFileValidator imageFileValidator;

  protected AbstractImageStorageService(
      ImageMapper imageMapper, UserMapper userMapper, ImageFileValidator imageFileValidator) {
    this.imageMapper = imageMapper;
    this.userMapper = userMapper;
    this.imageFileValidator = imageFileValidator;
  }

  @Override
  public ImageDTO loadForUser(UUID externalId, String username) {
    UserDTO user =
        userMapper
            .findByEmail(username)
            .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
    ImageDTO image =
        imageMapper
            .findByExternalId(externalId)
            .orElseThrow(() -> new NoSuchElementException("이미지를 찾을 수 없습니다."));
    if (!image.getUserId().equals(user.getId())) {
      throw new SecurityException("접근 권한이 없습니다.");
    }
    return image;
  }

  @Override
  public ImageDTO store(MultipartFile file, UserDTO owner, String contentHash) {
    if (file == null || file.isEmpty()) {
      return null;
    }

    String contentType = imageFileValidator.validateAndNormalizeContentType(file);
    String normalizedHash = (contentHash != null && !contentHash.isBlank()) ? contentHash : null;

    if (normalizedHash != null) {
      Optional<ImageDTO> existing =
          imageMapper.findByUserIdAndContentHash(owner.getId(), normalizedHash);
      if (existing.isPresent()) {
        log.info(
            "image reused userId={} imageId={} contentType={} sizeBytes={}",
            owner.getId(),
            existing.get().getId(),
            existing.get().getContentType(),
            existing.get().getSizeBytes());
        return existing.get();
      }
    }

    String originalFilename =
        StringUtils.cleanPath(
            file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
    String extension = extensionOf(originalFilename);

    String storagePath;
    try {
      storagePath = writeToPhysicalStorage(file, owner, contentType, originalFilename, extension);
    } catch (RuntimeException e) {
      log.error(
          "image storage failed userId={} contentType={} sizeBytes={}",
          owner.getId(),
          contentType,
          file.getSize(),
          e);
      throw e;
    } catch (Exception e) {
      log.error(
          "image storage failed userId={} contentType={} sizeBytes={}",
          owner.getId(),
          contentType,
          file.getSize(),
          e);
      throw new IllegalStateException("이미지 파일을 저장할 수 없습니다.", e);
    }

    ImageDTO image = new ImageDTO();
    image.setUserId(owner.getId());
    image.setStoragePath(storagePath);
    image.setOriginalFilename(originalFilename);
    image.setContentType(contentType);
    image.setSizeBytes(file.getSize());
    image.setContentHash(normalizedHash);
    try {
      imageMapper.insertImage(image);
    } catch (DataIntegrityViolationException e) {
      if (normalizedHash != null) {
        ImageDTO existing =
            imageMapper
                .findByUserIdAndContentHash(owner.getId(), normalizedHash)
                .orElseThrow(() -> e);
        log.warn(
            "image duplicate detected userId={} imageId={} contentType={} sizeBytes={}",
            owner.getId(),
            existing.getId(),
            existing.getContentType(),
            existing.getSizeBytes());
        return existing;
      }
      throw e;
    }
    ImageDTO stored = imageMapper.findById(image.getId()).orElseThrow();
    log.info(
        "image stored userId={} imageId={} contentType={} sizeBytes={}",
        owner.getId(),
        stored.getId(),
        stored.getContentType(),
        stored.getSizeBytes());
    return stored;
  }

  protected String extensionOf(String filename) {
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == filename.length() - 1) {
      return "";
    }
    return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
  }

  /** 물리 저장소에 파일을 실제로 전송/저장하고 고유 저장 경로(key 또는 filename)를 반환하는 추상 메소드 */
  protected abstract String writeToPhysicalStorage(
      MultipartFile file,
      UserDTO owner,
      String contentType,
      String originalFilename,
      String extension)
      throws Exception;
}
