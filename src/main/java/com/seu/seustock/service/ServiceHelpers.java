package com.seu.seustock.service;

import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 서비스 계층 공통 헬퍼.
 *
 * <p>여러 서비스에서 반복되던 {@code getUser}, {@code getMsg}, {@code blankToNull}을 한 곳으로 모아 중복을 제거합니다. 각 서비스는
 * 이 컴포넌트를 주입받아 사용합니다.
 */
@Component
@RequiredArgsConstructor
public class ServiceHelpers {

  private final UserMapper userMapper;
  private final MessageSource messageSource;

  public UserDTO getUser(String username) {
    return userMapper
        .findByEmail(username)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
  }

  public String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  public static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
