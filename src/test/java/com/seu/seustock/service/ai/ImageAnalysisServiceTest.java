package com.seu.seustock.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageAnalysisServiceTest {

  private final ImageResizeService service = new ImageResizeService();

  private byte[] createImageBytes(String format, int width, int height) throws Exception {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, format, baos);
    return baos.toByteArray();
  }

  @Test
  void smallPng_convertsToJpeg() throws Exception {
    byte[] png = createImageBytes("png", 100, 100);

    ImageResizeService.ResizedImage result = service.resizeForAnalysis(png, "image/png");

    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
    assertThat(decoded).isNotNull();
    assertThat(decoded.getWidth()).isEqualTo(100);
    assertThat(decoded.getHeight()).isEqualTo(100);
  }

  @Test
  void largePng_resizesAndConvertsToJpeg() throws Exception {
    byte[] png = createImageBytes("png", 2000, 1500);

    ImageResizeService.ResizedImage result = service.resizeForAnalysis(png, "image/png");

    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
    assertThat(decoded).isNotNull();
    assertThat(decoded.getWidth()).isLessThanOrEqualTo(1024);
    assertThat(decoded.getHeight()).isLessThanOrEqualTo(1024);
    assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(1024);
  }

  @Test
  void smallGif_convertsToJpeg() throws Exception {
    byte[] gif = createImageBytes("gif", 200, 150);

    ImageResizeService.ResizedImage result = service.resizeForAnalysis(gif, "image/gif");

    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
    assertThat(decoded).isNotNull();
    assertThat(decoded.getWidth()).isEqualTo(200);
    assertThat(decoded.getHeight()).isEqualTo(150);
  }

  @Test
  void largeJpeg_resizesAndConvertsToJpeg() throws Exception {
    byte[] jpeg = createImageBytes("jpeg", 1500, 1500);

    ImageResizeService.ResizedImage result = service.resizeForAnalysis(jpeg, "image/jpeg");

    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
    assertThat(decoded).isNotNull();
    assertThat(decoded.getWidth()).isLessThanOrEqualTo(1024);
    assertThat(decoded.getHeight()).isLessThanOrEqualTo(1024);
    assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(1024);
  }

  @Test
  void imageAtExactMaxSide_convertsToJpegWithoutResize() throws Exception {
    byte[] png = createImageBytes("png", 1024, 768);

    ImageResizeService.ResizedImage result = service.resizeForAnalysis(png, "image/png");

    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
    assertThat(decoded).isNotNull();
    assertThat(decoded.getWidth()).isEqualTo(1024);
    assertThat(decoded.getHeight()).isEqualTo(768);
  }
}
