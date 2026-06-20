package com.seu.seustock.service.ai;

import com.seu.seustock.model.dto.ImageAnalysisDTO;
import com.seu.seustock.service.ImageFileValidator;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class YoloGemmaImageAnalysisService implements ImageAnalysisService {

  private static final Logger log = LoggerFactory.getLogger(YoloGemmaImageAnalysisService.class);

  private final ImageFileValidator imageFileValidator;
  private final ImageResizeService imageResizeService;
  private final YoloDetectionClient yoloDetectionClient;
  private final GemmaVisionClient gemmaVisionClient;

  @Override
  public ImageAnalysisDTO analyze(MultipartFile imageFile) {
    return analyze(imageFile, 0, null, null);
  }

  @Override
  public ImageAnalysisDTO analyze(
      MultipartFile imageFile, int retryAttempt, String previousName, String previousDescription) {
    String contentType = validate(imageFile);

    try {
      log.info(
          "[YoloGemmaImageAnalysisService] 분석 시작 - contentType={}, size={}, retryAttempt={}",
          contentType,
          imageFile.getSize(),
          retryAttempt);
      ImageResizeService.ResizedImage resized =
          imageResizeService.resizeForAnalysis(imageFile.getBytes(), contentType);
      List<YoloDetection> detections =
          yoloDetectionClient.detect(resized.bytes(), resized.mimeType());
      try {
        return gemmaVisionClient.analyze(
            resized.bytes(),
            resized.mimeType(),
            retryAttempt,
            previousName,
            previousDescription,
            detections);
      } catch (RuntimeException e) {
        log.error("[YoloGemmaImageAnalysisService] LLM 이미지 분석 실패", e);
        throw new AiServiceUnavailableException("현재 AI 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.", e);
      }
    } catch (IOException e) {
      log.error("[YoloGemmaImageAnalysisService] 이미지 파일 읽기 실패", e);
      throw new IllegalStateException("이미지 파일을 읽을 수 없습니다.", e);
    }
  }

  private String validate(MultipartFile imageFile) {
    if (imageFile == null || imageFile.isEmpty()) {
      throw new IllegalArgumentException("분석할 이미지 파일이 없습니다.");
    }

    return imageFileValidator.validateAndNormalizeContentType(imageFile);
  }
}
