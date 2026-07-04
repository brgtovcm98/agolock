package com.seu.seustock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDetailDTO;
import com.seu.seustock.model.enumeration.StockStatus;
import com.seu.seustock.model.pagination.PageResult;
import com.seu.seustock.service.BoxService;
import com.seu.seustock.service.ItemService;
import com.seu.seustock.service.ShelfService;
import com.seu.seustock.service.SpaceService;
import com.seu.seustock.service.StockService;
import java.time.LocalDateTime;
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
 * StockController 웹 계층 테스트.
 *
 * <p>검증 범위:
 *
 * <ul>
 *   <li>Security: 21개 엔드포인트 미인증 → /login 리다이렉트
 *   <li>Response Shape: 패널 조회(4경로 × append 분기), 모달 GET, 변경 엔드포인트 뷰/헤더
 *   <li>Validation: StockForm count 범위, QuickStockForm name, StockInOutForm count, StockMoveForm
 *       items @NotEmpty, StockUpdateForm serialNumber 길이
 * </ul>
 *
 * 특이사항: 모든 변경 엔드포인트는 성공/실패 여부와 관계없이 HX-Trigger 헤더를 포함한 Thymeleaf 프래그먼트를 반환한다.
 */
class StockControllerTest extends AbstractControllerTest {

  @MockitoBean private StockService stockService;

  @MockitoBean private SpaceService spaceService;

  @MockitoBean private ShelfService shelfService;

  @MockitoBean private BoxService boxService;

  @MockitoBean private ItemService itemService;

  private static final String PANEL_SPACE_PATH = "/spaces/" + SPACE_ID + "/stocks";
  private static final String PANEL_SPACE_ALL_PATH = "/spaces/" + SPACE_ID + "/stocks/all";
  private static final String PANEL_SHELF_PATH =
      "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/stocks";
  private static final String PANEL_BOX_PATH =
      "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes/" + BOX_ID + "/stocks";
  private static final String STOCK_PATH = "/stocks/" + STOCK_ID;

  @BeforeEach
  void stubDefaults() {
    // 재고 상세 목록 (GET /stocks)
    given(
            stockService.searchDetailsPage(
                any(), any(), any(), any(), any(), any(), any(), anyString(), any()))
        .willReturn(new PageResult<>(List.of(), 1, 10, 0));

    // 단일 재고 상세 (editRow, cancelEdit, updateRow 오류 분기)
    given(stockService.findDetailByExternalId(any(), anyString())).willReturn(stubStockDetail());
    given(stockService.updateDetails(any(), any(), anyString())).willReturn(stubStockDetail());

    // 패널 페이지
    given(stockService.findPanelPageBySpaceAll(any(), any(), any(), anyString(), any()))
        .willReturn(new PageResult<>(List.of(), 1, 10, 0));
    given(stockService.findPanelPageBySpace(any(), anyString(), any()))
        .willReturn(new PageResult<>(List.of(), 1, 10, 0));
    given(stockService.findPanelPageByShelf(any(), any(), anyString(), any()))
        .willReturn(new PageResult<>(List.of(), 1, 10, 0));
    given(stockService.findPanelPageByBox(any(), any(), any(), anyString(), any()))
        .willReturn(new PageResult<>(List.of(), 1, 10, 0));

    // 메모 제안 (action-form)
    given(stockService.findMemoSuggestions(any(), anyString())).willReturn(List.of());

    // 공간·선반·박스 단건·목록
    given(spaceService.findByExternalId(any(), anyString())).willReturn(stubSpace());
    given(shelfService.findByExternalId(any(), any(), anyString())).willReturn(stubShelf());
    given(shelfService.findAllBySpaceId(any(), anyString())).willReturn(List.of());
    given(boxService.findByExternalId(any(), any(), any(), anyString())).willReturn(stubBox());
    given(boxService.findAllByShelfId(any(), any(), anyString())).willReturn(List.of());

    // 품목 목록 (newModal)
    given(itemService.findAllByUsername(anyString())).willReturn(List.of());
    // 품목 단건 (in-form 가격 prefill)
    given(itemService.findByExternalId(any(), anyString())).willReturn(stubItem());
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
        Arguments.of("GET", "/stocks"),
        Arguments.of("GET", "/stocks/" + STOCK_ID + "/edit"),
        Arguments.of("PUT", "/stocks/" + STOCK_ID),
        Arguments.of("GET", "/stocks/" + STOCK_ID + "/cancel"),
        Arguments.of("GET", "/stocks/" + STOCK_ID + "/memo"),
        Arguments.of("GET", "/stocks/" + STOCK_ID + "/memo/edit"),
        Arguments.of("PUT", "/stocks/" + STOCK_ID + "/memo"),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/stocks/all"),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/stocks"),
        Arguments.of("GET", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/stocks"),
        Arguments.of(
            "GET", "/spaces/" + SPACE_ID + "/shelves/" + SHELF_ID + "/boxes/" + BOX_ID + "/stocks"),
        Arguments.of("GET", "/stocks/new?spaceId=" + SPACE_ID),
        Arguments.of("POST", "/stocks"),
        Arguments.of("GET", "/stocks/quick?spaceId=" + SPACE_ID),
        Arguments.of("POST", "/stocks/quick"),
        Arguments.of("DELETE", "/stocks?item=" + ITEM_ID + "&space=" + SPACE_ID),
        Arguments.of("DELETE", "/stocks/" + STOCK_ID),
        Arguments.of(
            "GET", "/stocks/action-form?item=" + ITEM_ID + "&itemName=test&space=" + SPACE_ID),
        Arguments.of("GET", "/stocks/in-form?item=" + ITEM_ID + "&space=" + SPACE_ID),
        Arguments.of("POST", "/stocks/in"),
        Arguments.of("GET", "/stocks/out-form?item=" + ITEM_ID + "&space=" + SPACE_ID),
        Arguments.of("POST", "/stocks/out"),
        Arguments.of("GET", "/stocks/move-form"),
        Arguments.of("POST", "/stocks/move"));
  }

  // ── Response Shape: GET /stocks (목록) ──────────────────────────────────

  @Test
  @DisplayName("GET /stocks - 인증 → 200, stocks/list 뷰")
  void list_authenticated_returns200WithStocksListView() throws Exception {
    mockMvc
        .perform(get("/stocks").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/list"))
        .andExpect(model().attributeExists("stocks", "page"));
  }

  // ── Response Shape: 재고 상세 행 편집 ─────────────────────────────────────

  @Test
  @DisplayName("GET /stocks/{id}/edit → detail-row :: edit 프래그먼트")
  void editRow_returnsEditFragment() throws Exception {
    mockMvc
        .perform(get(STOCK_PATH + "/edit").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/detail-row :: edit"));
  }

  @Test
  @DisplayName("GET /stocks/{id}/cancel → detail-row :: view 프래그먼트")
  void cancelEdit_returnsViewFragment() throws Exception {
    mockMvc
        .perform(get(STOCK_PATH + "/cancel").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/detail-row :: view"));
  }

  @Test
  @DisplayName("PUT /stocks/{id} - 정상 → stock-detail-section 프래그먼트 + HX-Trigger")
  void updateRow_withValidForm_returnsListSectionWithToast() throws Exception {
    mockMvc
        .perform(
            put(STOCK_PATH).with(user("testuser")).with(csrf()).param("serialNumber", "SN-001"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/list :: stock-detail-section"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName(
      "PUT /stocks/{id} - serialNumber 256자 → stock-detail-section 프래그먼트 (유효성 실패, HX-Trigger 없음)")
  void updateRow_withTooLongSerialNumber_returnsListSectionWithoutToast() throws Exception {
    mockMvc
        .perform(
            put(STOCK_PATH)
                .with(user("testuser"))
                .with(csrf())
                .param("serialNumber", "a".repeat(256)))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/list :: stock-detail-section"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  // ── Response Shape: 재고 메모 모달 ─────────────────────────────────────

  @Test
  @DisplayName("GET /stocks/{id}/memo → memo-modal :: view 프래그먼트")
  void viewMemo_returnsViewFragment() throws Exception {
    mockMvc
        .perform(get(STOCK_PATH + "/memo").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/memo-modal :: view"))
        .andExpect(model().attributeExists("stock"));
  }

  @Test
  @DisplayName("GET /stocks/{id}/memo/edit → memo-modal :: edit 프래그먼트")
  void editMemo_returnsEditFragment() throws Exception {
    mockMvc
        .perform(get(STOCK_PATH + "/memo/edit").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/memo-modal :: edit"))
        .andExpect(model().attributeExists("stock"));
  }

  @Test
  @DisplayName("PUT /stocks/{id}/memo - 정상 → stock-detail-section 프래그먼트 + HX-Trigger")
  void updateMemo_withValidParam_returnsListSectionWithToast() throws Exception {
    mockMvc
        .perform(
            put(STOCK_PATH + "/memo").with(user("testuser")).with(csrf()).param("memo", "새 메모 내용"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/list :: stock-detail-section"))
        .andExpect(hasToastTrigger());
  }

  // ── Response Shape: 패널 조회 ──────────────────────────────────────────

  @Test
  @DisplayName("GET /spaces/{sid}/stocks → stock-panel 프래그먼트")
  void panelBySpace_returnsStockPanelFragment() throws Exception {
    mockMvc
        .perform(get(PANEL_SPACE_PATH).with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/stocks?append=true → stock-panel-more-response 프래그먼트")
  void panelBySpace_withAppendTrue_returnsMoreResponseFragment() throws Exception {
    mockMvc
        .perform(get(PANEL_SPACE_PATH).with(user("testuser")).param("append", "true"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-more-response"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/stocks/all → stock-panel 프래그먼트")
  void panelBySpaceAll_returnsStockPanelFragment() throws Exception {
    mockMvc
        .perform(get(PANEL_SPACE_ALL_PATH).with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/stocks/all?append=true → stock-panel-more-response 프래그먼트")
  void panelBySpaceAll_withAppendTrue_returnsMoreResponseFragment() throws Exception {
    mockMvc
        .perform(get(PANEL_SPACE_ALL_PATH).with(user("testuser")).param("append", "true"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-more-response"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/{shid}/stocks → stock-panel 프래그먼트")
  void panelByShelf_returnsStockPanelFragment() throws Exception {
    mockMvc
        .perform(get(PANEL_SHELF_PATH).with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel"));
  }

  @Test
  @DisplayName("GET /spaces/{sid}/shelves/{shid}/boxes/{bid}/stocks → stock-panel 프래그먼트")
  void panelByBox_returnsStockPanelFragment() throws Exception {
    mockMvc
        .perform(get(PANEL_BOX_PATH).with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel"));
  }

  // ── Response Shape: 모달 GET 엔드포인트 ──────────────────────────────────

  @Test
  @DisplayName("GET /stocks/new?spaceId=... → modal :: modal 프래그먼트")
  void newModal_returnsModalFragment() throws Exception {
    mockMvc
        .perform(get("/stocks/new").with(user("testuser")).param("spaceId", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/modal :: modal"));
  }

  @Test
  @DisplayName("GET /stocks/quick?spaceId=... → quick-modal :: modal 프래그먼트")
  void quickModal_returnsQuickModalFragment() throws Exception {
    mockMvc
        .perform(get("/stocks/quick").with(user("testuser")).param("spaceId", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/quick-modal :: modal"));
  }

  @Test
  @DisplayName("GET /stocks/action-form → action-modal :: modal 프래그먼트")
  void actionForm_returnsActionModalFragment() throws Exception {
    mockMvc
        .perform(
            get("/stocks/action-form")
                .with(user("testuser"))
                .param("item", ITEM_ID.toString())
                .param("itemName", "테스트 품목")
                .param("space", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/action-modal :: modal"));
  }

  @Test
  @DisplayName("GET /stocks/in-form → in-modal :: modal 프래그먼트")
  void inForm_returnsInModalFragment() throws Exception {
    mockMvc
        .perform(
            get("/stocks/in-form")
                .with(user("testuser"))
                .param("item", ITEM_ID.toString())
                .param("space", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/in-modal :: modal"));
  }

  @Test
  @DisplayName("GET /stocks/out-form → out-modal :: modal 프래그먼트")
  void outForm_returnsOutModalFragment() throws Exception {
    mockMvc
        .perform(
            get("/stocks/out-form")
                .with(user("testuser"))
                .param("item", ITEM_ID.toString())
                .param("space", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/out-modal :: modal"));
  }

  @Test
  @DisplayName("GET /stocks/move-form → move-modal :: modal 프래그먼트")
  void moveForm_returnsMoveModalFragment() throws Exception {
    mockMvc
        .perform(
            get("/stocks/move-form")
                .with(user("testuser"))
                .param("sourceSpaceExternalId", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/move-modal :: modal"));
  }

  // ── Response Shape: 변경 엔드포인트 ──────────────────────────────────────

  @Test
  @DisplayName("POST /stocks - 정상 → stock-panel-response 프래그먼트 + HX-Trigger")
  void create_withValidForm_returnsPanelResponseWithToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks")
                .with(user("testuser"))
                .with(csrf())
                .param("itemExternalId", ITEM_ID.toString())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("POST /stocks/quick - 정상 name → stock-panel-response 프래그먼트 + HX-Trigger")
  void createQuick_withValidForm_returnsPanelResponseWithToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/quick")
                .with(user("testuser"))
                .with(csrf())
                .param("name", "새 품목")
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("DELETE /stocks - 정상 → stock-panel-response 프래그먼트 + HX-Trigger")
  void delete_withValidParams_returnsPanelResponseWithToast() throws Exception {
    mockMvc
        .perform(
            delete("/stocks")
                .with(user("testuser"))
                .with(csrf())
                .param("item", ITEM_ID.toString())
                .param("space", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("DELETE /stocks/{id} → stock-detail-section 프래그먼트 + HX-Trigger")
  void deleteRow_returnsListSectionWithToast() throws Exception {
    mockMvc
        .perform(delete(STOCK_PATH).with(user("testuser")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/list :: stock-detail-section"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("POST /stocks/in - 정상 → stock-panel-response 프래그먼트 + HX-Trigger")
  void processIn_withValidForm_returnsPanelResponseWithToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/in")
                .with(user("testuser"))
                .with(csrf())
                .param("itemExternalId", ITEM_ID.toString())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("POST /stocks/out - 정상 → stock-panel-response 프래그먼트 + HX-Trigger")
  void processOut_withValidForm_returnsPanelResponseWithToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/out")
                .with(user("testuser"))
                .with(csrf())
                .param("itemExternalId", ITEM_ID.toString())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-response"))
        .andExpect(hasToastTrigger());
  }

  @Test
  @DisplayName("POST /stocks/move - 정상 → stock-panel-response 프래그먼트 + HX-Trigger")
  void processMove_withValidForm_returnsPanelResponseWithToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/move")
                .with(user("testuser"))
                .with(csrf())
                .param("sourceSpaceExternalId", SPACE_ID.toString())
                .param("targetSpaceExternalId", SPACE_ID.toString())
                .param("items[0].itemExternalId", ITEM_ID.toString())
                .param("items[0].count", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/panel :: stock-panel-response"))
        .andExpect(hasToastTrigger());
  }

  // ── Validation ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /stocks - itemExternalId 없음 → modal (유효성 실패, HX-Trigger 없음)")
  void create_withNullItem_returnsModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks")
                .with(user("testuser"))
                .with(csrf())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /stocks - count=0 → modal (유효성 실패, HX-Trigger 없음)")
  void create_withCountZero_returnsModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks")
                .with(user("testuser"))
                .with(csrf())
                .param("itemExternalId", ITEM_ID.toString())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "0"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /stocks - count=51 → modal (유효성 실패, HX-Trigger 없음)")
  void create_withCountOverMax_returnsModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks")
                .with(user("testuser"))
                .with(csrf())
                .param("itemExternalId", ITEM_ID.toString())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "51"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /stocks/quick - 빈 name → quick-modal (유효성 실패, HX-Trigger 없음)")
  void createQuick_withBlankName_returnsQuickModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/quick")
                .with(user("testuser"))
                .with(csrf())
                .param("name", "")
                .param("spaceExternalId", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/quick-modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /stocks/in - count=0 → in-modal (유효성 실패, HX-Trigger 없음)")
  void processIn_withCountZero_returnsInModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/in")
                .with(user("testuser"))
                .with(csrf())
                .param("itemExternalId", ITEM_ID.toString())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "0"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/in-modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /stocks/out - count=0 → out-modal (유효성 실패, HX-Trigger 없음)")
  void processOut_withCountZero_returnsOutModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/out")
                .with(user("testuser"))
                .with(csrf())
                .param("itemExternalId", ITEM_ID.toString())
                .param("spaceExternalId", SPACE_ID.toString())
                .param("count", "0"))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/out-modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  @Test
  @DisplayName("POST /stocks/move - items 빈 목록 → move-modal (유효성 실패, HX-Trigger 없음)")
  void processMove_withEmptyItems_returnsMoveModalWithoutToast() throws Exception {
    mockMvc
        .perform(
            post("/stocks/move")
                .with(user("testuser"))
                .with(csrf())
                .param("sourceSpaceExternalId", SPACE_ID.toString())
                .param("targetSpaceExternalId", SPACE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("stocks/fragments/move-modal :: modal"))
        .andExpect(header().doesNotExist("HX-Trigger"));
  }

  // ── 헬퍼 ───────────────────────────────────────────────────────────────

  private StockDetailDTO stubStockDetail() {
    StockDetailDTO dto = new StockDetailDTO();
    dto.setExternalId(STOCK_ID);
    dto.setItemExternalId(ITEM_ID);
    dto.setItemName("테스트 품목");
    dto.setSpaceExternalId(SPACE_ID);
    dto.setSpaceName("테스트 공간");
    dto.setStatus(StockStatus.IN_STOCK);
    dto.setCreatedAt(LocalDateTime.now());
    return dto;
  }

  private ItemDTO stubItem() {
    ItemDTO dto = new ItemDTO();
    dto.setId(1L);
    dto.setExternalId(ITEM_ID);
    dto.setUserId(1L);
    dto.setName("테스트 품목");
    dto.setPrice(new java.math.BigDecimal("5000"));
    return dto;
  }

  private SpaceDTO stubSpace() {
    SpaceDTO dto = new SpaceDTO();
    dto.setId(1L);
    dto.setExternalId(SPACE_ID);
    dto.setUserId(1L);
    dto.setName("테스트 공간");
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

  private BoxDTO stubBox() {
    BoxDTO dto = new BoxDTO();
    dto.setId(1L);
    dto.setExternalId(BOX_ID);
    dto.setShelfId(1L);
    dto.setName("테스트 박스");
    return dto;
  }
}
