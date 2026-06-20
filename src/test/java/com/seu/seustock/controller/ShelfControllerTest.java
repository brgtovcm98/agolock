package com.seu.seustock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ShelfService;
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

/** ShelfController 웹 계층 테스트. */
class ShelfControllerTest extends AbstractControllerTest {

  @MockitoBean private ShelfService shelfService;

  @MockitoBean private BoxService boxService;

  private static final String SPACE_SHELF_PATH = "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID;

  @BeforeEach
  void stubDefaults() {
    given(shelfService.findByExternalId(any(), any(), anyString())).willReturn(stubShelf());
    given(shelfService.findAllBySpaceId(any(), anyString())).willReturn(List.of());
    given(shelfService.rename(any(), any(), any(), anyString())).willReturn(stubShelf());
    given(boxService.findAllByShelfId(any(), any(), anyString())).willReturn(List.of());
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
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes"),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/edit"),
        Arguments.of("PATCH", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/shelves/new"),
        Arguments.of("POST", "/spaces/" + SPACE_ID + "/shelves"),
        Arguments.of("DELETE", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID));
  }

  // ── Response Shape: GET 엔드포인트 ────────────────────────────────────

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/{id}/boxes → box-list 프래그먼트")
  void boxList_returnsBoxListFragment() throws Exception {
    mockMvc
        .perform(get(SPACE_SHELF_PATH + "/boxes").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/box-list :: box-list"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/{id}/edit → edit-modal 프래그먼트")
  void editModal_returnsEditModalFragment() throws Exception {
    mockMvc
        .perform(get(SPACE_SHELF_PATH + "/edit").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/modal :: edit-modal"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/new → modal 프래그먼트")
  void newModal_returnsModalFragment() throws Exception {
    mockMvc
        .perform(get("/spaces/" + SPACE_ID + "/shelves/new").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/modal :: modal"));
  }

  // ── Response Shape: 변경 엔드포인트 ──────────────────────────────────

  @Test
  @DisplayName(
      "PATCH /spaces/{sid}/shelves/{id} - 정상 name → shelf-list-response 프래그먼트 + HX-Trigger")
  void rename_withValidName_returnsShelfListResponseWithToast() throws Exception {
    mockMvc
        .perform(
            patch(SPACE_SHELF_PATH).with(user("testuser")).with(csrf()).param("name", "수정된 선반"))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/fragments/shelf-list-response :: shelf-list-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("POST /spaces/{sid}/shelves - 정상 name → shelf-list-response 프래그먼트 + HX-Trigger")
  void create_withValidName_returnsShelfListResponseWithToast() throws Exception {
    given(shelfService.create(any(), any(), anyString())).willReturn(stubShelf());

    mockMvc
        .perform(
            post("/spaces/" + SPACE_ID + "/shelves")
                .with(user("testuser"))
                .with(csrf())
                .param("name", "새 선반"))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/fragments/shelf-list-response :: shelf-list-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("DELETE /spaces/{sid}/shelves/{id} → spaces/detail :: shelf-list + HX-Trigger")
  void delete_returnsShelfListWithToast() throws Exception {
    mockMvc
        .perform(delete(SPACE_SHELF_PATH).with(user("testuser")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("spaces/detail :: shelf-list"))
        .andExpect(hasToastTrigger());
  }

  // ── Validation ────────────────────────────────────────────────────────

  @Test
  @DisplayName("PATCH /spaces/{sid}/shelves/{id} - 빈 name → edit-modal (유효성 실패, HX-Trigger 없음)")
  void rename_withBlankName_returnsEditModalWithoutToast() throws Exception {
    mockMvc
        .perform(patch(SPACE_SHELF_PATH).with(user("testuser")).with(csrf()).param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/modal :: edit-modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /spaces/{sid}/shelves - 빈 name → modal (유효성 실패, HX-Trigger 없음)")
  void create_withBlankName_returnsModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/spaces/" + SPACE_ID + "/shelves")
                .with(user("testuser"))
                .with(csrf())
                .param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────

  private ShelfDTO stubShelf() {
    ShelfDTO dto = new ShelfDTO();
    dto.setId(1L);
    dto.setExternalId(SHELF_ID);
    dto.setSpaceId(1L);
    dto.setName("테스트 선반");
    return dto;
  }
}
