package com.seu.seustock.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.seu.seustock.model.dto.DashboardSummaryDTO;
import com.seu.seustock.service.DashboardService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * IndexController(홈 대시보드) 웹 계층 테스트.
 *
 * <p>주의: Spring Security 7.x에서 {@code SecurityContextHolderFilter}가 SecurityContext를 덮어쓰므로
 * {@code @WithMockUser} 대신 {@code .with(user(...))} 방식을 사용한다.
 */
class IndexControllerTest extends AbstractControllerTest {

  @MockitoBean private DashboardService dashboardService;

  private DashboardSummaryDTO summary() {
    DashboardSummaryDTO s = new DashboardSummaryDTO();
    s.setTotalValue(BigDecimal.ZERO);
    return s;
  }

  @Test
  @DisplayName("GET / - 미인증 → /login 리다이렉트")
  void getRoot_whenUnauthenticated_redirectsToLogin() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("GET / - 인증 상태 → 200, dashboard 뷰")
  void getRoot_whenAuthenticated_returns200WithDashboardView() throws Exception {
    when(dashboardService.getSummary("testuser")).thenReturn(summary());
    when(dashboardService.getSpaceSnapshot("testuser")).thenReturn(List.of());
    when(dashboardService.getRecentActivity("testuser")).thenReturn(List.of());

    mockMvc
        .perform(get("/").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("dashboard"));
  }

  @Test
  @DisplayName("POST /dashboard/target - 유효한 목표 → 저장 후 / 리다이렉트")
  void postTarget_valid_savesAndRedirects() throws Exception {
    mockMvc
        .perform(
            post("/dashboard/target").with(user("testuser")).with(csrf()).param("target", "100"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"))
        .andExpect(flash().attribute("toastType", "success"));

    verify(dashboardService).updateTarget("testuser", 100);
  }

  @Test
  @DisplayName("POST /dashboard/target - 음수 목표 → 검증 실패, dashboard 뷰 재렌더")
  void postTarget_negative_returnsDashboardWithErrors() throws Exception {
    when(dashboardService.getSummary("testuser")).thenReturn(summary());
    when(dashboardService.getSpaceSnapshot("testuser")).thenReturn(List.of());
    when(dashboardService.getRecentActivity("testuser")).thenReturn(List.of());

    mockMvc
        .perform(
            post("/dashboard/target").with(user("testuser")).with(csrf()).param("target", "-1"))
        .andExpect(status().isOk())
        .andExpect(view().name("dashboard"))
        .andExpect(model().attributeHasFieldErrors("targetForm", "target"));
  }

  @Test
  @DisplayName("GET /empty - 인증 상태 → 200, modal div 포함")
  void getEmpty_whenAuthenticated_returns200WithModalDiv() throws Exception {
    mockMvc
        .perform(get("/empty").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("modal")));
  }
}
