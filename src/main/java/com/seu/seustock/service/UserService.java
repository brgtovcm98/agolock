package com.seu.seustock.service;

import com.seu.seustock.configuration.AuthenticatedUser;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.LoginForm;
import com.seu.seustock.model.form.UserRegistrationForm;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  public boolean existsByEmail(String email) {
    return userMapper.findByEmail(normalizeEmail(email)).isPresent();
  }

  public void register(UserRegistrationForm form) {
    UserDTO user = new UserDTO();
    user.setEmail(normalizeEmail(form.getEmail()));
    user.setNickname(form.getNickname());
    user.setPassword(passwordEncoder.encode(form.getPassword()));
    userMapper.insertUser(user);
    log.info("user registered userId={}", user.getId());
  }

  public Optional<UserDTO> authenticate(LoginForm form) {
    return userMapper
        .findByEmail(normalizeEmail(form.getEmail()))
        .filter(user -> passwordEncoder.matches(form.getPassword(), user.getPassword()));
  }

  public void updateNickname(String email, String nickname) {
    UserDTO user =
        userMapper
            .findByEmail(normalizeEmail(email))
            .orElseThrow(() -> new IllegalArgumentException("error.user.notFound"));
    user.setNickname(nickname);
    userMapper.updateNickname(user);
    refreshPrincipalNickname(email, nickname);
    log.info("user nickname updated userId={}", user.getId());
  }

  public void updatePassword(String email, String currentPassword, String newPassword) {
    UserDTO user =
        userMapper
            .findByEmail(normalizeEmail(email))
            .orElseThrow(() -> new IllegalArgumentException("error.user.notFound"));
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new IllegalArgumentException("error.password.incorrect");
    }
    user.setPassword(passwordEncoder.encode(newPassword));
    userMapper.updatePassword(user);
    log.info("user password updated userId={}", user.getId());
  }

  private void refreshPrincipalNickname(String email, String nickname) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser)) {
      return;
    }
    AuthenticatedUser current = (AuthenticatedUser) authentication.getPrincipal();
    AuthenticatedUser updated =
        new AuthenticatedUser(email, current.getPassword(), nickname, current.getAuthorities());
    Authentication updatedAuth =
        new UsernamePasswordAuthenticationToken(
            updated, authentication.getCredentials(), updated.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(updatedAuth);
  }

  private static String normalizeEmail(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase();
  }
}
