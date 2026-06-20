package com.seu.seustock.service.ai;

import com.seu.seustock.model.dto.ImageAnalysisDTO;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

@Component
public class GemmaVisionClient {

  private static final Logger log = LoggerFactory.getLogger(GemmaVisionClient.class);
  private static final int MAX_RETRY_ATTEMPT = 3;

  private final ChatClient chatClient;

  public GemmaVisionClient(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  public ImageAnalysisDTO analyze(
      byte[] imageBytes,
      String mimeType,
      int retryAttempt,
      String previousName,
      String previousDescription,
      List<YoloDetection> detections) {
    int normalizedRetryAttempt = Math.clamp(retryAttempt, 0, MAX_RETRY_ATTEMPT);
    BeanOutputConverter<ImageAnalysisDTO> outputConverter =
        new BeanOutputConverter<>(ImageAnalysisDTO.class);

    log.info(
        "[GemmaVisionClient] Ollama 호출 시작 - retryAttempt={}, yoloDetections={}",
        normalizedRetryAttempt,
        detections.size());

    OllamaChatOptions.Builder optionsBuilder =
        OllamaChatOptions.builder().temperature(temperatureFor(normalizedRetryAttempt));
    if (normalizedRetryAttempt > 0) {
      optionsBuilder.seed(ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
    }

    ImageAnalysisDTO result =
        chatClient
            .prompt()
            .options(optionsBuilder)
            .system(systemPrompt(normalizedRetryAttempt))
            .user(
                user ->
                    user.text(
                            userPrompt(
                                normalizedRetryAttempt,
                                previousName,
                                previousDescription,
                                detections,
                                outputConverter))
                        .media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes)))
            .call()
            .entity(outputConverter);

    log.info("[GemmaVisionClient] Ollama 응답 수신 - resultPresent={}", result != null);
    if (result == null) {
      throw new IllegalStateException("이미지 분석 결과가 비어 있습니다.");
    }
    return result;
  }

  private String systemPrompt(int retryAttempt) {
    String base =
        """
                당신은 재고 관리 앱의 이미지 분석 도우미입니다.
                이미지를 분석하여 요청한 JSON 형식으로만 응답하세요.
                모든 텍스트 값은 반드시 한국어로 작성하세요.
                이미지에서 확인되지 않는 브랜드·모델·수량은 절대 추측하지 마세요.
                YOLO 탐지 결과는 보조 단서로만 사용하고, 최종 판단은 이미지에서 확인되는 내용에 근거하세요.
                """;
    if (retryAttempt == 0) {
      return base;
    }
    return base
        + """
                이 요청은 사용자가 이전 분석 결과가 마음에 들지 않아 다시 요청한 retry입니다.
                같은 물품이라는 판단은 유지하되, 이전 결과와 같은 이름·설명 표현을 반복하지 마세요.
                더 일반적인 명칭, 더 구체적인 명칭, 다른 시각적 특징 중심 설명 중 하나를 선택하세요.
                """;
  }

  private String userPrompt(
      int retryAttempt,
      String previousName,
      String previousDescription,
      List<YoloDetection> detections,
      BeanOutputConverter<ImageAnalysisDTO> outputConverter) {
    String previousResult = "";
    if (retryAttempt > 0) {
      previousResult =
          """

                    이전 분석 결과:
                    - name: %s
                    - description: %s

                    위 표현을 그대로 반복하지 말고, 이미지에서 확인되는 범위 안에서 다른 후보를 제안해주세요.
                    """
              .formatted(blankToDash(previousName), blankToDash(previousDescription));
    }

    return """
                이미지의 물품을 분석하여 아래 필드를 한국어로 채워주세요.

                - name: 물품을 나타내는 짧은 이름 (5단어 이내, 예: USB 충전기, 드라이버 세트)
                - description: 색상·형태·소재·상태·포장 등 눈으로 확인되는 특징

                YOLO 전처리 결과:
                %s
                %s

                %s
                """
        .formatted(yoloSummary(detections), previousResult, outputConverter.getFormat());
  }

  private String yoloSummary(List<YoloDetection> detections) {
    if (detections == null || detections.isEmpty()) {
      return "- 탐지된 객체 없음. 전체 이미지를 기준으로 판단하세요.";
    }
    StringBuilder sb = new StringBuilder();
    int limit = Math.min(detections.size(), 10);
    for (int i = 0; i < limit; i++) {
      YoloDetection detection = detections.get(i);
      sb.append("- label: ")
          .append(blankToDash(detection.label()))
          .append(", confidence: ")
          .append(
              detection.confidence() == null ? "-" : String.format("%.2f", detection.confidence()));
      if (detection.x1() != null
          && detection.y1() != null
          && detection.x2() != null
          && detection.y2() != null) {
        sb.append(", box: [")
            .append(Math.round(detection.x1()))
            .append(", ")
            .append(Math.round(detection.y1()))
            .append(", ")
            .append(Math.round(detection.x2()))
            .append(", ")
            .append(Math.round(detection.y2()))
            .append("]");
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private double temperatureFor(int retryAttempt) {
    return switch (retryAttempt) {
      case 0 -> 0.1;
      case 1 -> 0.35;
      case 2 -> 0.55;
      default -> 0.7;
    };
  }

  private String blankToDash(String value) {
    if (value == null || value.isBlank()) {
      return "-";
    }
    return value.strip();
  }
}
