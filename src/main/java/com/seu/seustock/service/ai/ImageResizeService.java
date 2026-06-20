package com.seu.seustock.service.ai;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImageResizeService {

  private static final Logger log = LoggerFactory.getLogger(ImageResizeService.class);
  private static final int MAX_SIDE = 1024;

  public ResizedImage resizeForAnalysis(byte[] original, String originalMimeType)
      throws IOException {
    BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
    if (src == null) {
      log.warn("[ImageResizeService] 리사이즈 불가 포맷({}), 원본 전송", originalMimeType);
      return new ResizedImage(original, originalMimeType);
    }

    int w = src.getWidth();
    int h = src.getHeight();

    BufferedImage dst;
    if (w <= MAX_SIDE && h <= MAX_SIDE) {
      log.debug("[ImageResizeService] 리사이즈 생략, JPEG 변환만 수행 ({}x{})", w, h);
      dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = dst.createGraphics();
      g.drawImage(src, 0, 0, null);
      g.dispose();
    } else {
      double scale = (double) MAX_SIDE / Math.max(w, h);
      int nw = (int) Math.round(w * scale);
      int nh = (int) Math.round(h * scale);
      log.debug("[ImageResizeService] 리사이즈 {}x{} -> {}x{}", w, h, nw, nh);
      dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = dst.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(src, 0, 0, nw, nh, null);
      g.dispose();
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(dst, "jpeg", baos);
    return new ResizedImage(baos.toByteArray(), "image/jpeg");
  }

  public record ResizedImage(byte[] bytes, String mimeType) {}
}
