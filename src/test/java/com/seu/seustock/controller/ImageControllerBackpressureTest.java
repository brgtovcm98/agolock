package com.seu.seustock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seu.seustock.service.ImageStorageService;
import com.seu.seustock.service.ai.ImageAnalysisService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

class ImageControllerBackpressureTest extends AbstractControllerTest {

  @MockitoBean private ImageStorageService imageStorageService;

  @MockitoBean private ImageAnalysisService imageAnalysisService;

  @MockitoBean(name = "aiAnalysisExecutor")
  private Executor aiAnalysisExecutor;

  @Test
  @DisplayName("POST /images/analyze - AI executor 포화 → 503 + 재시도 안내")
  void analyze_whenExecutorRejects_returns503WithRetryMessage() throws Exception {
    doThrow(new RejectedExecutionException("executor full"))
        .when(aiAnalysisExecutor)
        .execute(any(Runnable.class));
    MockMultipartFile imageFile =
        new MockMultipartFile("imageFile", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});

    MvcResult asyncResult =
        mockMvc
            .perform(
                multipart("/images/analyze").file(imageFile).with(user("testuser")).with(csrf()))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(asyncResult))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("이미지 AI 분석 요청이 많습니다. 잠시 후 다시 시도해주세요."));
  }
}
