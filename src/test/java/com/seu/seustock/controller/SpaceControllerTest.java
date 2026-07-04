package com.seu.seustock.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.pagination.PageResult;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SpaceController 웹 계층 테스트.
 *
 * <p>검증 범위:
 *
 * <ul>
 *   <li>Security: 모든 엔드포인트 미인증 → /login 리다이렉트, POST CSRF 검증
 *   <li>Validation: 빈 이름 제출 시 동일 뷰 반환, HX-Trigger 없음
 *   <li>Response Shape: full-page vs. HTMX 프래그먼트 뷰 이름, append=true 분기
 *   <li>GlobalExceptionHandler: HTMX 요청 시 에러 모달 프래그먼트 반환
 * </ul>
 */
class SpaceControllerTest extends AbstractControllerTest {

  @MockitoBean private SpaceService spaceService;

  @MockitoBean private ShelfService shelfService;

  @MockitoBean private StockService stockService;

  // ── 기본 스텁 ──────────────────────────────────────────────────────────

  @BeforeEach
  void stubDefaults() {
    given(spaceService.findPageByUsername(anyString(), any(), anyString(), any()))
        .willReturn(new PageResult<>(List.of(), 1, 10, 0));
    given(spaceService.findByExternalId(any(), anyString())).willReturn(stubSpace());
    given(shelfService.findAllBySpaceId(any(), anyString())).willReturn(List.of());
    given(stockService.findPanelBySpace(any(), anyString())).willReturn(List.of());
    given(spaceService.update(any(), any(), anyString())).willReturn(stubSpace());
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
        Arguments.of("GET", "/spaces"),
        Arguments.of("GET", "/spaces/" + SPACE_ID),
        Arguments.of("POST", "/spaces"),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/edit"),
        Arguments.of("PUT", "/spaces/" + SPACE_ID),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/cancel"),
        Arguments.of("DELETE", "/spaces/" + SPACE_ID));
  }

  @Test
  @DisplayName("POST /spaces - CSRF 토큰 없음 → 403 Forbidden")
  void create_withoutCsrf_returns403() throws Exception {
    mockMvc
        .perform(post("/spaces").with(user("testuser")).param("name", "창고"))
        .andExpect(status().isForbidden());
  }

  // ── Response Shape: GET 엔드포인트 ────────────────────────────────────

  @Test
  @DisplayName("GET /spaces - 인증 → 200, spaces/list 뷰")
  void list_authenticated_returns200WithSpacesListView() throws Exception {
    mockMvc
        .perform(get("/spaces").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/list"))
        .andExpect(model().attributeExists("spaces", "page", "form"));
  }

  @Test
  @DisplayName("GET /spaces?append=true - 인증 → space-more-response 프래그먼트")
  void list_withAppendTrue_returnsMoreResponseFragment() throws Exception {
    mockMvc
        .perform(get("/spaces").with(user("testuser")).param("append", "true"))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/fragments/list-response :: space-more-response"));
  }

  @Test
  @DisplayName("GET /spaces/{id} - 인증 → 200, spaces/detail 뷰")
  void detail_authenticated_returns200WithDetailView() throws Exception {
    mockMvc
        .perform(get("/spaces/" + SPACE_ID).with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/detail"))
        .andExpect(model().attributeExists("space", "shelves"));
  }

  @Test
  @DisplayName("GET /spaces/{id}/edit - 인증 → edit 프래그먼트")
  void editRow_returns200WithEditFragment() throws Exception {
    mockMvc
        .perform(get("/spaces/" + SPACE_ID + "/edit").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/fragments/row :: edit"));
  }

  @Test
  @DisplayName("GET /spaces/{id}/cancel - 인증 → view 프래그먼트")
  void cancelEdit_returns200WithViewFragment() throws Exception {
    mockMvc
        .perform(get("/spaces/" + SPACE_ID + "/cancel").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/fragments/row :: view"));
  }

  // ── Response Shape: 변경 엔드포인트 ──────────────────────────────────

  @Test
  @DisplayName("POST /spaces - 정상 name → /spaces 리다이렉트")
  void create_withValidName_redirectsToSpaces() throws Exception {
    mockMvc
        .perform(post("/spaces").with(user("testuser")).with(csrf()).param("name", "새 창고"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/spaces"));
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
  @DisplayName("POST /spaces - XSRF 쿠키와 헤더 조합 → /spaces 리다이렉트")
  void create_withXsrfCookieAndHeader_redirectsToSpaces() throws Exception {
    MvcResult pageResult =
        mockMvc
            .perform(get("/spaces").with(user("testuser")))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();
    Cookie xsrfCookie = pageResult.getResponse().getCookie("XSRF-TOKEN");

    mockMvc
        .perform(
            post("/spaces")
                .with(user("testuser"))
                .cookie(xsrfCookie)
                .header("X-XSRF-TOKEN", xsrfCookie.getValue())
                .param("name", "새 창고"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/spaces"));
  }

  @Test
  @DisplayName("PUT /spaces/{id} - 정상 name → space-list-section 프래그먼트 + HX-Trigger")
  void updateRow_withValidName_returnsListSectionWithToast() throws Exception {
    mockMvc
        .perform(
            put("/spaces/" + SPACE_ID).with(user("testuser")).with(csrf()).param("name", "수정된 창고"))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/list :: space-list-section"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("DELETE /spaces/{id} - 인증 → space-list-section 프래그먼트 + HX-Trigger")
  void delete_returnsListSectionWithToast() throws Exception {
    mockMvc
        .perform(delete("/spaces/" + SPACE_ID).with(user("testuser")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/list :: space-list-section"))
        .andExpect(hasToastTrigger());
  }

  // ── Validation ────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /spaces - 빈 name → spaces/list 뷰 (유효성 실패, HX-Trigger 없음)")
  void create_withBlankName_returnsListViewWithoutToast() throws Exception {
    mockMvc
        .perform(post("/spaces").with(user("testuser")).with(csrf()).param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/list"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("PUT /spaces/{id} - 빈 name → space-list-section 프래그먼트 (유효성 실패, HX-Trigger 없음)")
  void updateRow_withBlankName_returnsListSectionWithoutToast() throws Exception {
    mockMvc
        .perform(put("/spaces/" + SPACE_ID).with(user("testuser")).with(csrf()).param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/list :: space-list-section"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────

  private SpaceDTO stubSpace() {
    SpaceDTO dto = new SpaceDTO();
    dto.setId(1L);
    dto.setExternalId(SPACE_ID);
    dto.setUserId(1L);
    dto.setName("테스트 공간");
    return dto;
  }
}
