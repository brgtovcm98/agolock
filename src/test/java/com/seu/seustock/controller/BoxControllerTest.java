package com.seu.seustock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.seu.seustock.model.dto.BoxDTO;
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

/**
 * BoxController 웹 계층 테스트.
 *
 * <p>특이사항: POST /shelves/{shelfId}/boxes 는 {@code spaceExternalId}를 {@code @PathVariable}이 아닌
 * {@code @RequestParam}으로 받는다.
 */
class BoxControllerTest extends AbstractControllerTest {

  @MockitoBean private BoxService boxService;

  @MockitoBean private ShelfService shelfService;

  private static final String SPACE_SHELF_BOX_PATH =
      "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes/" + BOX_ID;

  @BeforeEach
  void stubDefaults() {
    given(boxService.findByExternalId(any(), any(), any(), anyString())).willReturn(stubBox());
    given(boxService.findAllByShelfId(any(), any(), anyString())).willReturn(List.of());
    given(shelfService.findByExternalId(any(), any(), anyString())).willReturn(stubShelf());
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
    // editModal과 create는 별도 개별 테스트로 커버
    return Stream.of(
        Arguments.of("PATCH", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes/" + BOX_ID),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes/new"),
        Arguments.of(
            "DELETE", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes/" + BOX_ID));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/{shid}/boxes/{id}/edit 미인증 → /login")
  void editModal_whenUnauthenticated_redirectsToLogin() throws Exception {
    mockMvc
        .perform(get(SPACE_SHELF_BOX_PATH + "/edit"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("POST /shelves/{shid}/boxes 미인증 → /login")
  void create_whenUnauthenticated_redirectsToLogin() throws Exception {
    mockMvc
        .perform(
            post("/shelves/" + SHELF_ID + "/boxes")
                .with(csrf())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("name", "박스"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  // ── Response Shape: GET 엔드포인트 ────────────────────────────────────

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/{shid}/boxes/{id}/edit → edit-modal 프래그먼트")
  void editModal_returnsEditModalFragment() throws Exception {
    mockMvc
        .perform(get(SPACE_SHELF_BOX_PATH + "/edit").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("boxes/fragments/modal :: edit-modal"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/{shid}/boxes/new → modal 프래그먼트")
  void newModal_returnsModalFragment() throws Exception {
    mockMvc
        .perform(
            get("/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes/new")
                .with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("boxes/fragments/modal :: modal"));
  }

  // ── Response Shape: 변경 엔드포인트 ──────────────────────────────────

  @Test
  @DisplayName("PATCH /spaces/.../boxes/{id} - 정상 name → box-list-response 프래그먼트 + HX-Trigger")
  void rename_withValidName_returnsBoxListResponseWithToast() throws Exception {
    mockMvc
        .perform(
            patch(SPACE_SHELF_BOX_PATH).with(user("testuser")).with(csrf()).param("name", "수정된 박스"))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/box-list :: box-list-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("POST /shelves/{shid}/boxes - 정상 name → box-list-response 프래그먼트 + HX-Trigger")
  void create_withValidName_returnsBoxListResponseWithToast() throws Exception {
    given(boxService.create(any(), any(), any(), anyString())).willReturn(stubBox());

    mockMvc
        .perform(
            post("/shelves/" + SHELF_ID + "/boxes")
                .with(user("testuser"))
                .with(csrf())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("name", "새 박스"))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/box-list :: box-list-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("DELETE /spaces/.../boxes/{id} → box-list-container 프래그먼트 + HX-Trigger")
  void delete_returnsBoxListContainerWithToast() throws Exception {
    mockMvc
        .perform(delete(SPACE_SHELF_BOX_PATH).with(user("testuser")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("shelves/fragments/box-list :: box-list-container"))
        .andExpect(hasToastTrigger());
  }

  // ── Validation ────────────────────────────────────────────────────────

  @Test
  @DisplayName("PATCH /spaces/.../boxes/{id} - 빈 name → edit-modal (유효성 실패, HX-Trigger 없음)")
  void rename_withBlankName_returnsEditModalWithoutToast() throws Exception {
    mockMvc
        .perform(patch(SPACE_SHELF_BOX_PATH).with(user("testuser")).with(csrf()).param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("boxes/fragments/modal :: edit-modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /shelves/{shid}/boxes - 빈 name → modal (유효성 실패, HX-Trigger 없음)")
  void create_withBlankName_returnsModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/shelves/" + SHELF_ID + "/boxes")
                .with(user("testuser"))
                .with(csrf())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("name", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("boxes/fragments/modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────

  private BoxDTO stubBox() {
    BoxDTO dto = new BoxDTO();
    dto.setId(1L);
    dto.setExternalId(BOX_ID);
    dto.setShelfId(1L);
    dto.setName("테스트 박스");
    return dto;
  }

  private ShelfDTO stubShelf() {
    ShelfDTO dto = new ShelfDTO();
    dto.setId(1L);
    dto.setExternalId(SHELF_ID);
    dto.setSpaceId(1L);
    dto.setName("테스트 선반");
    return dto;
  }
}
