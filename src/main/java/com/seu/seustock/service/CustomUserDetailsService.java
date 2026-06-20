package com.seu.seustock.service;

import com.seu.seustock.configuration.AuthenticatedUser;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserMapper userMapper;

  /** Spring Security form login은 입력된 식별자(여기서는 이메일)를 {@code username} 인자로 넘긴다. */
  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    UserDTO user =
        userMapper
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    return new AuthenticatedUser(
        user.getEmail(),
        user.getPassword(),
        user.getNickname(),
        List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }
}
