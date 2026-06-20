package com.seu.seustock.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IndexController 웹 계층 테스트.
 *
 * <p>IndexController는 서비스 의존성이 없으므로 {@code @MockitoBean} 불필요.
 *
 * <p>주의: Spring Security 7.x에서 {@code SecurityContextHolderFilter}가 필터 처리 시 SecurityContext를 덮어쓰기
 * 때문에 {@code @WithMockUser} 대신 {@code .with(user(...))} 방식을 사용한다.
 */
class IndexControllerTest extends AbstractControllerTest {

  @Test
  @DisplayName("GET / - 미인증 → /login 리다이렉트")
  void getRoot_whenUnauthenticated_redirectsToLogin() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("GET / - 인증 상태 → 200, index 뷰")
  void getRoot_whenAuthenticated_returns200WithIndexView() throws Exception {
    mockMvc
        .perform(get("/").with(user("testuser")))
        .andExpect(status().isOk())
        .andExpect(view().name("index"));
  }

  @Test
  @DisplayName("GET /empty - 미인증 → /login 리다이렉트")
  void getEmpty_whenUnauthenticated_redirectsToLogin() throws Exception {
    mockMvc
        .perform(get("/empty"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
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
