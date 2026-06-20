package com.seu.seustock.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * GlobalExceptionHandler 순수 단위 테스트.
 *
 * <p>{@code HtmxExceptionHandlerExceptionResolver}의 {@code @RequestHeader} 파라미터 해결 제한으로
 * {@code @SpringBootTest + MockMvc}로는 HTMX 분기를 테스트할 수 없어 직접 단위 테스트로 검증한다.
 *
 * <p>검증 범위:
 *
 * <ul>
 *   <li>예외 종류별 올바른 뷰 이름 반환
 *   <li>HX-Request 헤더 유무에 따른 에러 모달 vs. 에러 페이지 분기
 *   <li>모델에 {@code statusCode}, {@code errorTitle} 속성이 설정됨
 * </ul>
 */
class GlobalExceptionHandlerTest {

  private MessageSource messageSource;
  private GlobalExceptionHandler handler;
  private final Model model = new ExtendedModelMap();

  @BeforeEach
  void setUp() {
    messageSource = mock(MessageSource.class);
    handler = new GlobalExceptionHandler(messageSource);

    when(messageSource.getMessage(eq("error.404.title"), any(), any(Locale.class)))
        .thenReturn("항목을 찾을 수 없습니다");
    when(messageSource.getMessage(eq("error.403.title"), any(), any(Locale.class)))
        .thenReturn("접근할 수 없습니다");
    when(messageSource.getMessage(eq("error.400.title"), any(), any(Locale.class)))
        .thenReturn("요청을 처리할 수 없습니다");
    when(messageSource.getMessage(eq("error.image.sizeExceeded"), any(), any(), any(Locale.class)))
        .thenReturn("업로드 파일 크기 제한(10MB)을 초과했습니다.");
  }

  // ── NoSuchElementException (404) ──────────────────────────────────────

  @Test
  @DisplayName("NoSuchElementException + HX-Request:true → 에러 모달 프래그먼트 반환")
  void handleNotFound_withHxRequest_returnsErrorModalFragment() {
    String view =
        handler.handleNotFound(new NoSuchElementException("공간을 찾을 수 없습니다."), "true", model);

    assertThat(view).isEqualTo("fragments/error-modal :: modal");
    assertThat(model.asMap().get("statusCode")).isEqualTo(404);
    assertThat(model.asMap().get("errorTitle")).isEqualTo("항목을 찾을 수 없습니다");
    assertThat(model.asMap().get("errorMessage")).isEqualTo("공간을 찾을 수 없습니다.");
  }

  @Test
  @DisplayName("NoSuchElementException, HX-Request 없음 → error/404 전체 페이지 반환")
  void handleNotFound_withoutHxRequest_returnsFullErrorPage() {
    String view = handler.handleNotFound(new NoSuchElementException("공간을 찾을 수 없습니다."), null, model);

    assertThat(view).isEqualTo("error/404");
  }

  @Test
  @DisplayName("NoSuchElementException, HX-Request:false → error/404 전체 페이지 반환")
  void handleNotFound_withHxRequestFalse_returnsFullErrorPage() {
    String view =
        handler.handleNotFound(new NoSuchElementException("공간을 찾을 수 없습니다."), "false", model);

    assertThat(view).isEqualTo("error/404");
  }

  // ── SecurityException (403) ───────────────────────────────────────────

  @Test
  @DisplayName("SecurityException + HX-Request:true → 에러 모달 프래그먼트 반환")
  void handleForbidden_withHxRequest_returnsErrorModalFragment() {
    String view = handler.handleForbidden(new SecurityException("접근 권한이 없습니다."), "true", model);

    assertThat(view).isEqualTo("fragments/error-modal :: modal");
    assertThat(model.asMap().get("statusCode")).isEqualTo(403);
    assertThat(model.asMap().get("errorTitle")).isEqualTo("접근할 수 없습니다");
  }

  @Test
  @DisplayName("SecurityException, HX-Request 없음 → error/403 전체 페이지 반환")
  void handleForbidden_withoutHxRequest_returnsFullErrorPage() {
    String view = handler.handleForbidden(new SecurityException("접근 권한이 없습니다."), null, model);

    assertThat(view).isEqualTo("error/403");
  }

  // ── IllegalArgumentException / IllegalStateException (400) ───────────

  @Test
  @DisplayName("IllegalArgumentException + HX-Request:true → 에러 모달 프래그먼트 반환")
  void handleBadRequest_withIllegalArgument_andHxRequest_returnsErrorModalFragment() {
    String view =
        handler.handleBadRequest(new IllegalArgumentException("잘못된 요청입니다."), "true", model);

    assertThat(view).isEqualTo("fragments/error-modal :: modal");
    assertThat(model.asMap().get("statusCode")).isEqualTo(400);
    assertThat(model.asMap().get("errorTitle")).isEqualTo("요청을 처리할 수 없습니다");
  }

  @Test
  @DisplayName("IllegalStateException + HX-Request:true → 에러 모달 프래그먼트 반환")
  void handleBadRequest_withIllegalState_andHxRequest_returnsErrorModalFragment() {
    String view =
        handler.handleBadRequest(
            new IllegalStateException("재고가 있는 공간은 삭제할 수 없습니다."), "true", model);

    assertThat(view).isEqualTo("fragments/error-modal :: modal");
    assertThat(model.asMap().get("errorMessage")).isEqualTo("재고가 있는 공간은 삭제할 수 없습니다.");
  }

  @Test
  @DisplayName("IllegalArgumentException, HX-Request 없음 → error/400 전체 페이지 반환")
  void handleBadRequest_withoutHxRequest_returnsFullErrorPage() {
    String view = handler.handleBadRequest(new IllegalArgumentException("bad"), null, model);

    assertThat(view).isEqualTo("error/400");
  }

  // ── MaxUploadSizeExceededException (400) ──────────────────────────────────

  @Test
  @DisplayName("MaxUploadSizeExceededException + HX-Request:true → 에러 모달 프래그먼트 반환")
  void handleMaxUploadSizeExceeded_withHxRequest_returnsErrorModalFragment() {
    String view =
        handler.handleMaxUploadSizeExceeded(
            new MaxUploadSizeExceededException(10 * 1024 * 1024), "true", model);

    assertThat(view).isEqualTo("fragments/error-modal :: modal");
    assertThat(model.asMap().get("statusCode")).isEqualTo(400);
    assertThat(model.asMap().get("errorTitle")).isEqualTo("요청을 처리할 수 없습니다");
    assertThat(model.asMap().get("errorMessage")).isEqualTo("업로드 파일 크기 제한(10MB)을 초과했습니다.");
  }

  @Test
  @DisplayName("MaxUploadSizeExceededException, HX-Request 없음 → error/400 전체 페이지 반환")
  void handleMaxUploadSizeExceeded_withoutHxRequest_returnsFullErrorPage() {
    String view =
        handler.handleMaxUploadSizeExceeded(
            new MaxUploadSizeExceededException(10 * 1024 * 1024), null, model);

    assertThat(view).isEqualTo("error/400");
    assertThat(model.asMap().get("errorMessage")).isEqualTo("업로드 파일 크기 제한(10MB)을 초과했습니다.");
  }
}
