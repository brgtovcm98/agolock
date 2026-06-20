package com.seu.seustock.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeServiceTest {

  private final QrCodeService qrCodeService = new QrCodeService();

  @Test
  @DisplayName("QR 코드 생성 테스트 - PNG 바이트 배열을 반환해야 함")
  void generateQrCodeImage() throws Exception {
    // given
    String text = "https://example.com/qr/box/uuid-1234";
    int width = 300;
    int height = 300;

    // when
    byte[] imageBytes = qrCodeService.generateQrCodeImage(text, width, height);

    // then
    assertNotNull(imageBytes);
    assertTrue(imageBytes.length > 0);
    // PNG 파일 매직 넘버 확인 (89 50 4E 47 0D 0A 1A 0A)
    assertEquals((byte) 0x89, imageBytes[0]);
    assertEquals((byte) 0x50, imageBytes[1]);
    assertEquals((byte) 0x4E, imageBytes[2]);
    assertEquals((byte) 0x47, imageBytes[3]);
  }
}
