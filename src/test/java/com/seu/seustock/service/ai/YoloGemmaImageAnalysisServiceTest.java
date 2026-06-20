package com.seu.seustock.service.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.seu.seustock.service.ImageFileValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class YoloGemmaImageAnalysisServiceTest {

  @Mock private ImageResizeService imageResizeService;
  @Mock private YoloDetectionClient yoloDetectionClient;
  @Mock private GemmaVisionClient gemmaVisionClient;

  @Test
  void analyze_rejectsSpoofedImageBeforeCallingClients() throws Exception {
    YoloGemmaImageAnalysisService service =
        new YoloGemmaImageAnalysisService(
            new ImageFileValidator(), imageResizeService, yoloDetectionClient, gemmaVisionClient);
    MockMultipartFile file =
        new MockMultipartFile(
            "imageFile", "spoof.jpg", "image/jpeg", new byte[] {0x01, 0x02, 0x03});

    assertThatThrownBy(() -> service.analyze(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 이미지 형식입니다.");

    verify(imageResizeService, never()).resizeForAnalysis(any(), any());
    verify(yoloDetectionClient, never()).detect(any(), any());
    verify(gemmaVisionClient, never()).analyze(any(), any(), anyInt(), any(), any(), any());
  }
}
