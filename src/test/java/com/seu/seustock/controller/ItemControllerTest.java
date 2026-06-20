package com.seu.seustock.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.pagination.PageResult;
import com.seu.seustock.service.ItemService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** ItemController 웹 계층 테스트. */
class ItemControllerTest extends AbstractControllerTest {

  @MockitoBean private ItemService itemService;

  @BeforeEach
  void stubDefaults() {
    given(itemService.findPageByUsername(anyString(), any(), anyString(), anyString(), any()))
        .willReturn(new PageResult<>(List.of(), 1, 10, 0));
    given(itemService.findByExternalId(any(), anyString())).willReturn(stubItem());
    given(itemService.create(anyString(), any())).willReturn(stubItem());
    given(itemService.update(any(), any(), anyString())).willReturn(stubItem());
    given(itemService.findSpaceStock(any(), anyString())).willReturn(List.of());
    given(itemService.findTransactionHistory(any(), anyString())).willReturn(List.of());
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
        Arguments.of("GET", "/items"),
        Arguments.of("GET", "/items/new"),
        Arguments.of("POST", "/items"),
        Arguments.of("GET", "/items/" + ITEM_ID + "/edit"),
        Arguments.of("PUT", "/items/" + ITEM_ID),
        Arguments.of("GET", "/items/" + ITEM_ID + "/cancel"),
        Arguments.of("GET", "/items/" + ITEM_ID + "/spaces"),
        Arguments.of("GET", "/items/" + ITEM_ID + "/history"),
        Arguments.of("DELETE", "/items/" + ITEM_ID));
  }

  @Test
  @DisplayName("POST /items - CSRF 없음 → 403 Forbidden")
  void create_withoutCsrf_returns403() throws Exception {
    mockMvc
        .perform(post("/items").with(user("testuser")).param("name", "신규 품목"))
        .andExpect(status().isForbidden());
  }

  // ── Response Shape: GET 엔드포인트 ────────────────────────────────────

  @Test
  @DisplayName("GET /items - 인증 → 200, items/list 뷰")
  void list_authenticated_returns200WithItemsListView() throws Exception {
    mockMvc
        .perform(get("/items").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("items/list"))
        .andExpect(model().attributeExists("items", "page"));
  }

  @Test
  @DisplayName("GET /items/new → modal 프래그먼트")
  void newModal_returnsModalFragment() throws Exception {
    mockMvc
        .perform(get("/items/new").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/modal :: modal"));
  }

  @Test
  @DisplayName("GET /items/{id}/edit → card :: edit 프래그먼트")
  void editRow_returnsEditFragment() throws Exception {
    mockMvc
        .perform(get("/items/" + ITEM_ID + "/edit").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/card :: edit"));
  }

  @Test
  @DisplayName("GET /items/{id}/cancel → card :: view 프래그먼트")
  void cancelEdit_returnsViewFragment() throws Exception {
    mockMvc
        .perform(get("/items/" + ITEM_ID + "/cancel").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/card :: view"));
  }

  @Test
  @DisplayName("GET /items/{id}/spaces → space-stock-modal :: modal 프래그먼트")
  void spaces_returnsSpaceStockModalFragment() throws Exception {
    mockMvc
        .perform(get("/items/" + ITEM_ID + "/spaces").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/space-stock-modal :: modal"));
  }

  @Test
  @DisplayName("GET /items/{id}/history → history-modal :: modal 프래그먼트")
  void history_returnsHistoryModalFragment() throws Exception {
    mockMvc
        .perform(get("/items/" + ITEM_ID + "/history").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/history-modal :: modal"));
  }

  // ── Response Shape: 변경 엔드포인트 ──────────────────────────────────

  @Test
  @DisplayName("POST /items - 정상 name → created 프래그먼트 + HX-Trigger")
  void create_withValidName_returnsCreatedFragmentWithToast() throws Exception {
    mockMvc
        .perform(post("/items").with(user("testuser")).with(csrf()).param("name", "새 품목"))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/modal :: created"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("PUT /items/{id} - 정상 name → card :: view 프래그먼트 + HX-Trigger")
  void updateRow_withValidName_returnsViewFragmentWithToast() throws Exception {
    mockMvc
        .perform(
            put("/items/" + ITEM_ID).with(user("testuser")).with(csrf()).param("name", "수정된 품목"))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/card :: view"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("DELETE /items/{id} → items/list :: item-list-section + HX-Trigger")
  void delete_returnsItemListSectionWithToast() throws Exception {
    mockMvc
        .perform(delete("/items/" + ITEM_ID).with(user("testuser")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("items/list :: item-list-section"))
        .andExpect(hasToastTrigger());
  }

  // ── Validation ────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /items - 빈 name → modal 프래그먼트 (유효성 실패, HX-Trigger 없음)")
  void create_withBlankName_returnsModalWithoutToast() throws Exception {
    mockMvc
        .perform(post("/items").with(user("testuser")).with(csrf()).param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /items - name 100자 초과 → modal 프래그먼트 (유효성 실패)")
  void create_withTooLongName_returnsModalWithoutToast() throws Exception {
    String longName = "a".repeat(101);
    mockMvc
        .perform(post("/items").with(user("testuser")).with(csrf()).param("name", longName))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("PUT /items/{id} - 빈 name → card :: edit 프래그먼트 (유효성 실패)")
  void updateRow_withBlankName_returnsEditFragmentWithoutToast() throws Exception {
    mockMvc
        .perform(put("/items/" + ITEM_ID).with(user("testuser")).with(csrf()).param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("items/fragments/card :: edit"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────

  private ItemDTO stubItem() {
    ItemDTO dto = new ItemDTO();
    dto.setId(1L);
    dto.setExternalId(ITEM_ID);
    dto.setUserId(1L);
    dto.setName("테스트 품목");
    dto.setDescription("설명");
    return dto;
  }
}
