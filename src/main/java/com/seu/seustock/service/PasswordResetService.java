package com.seu.seustock.service;

import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 이메일 기반 비밀번호 재설정 흐름의 핵심 로직. 계정 존재 여부를 노출하지 않도록(anti-enumeration) 재설정 요청은 항상 조용히 처리한다. */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

  private final UserMapper userMapper;
  private final PasswordResetTokenStore tokenStore;
  private final PasswordResetMailSender mailSender;
  private final PasswordEncoder passwordEncoder;
  private final MessageSource messageSource;
  private final String baseUrl;

  public PasswordResetService(
      UserMapper userMapper,
      PasswordResetTokenStore tokenStore,
      PasswordResetMailSender mailSender,
      PasswordEncoder passwordEncoder,
      MessageSource messageSource,
      @Value("${app.base-url}") String baseUrl) {
    this.userMapper = userMapper;
    this.tokenStore = tokenStore;
    this.mailSender = mailSender;
    this.passwordEncoder = passwordEncoder;
    this.messageSource = messageSource;
    this.baseUrl = baseUrl;
  }

  private String getMsg(String key) {
    Locale locale = LocaleContextHolder.getLocale();
    return messageSource.getMessage(key, null, locale);
  }

  /** 재설정 메일을 요청한다. 계정이 존재하고 재발송 쿨다운 중이 아니면 토큰을 발급하고 메일을 보낸다. 그 외에는 아무 동작도 하지 않는다(계정 존재 여부 비노출). */
  public void requestReset(String email) {
    String normalizedEmail = normalizeEmail(email);
    UserDTO user = userMapper.findByEmail(normalizedEmail).orElse(null);
    if (user == null) {
      log.info("password reset request skipped reason=user_not_found");
      return;
    }
    if (tokenStore.onCooldown(normalizedEmail)) {
      log.info("password reset request skipped userId={} reason=cooldown", user.getId());
      return;
    }
    String token = tokenStore.issue(normalizedEmail);
    tokenStore.startCooldown(normalizedEmail);
    try {
      mailSender.send(normalizedEmail, baseUrl + "/password/reset?token=" + token);
      log.info("password reset requested userId={}", user.getId());
    } catch (RuntimeException e) {
      log.error("password reset mail failed userId={}", user.getId(), e);
      throw e;
    }
  }

  /** 토큰이 유효한지(만료/미존재가 아닌지) 확인한다. */
  public boolean validateToken(String token) {
    return tokenStore.emailFor(token).isPresent();
  }

  /**
   * 토큰을 검증하고 새 비밀번호로 교체한 뒤 토큰을 폐기한다.
   *
   * @throws IllegalArgumentException 토큰이 유효하지 않거나 만료된 경우
   */
  public void resetPassword(String token, String rawPassword) {
    String normalizedEmail =
        normalizeEmail(
            tokenStore
                .emailFor(token)
                .orElseThrow(
                    () -> new IllegalArgumentException(getMsg("error.passwordReset.invalidLink"))));
    UserDTO user =
        userMapper
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () -> new IllegalArgumentException(getMsg("error.passwordReset.invalidLink")));
    user.setPassword(passwordEncoder.encode(rawPassword));
    userMapper.updatePassword(user);
    tokenStore.consume(token);
    log.info("password reset completed userId={}", user.getId());
  }

  private static String normalizeEmail(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase();
  }
}
