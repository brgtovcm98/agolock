package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;

class ImageFileValidatorTest {

  private final ImageFileValidator validator = new ImageFileValidator();

  @ParameterizedTest(name = "{1}")
  @MethodSource("validImages")
  void validateAndNormalizeContentType_acceptsMatchingSignatures(byte[] bytes, String contentType) {
    MockMultipartFile file = new MockMultipartFile("imageFile", "image", contentType, bytes);

    String result = validator.validateAndNormalizeContentType(file);

    assertThat(result).isEqualTo(contentType);
  }

  @Test
  void validateAndNormalizeContentType_normalizesDeclaredContentType() {
    MockMultipartFile file =
        new MockMultipartFile("imageFile", "image.jpg", "IMAGE/JPEG", jpegBytes());

    String result = validator.validateAndNormalizeContentType(file);

    assertThat(result).isEqualTo("image/jpeg");
  }

  @Test
  void validateAndNormalizeContentType_rejectsMismatchedSignature() {
    MockMultipartFile file =
        new MockMultipartFile("imageFile", "image.jpg", "image/jpeg", pngBytes());

    assertThatThrownBy(() -> validator.validateAndNormalizeContentType(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 이미지 형식입니다.");
  }

  @Test
  void validateAndNormalizeContentType_rejectsUnsupportedDeclaredContentType() {
    MockMultipartFile file =
        new MockMultipartFile("imageFile", "image.txt", "text/plain", jpegBytes());

    assertThatThrownBy(() -> validator.validateAndNormalizeContentType(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 이미지 형식입니다.");
  }

  @Test
  void validateAndNormalizeContentType_rejectsUnknownSignature() {
    MockMultipartFile file =
        new MockMultipartFile("imageFile", "image.jpg", "image/jpeg", "not an image".getBytes());

    assertThatThrownBy(() -> validator.validateAndNormalizeContentType(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 이미지 형식입니다.");
  }

  @Test
  void validateAndNormalizeContentType_rejectsTruncatedSignature() {
    MockMultipartFile file =
        new MockMultipartFile("imageFile", "image.png", "image/png", new byte[] {(byte) 0x89});

    assertThatThrownBy(() -> validator.validateAndNormalizeContentType(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 이미지 형식입니다.");
  }

  private static Stream<Arguments> validImages() {
    return Stream.of(
        Arguments.of(jpegBytes(), "image/jpeg"),
        Arguments.of(pngBytes(), "image/png"),
        Arguments.of(webpBytes(), "image/webp"),
        Arguments.of(gifBytes(), "image/gif"));
  }

  private static byte[] jpegBytes() {
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
  }

  private static byte[] pngBytes() {
    return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
  }

  private static byte[] webpBytes() {
    return new byte[] {
      0x52, 0x49, 0x46, 0x46,
      0x00, 0x00, 0x00, 0x00,
      0x57, 0x45, 0x42, 0x50,
      0x00
    };
  }

  private static byte[] gifBytes() {
    return new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00};
  }
}
