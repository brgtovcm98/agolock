package com.seu.seustock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.seu.seustock.model.dto.ImageAnalysisDTO;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.service.ImageStorageService;
import com.seu.seustock.service.ai.AiServiceUnavailableException;
import com.seu.seustock.service.ai.ImageAnalysisService;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ImageController 웹 계층 테스트.
 *
 * <p>검증 범위:
 *
 * <ul>
 *   <li>Security: 3개 엔드포인트 미인증 → /login 리다이렉트
 *   <li>Response Shape: GET /images/{id} → 200, Cache-Control, ETag 헤더
 *   <li>Async: POST /images/analyze 파일 업로드 → asyncStarted → 200 JSON
 *   <li>Async: POST /images/{id}/analyze → asyncStarted → 200 JSON
 * </ul>
 *
 * 특이사항: {@code aiAnalysisExecutor}는 실제 빈을 사용하고 {@code ImageAnalysisService}를 Mock으로 대체하여 Ollama 호출
 * 없이 테스트한다.
 */
class ImageControllerTest extends AbstractControllerTest {

  @MockitoBean private ImageStorageService imageStorageService;

  @MockitoBean private ImageAnalysisService imageAnalysisService;

  private static final String IMAGE_PATH = "/images/" + IMAGE_ID;

  @BeforeEach
  void stubDefaults() {
    given(imageStorageService.loadForUser(any(), anyString())).willReturn(stubImage());
    given(imageStorageService.load(any())).willReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
    given(imageAnalysisService.analyze(any(), anyInt(), any(), any())).willReturn(stubAnalysis());
  }

  // ── Security: 미인증 리다이렉트 ────────────────────────────────────────

  @ParameterizedTest(name = "{0} {1} 미인증 → /login")
  @MethodSource("protectedEndpoints")
  @DisplayName("모든 엔드포인트 - 미인증 → /login 리다이렉트")
  void endpoints_whenUnauthenticated_redirectToLogin(String method, String url) throws Exception {
    mockMvc
        .perform(request(HttpMethod.valueOf(method), url).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  static Stream<Arguments> protectedEndpoints() {
    return Stream.of(
        Arguments.of("GET", "/images/" + IMAGE_ID),
        Arguments.of("POST", "/images/" + IMAGE_ID + "/analyze"),
        Arguments.of("POST", "/images/analyze"));
  }

  // ── Response Shape: GET /images/{id} ─────────────────────────────────

  @Test
  @DisplayName("GET /images/{id} - 인증 → 200, Cache-Control max-age, ETag 헤더")
  void show_authenticated_returns200WithCacheHeaders() throws Exception {
    mockMvc
        .perform(get(IMAGE_PATH).with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("max-age=604800")))
        .andExpect(header().exists("ETag"))
        .andExpect(content().bytes(new byte[] {1, 2, 3}));
  }

  @Test
  @DisplayName("GET /images/{id} - 한글 파일명 → ASCII-safe Content-Disposition 헤더")
  void show_withKoreanFilename_returnsAsciiSafeContentDisposition() throws Exception {
    ImageDTO image = stubImage();
    image.setOriginalFilename("한글 이미지.png");
    given(imageStorageService.loadForUser(any(), anyString())).willReturn(image);

    MvcResult result =
        mockMvc
            .perform(get(IMAGE_PATH).with(user("testuser")))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Disposition"))
            .andReturn();

    String disposition = result.getResponse().getHeader("Content-Disposition");
    assertThat(disposition).contains("filename*=UTF-8''");
    assertThat(disposition.chars()).allMatch(c -> c <= 0x7f);
  }

  // ── Response Shape: POST /images/analyze (파일 업로드, 비동기) ────────────

  @Test
  @DisplayName("POST /images/analyze - 파일 업로드 → asyncStarted, 이후 200 + JSON 분석 결과")
  void analyze_withImageFile_returns200WithAnalysisResult() throws Exception {
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
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("분석된 품목"))
        .andExpect(jsonPath("$.description").value("분석된 설명"));
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  @DisplayName("POST /images/analyze - XSRF 쿠키와 헤더 조합 → asyncStarted, 이후 200 + JSON 분석 결과")
  void analyze_withXsrfCookieAndHeader_returns200WithAnalysisResult() throws Exception {
    MvcResult pageResult =
        mockMvc
            .perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();
    Cookie xsrfCookie = pageResult.getResponse().getCookie("XSRF-TOKEN");
    MockMultipartFile imageFile =
        new MockMultipartFile("imageFile", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});

    MvcResult asyncResult =
        mockMvc
            .perform(
                multipart("/images/analyze")
                    .file(imageFile)
                    .with(user("testuser"))
                    .cookie(xsrfCookie)
                    .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(asyncResult))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("분석된 품목"))
        .andExpect(jsonPath("$.description").value("분석된 설명"));
  }

  @Test
  @DisplayName("POST /images/analyze - AI 서비스 실패 → asyncStarted, 이후 503 + 사용자 메시지")
  void analyze_whenAiServiceUnavailable_returns503WithMessage() throws Exception {
    given(imageAnalysisService.analyze(any(), anyInt(), any(), any()))
        .willThrow(
            new AiServiceUnavailableException(
                "현재 AI 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.",
                new RuntimeException("ollama unavailable")));
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
        .andExpect(jsonPath("$.message").value("현재 AI 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요."));
  }

  // ── Response Shape: POST /images/{id}/analyze (저장된 이미지 재분석, 비동기) ─

  @Test
  @DisplayName("POST /images/{id}/analyze - 인증 → asyncStarted, 이후 200 + JSON 분석 결과")
  void analyzeStored_authenticated_returns200WithAnalysisResult() throws Exception {
    MvcResult asyncResult =
        mockMvc
            .perform(post(IMAGE_PATH + "/analyze").with(user("testuser")).with(csrf()))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(asyncResult))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("분석된 품목"));
  }

  @Test
  @DisplayName("POST /images/{id}/analyze - 저장 이미지 AI 서비스 실패 → asyncStarted, 이후 503 + 사용자 메시지")
  void analyzeStored_whenAiServiceUnavailable_returns503WithMessage() throws Exception {
    given(imageAnalysisService.analyze(any(), anyInt(), any(), any()))
        .willThrow(
            new AiServiceUnavailableException(
                "현재 AI 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.",
                new RuntimeException("ollama unavailable")));

    MvcResult asyncResult =
        mockMvc
            .perform(post(IMAGE_PATH + "/analyze").with(user("testuser")).with(csrf()))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(asyncResult))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("현재 AI 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요."));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────

  private ImageDTO stubImage() {
    ImageDTO dto = new ImageDTO();
    dto.setExternalId(IMAGE_ID);
    dto.setContentType("image/jpeg");
    dto.setOriginalFilename("test.jpg");
    return dto;
  }

  private ImageAnalysisDTO stubAnalysis() {
    ImageAnalysisDTO dto = new ImageAnalysisDTO();
    dto.setName("분석된 품목");
    dto.setDescription("분석된 설명");
    return dto;
  }
}
