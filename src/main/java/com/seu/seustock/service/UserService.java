package com.seu.seustock.service;

import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.LoginForm;
import com.seu.seustock.model.form.UserRegistrationForm;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private static String normalizeEmail(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase();
  }
}
