package com.seu.seustock.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.seu.seustock.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AccountControllerTest extends AbstractControllerTest {

  @MockitoBean private UserService userService;

  @Test
  @DisplayName("GET /account - 인증된 사용자 → 200 account 뷰")
  void getAccount_whenAuthenticated_returns200() throws Exception {
    mockMvc
        .perform(get("/account").with(user("test@test.com")))
        .andExpect(status().isOk())
        .andExpect(view().name("account"))
        .andExpect(model().attributeExists("nicknameForm", "passwordForm"));
  }

  @Test
  @DisplayName("GET /account - 미인증 → 302 login 리다이렉트")
  void getAccount_whenUnauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/account")).andExpect(status().is3xxRedirection());
  }

  @Test
  @DisplayName("POST /account/nickname - 정상 → /account 리다이렉트 + flash toast")
  void postUpdateNickname_withValidForm_redirectsWithToast() throws Exception {
    doNothing().when(userService).updateNickname(anyString(), anyString());

    mockMvc
        .perform(
            post("/account/nickname")
                .with(csrf())
                .with(user("test@test.com"))
                .param("nickname", "새닉네임"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("toastMessage"));
  }

  @Test
  @DisplayName("POST /account/nickname - 닉네임 1자 → account 뷰")
  void postUpdateNickname_withTooShortNickname_returnsAccountView() throws Exception {
    mockMvc
        .perform(
            post("/account/nickname")
                .with(csrf())
                .with(user("test@test.com"))
                .param("nickname", "a"))
        .andExpect(status().isOk())
        .andExpect(view().name("account"));
  }

  @Test
  @DisplayName("POST /account/nickname - CSRF 없음 → 403")
  void postUpdateNickname_withoutCsrf_returns403() throws Exception {
    mockMvc
        .perform(post("/account/nickname").with(user("test@test.com")).param("nickname", "닉네임"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /account/password - 정상 → /account 리다이렉트 + flash toast")
  void postUpdatePassword_withValidForm_redirectsWithToast() throws Exception {
    doNothing().when(userService).updatePassword(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            post("/account/password")
                .with(csrf())
                .with(user("test@test.com"))
                .param("currentPassword", "oldpass123")
                .param("newPassword", "newpass123")
                .param("newPasswordConfirm", "newpass123"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("toastMessage"));
  }

  @Test
  @DisplayName("POST /account/password - 새 비밀번호 불일치 → account 뷰")
  void postUpdatePassword_withMismatchedNewPasswords_returnsAccountView() throws Exception {
    mockMvc
        .perform(
            post("/account/password")
                .with(csrf())
                .with(user("test@test.com"))
                .param("currentPassword", "oldpass123")
                .param("newPassword", "newpass123")
                .param("newPasswordConfirm", "different1"))
        .andExpect(status().isOk())
        .andExpect(view().name("account"));
  }

  @Test
  @DisplayName("POST /account/password - 현재 비밀번호 틀림 → account 뷰")
  void postUpdatePassword_withWrongCurrentPassword_returnsAccountView() throws Exception {
    willThrow(new IllegalArgumentException("error.password.incorrect"))
        .given(userService)
        .updatePassword(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            post("/account/password")
                .with(csrf())
                .with(user("test@test.com"))
                .param("currentPassword", "wrongpass")
                .param("newPassword", "newpass123")
                .param("newPasswordConfirm", "newpass123"))
        .andExpect(status().isOk())
        .andExpect(view().name("account"));
  }
}
