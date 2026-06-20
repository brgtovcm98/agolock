package com.seu.seustock.configuration;

import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

  /** 로그인 식별자(이메일). */
  @ModelAttribute("currentUsername")
  public String currentUsername(Principal principal) {
    return principal != null ? principal.getName() : null;
  }

  /** 화면에 표시할 닉네임. principal에서 바로 읽어 페이지마다 DB를 조회하지 않는다. */
  @ModelAttribute("currentNickname")
  public String currentNickname(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      return user.getNickname();
    }
    return null;
  }

  @ModelAttribute("_csrf")
  public CsrfToken csrfToken(CsrfToken csrfToken) {
    csrfToken.getToken();
    return csrfToken;
  }
}
