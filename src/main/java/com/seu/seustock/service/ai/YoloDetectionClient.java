package com.seu.seustock.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class YoloDetectionClient {

  private static final Logger log = LoggerFactory.getLogger(YoloDetectionClient.class);

  private final RestClient restClient;
  private final boolean enabled;

  public YoloDetectionClient(
      @Qualifier("yoloRestClient") RestClient restClient,
      @Value("${seustock.ai.yolo.enabled:false}") boolean enabled) {
    this.restClient = restClient;
    this.enabled = enabled;
  }

  public List<YoloDetection> detect(byte[] imageBytes, String mimeType) {
    if (!enabled) {
      log.info("[YoloDetectionClient] YOLO 비활성화 - Gemma 단독 분석으로 진행합니다.");
      return List.of();
    }

    try {
      log.info(
          "[YoloDetectionClient] YOLO 호출 시작 - mimeType={}, size={}", mimeType, imageBytes.length);
      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", new NamedByteArrayResource(imageBytes, "analysis-image.jpg"));

      YoloDetectionResponse response =
          restClient
              .post()
              .uri("/detect")
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .body(YoloDetectionResponse.class);

      if (response == null || response.objects() == null) {
        log.info("[YoloDetectionClient] YOLO 응답 수신 - detections=0");
        return List.of();
      }
      List<YoloDetection> detections =
          response.objects().stream().map(YoloDetectionPayload::toDetection).toList();
      log.info("[YoloDetectionClient] YOLO 응답 수신 - detections={}", detections.size());
      return detections;
    } catch (Exception e) {
      log.warn("[YoloDetectionClient] YOLO 호출 실패, Gemma 단독 분석으로 fallback합니다.", e);
      return List.of();
    }
  }

  private static class NamedByteArrayResource extends ByteArrayResource {
    private final String filename;

    private NamedByteArrayResource(byte[] byteArray, String filename) {
      super(byteArray);
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record YoloDetectionResponse(List<YoloDetectionPayload> objects) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record YoloDetectionPayload(String label, Double confidence, Bbox bbox) {
    private YoloDetection toDetection() {
      if (bbox == null) {
        return new YoloDetection(label, confidence, null, null, null, null);
      }
      return new YoloDetection(label, confidence, bbox.x1(), bbox.y1(), bbox.x2(), bbox.y2());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Bbox(Double x1, Double y1, Double x2, Double y2) {}
}
