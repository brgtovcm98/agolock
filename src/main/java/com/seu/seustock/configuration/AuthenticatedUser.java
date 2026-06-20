package com.seu.seustock.configuration;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Spring Security principal. {@code username}(= {@link #getUsername()})은 로그인 식별자인 이메일이며, 표시용 {@code
 * nickname}을 함께 보유한다. 덕분에 네비게이션 바 등에서 페이지마다 DB를 조회하지 않고 principal에서 바로 닉네임을 읽을 수 있다.
 */
public class AuthenticatedUser extends User {

  private final String nickname;

  public AuthenticatedUser(
      String email,
      String password,
      String nickname,
      Collection<? extends GrantedAuthority> authorities) {
    super(email, password, authorities);
    this.nickname = nickname;
  }

  public String getNickname() {
    return nickname;
  }
}
