package com.seu.seustock.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageFileValidator {

  private static final int SIGNATURE_BYTES = 12;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
  private static final Map<String, String> SIGNATURE_CONTENT_TYPES =
      Map.of(
          "jpeg", "image/jpeg",
          "png", "image/png",
          "webp", "image/webp",
          "gif", "image/gif");

  private final MessageSource messageSource;

  public ImageFileValidator(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  public String validateAndNormalizeContentType(MultipartFile file) {
    String declaredContentType = normalize(file.getContentType());
    if (declaredContentType == null || !ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
      throw invalidImageFormat();
    }

    String detectedContentType = detectContentType(file);
    if (!declaredContentType.equals(detectedContentType)) {
      throw invalidImageFormat();
    }
    return declaredContentType;
  }

  private String normalize(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    return contentType.toLowerCase(Locale.ROOT);
  }

  private String detectContentType(MultipartFile file) {
    byte[] header = new byte[SIGNATURE_BYTES];
    int bytesRead;
    try (InputStream inputStream = file.getInputStream()) {
      bytesRead = inputStream.readNBytes(header, 0, header.length);
    } catch (IOException e) {
      throw new IllegalStateException(
          messageSource.getMessage("error.image.readFailed", null, LocaleContextHolder.getLocale()),
          e);
    }

    String signature = detectSignature(header, bytesRead);
    if (signature == null) {
      throw invalidImageFormat();
    }
    String contentType = SIGNATURE_CONTENT_TYPES.get(signature);
    if (contentType == null) {
      throw invalidImageFormat();
    }
    return contentType;
  }

  private String detectSignature(byte[] header, int bytesRead) {
    if (bytesRead >= 3
        && (header[0] & 0xFF) == 0xFF
        && (header[1] & 0xFF) == 0xD8
        && (header[2] & 0xFF) == 0xFF) {
      return "jpeg";
    }
    if (bytesRead >= 8
        && (header[0] & 0xFF) == 0x89
        && header[1] == 0x50
        && header[2] == 0x4E
        && header[3] == 0x47
        && header[4] == 0x0D
        && header[5] == 0x0A
        && header[6] == 0x1A
        && header[7] == 0x0A) {
      return "png";
    }
    if (bytesRead >= 6
        && header[0] == 0x47
        && header[1] == 0x49
        && header[2] == 0x46
        && header[3] == 0x38
        && (header[4] == 0x37 || header[4] == 0x39)
        && header[5] == 0x61) {
      return "gif";
    }
    if (bytesRead >= 12
        && header[0] == 0x52
        && header[1] == 0x49
        && header[2] == 0x46
        && header[3] == 0x46
        && header[8] == 0x57
        && header[9] == 0x45
        && header[10] == 0x42
        && header[11] == 0x50) {
      return "webp";
    }
    return null;
  }

  private IllegalArgumentException invalidImageFormat() {
    return new IllegalArgumentException(
        messageSource.getMessage(
            "error.image.invalidFormat", null, LocaleContextHolder.getLocale()));
  }
}
