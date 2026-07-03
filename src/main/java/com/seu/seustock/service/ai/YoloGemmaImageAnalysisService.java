package com.seu.seustock.service.ai;

import com.seu.seustock.model.dto.ImageAnalysisDTO;
import com.seu.seustock.service.ImageFileValidator;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
  private final MessageSource messageSource;

  private String getMsg(String code, Object... args) {
    return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
  }

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
        throw new AiServiceUnavailableException(getMsg("error.ai.unavailable"), e);
      }
    } catch (IOException e) {
      log.error("[YoloGemmaImageAnalysisService] 이미지 파일 읽기 실패", e);
      throw new IllegalStateException(getMsg("error.image.readFailed"), e);
    }
  }

  private String validate(MultipartFile imageFile) {
    if (imageFile == null || imageFile.isEmpty()) {
      throw new IllegalArgumentException(getMsg("error.image.noFileToAnalyze"));
    }

    return imageFileValidator.validateAndNormalizeContentType(imageFile);
  }
}
