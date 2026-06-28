package com.seu.seustock.service;

import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import java.util.NoSuchElementException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 서비스 계층 공통 헬퍼를 제공하는 추상 기반 클래스.
 *
 * <p>{@code getUser}, {@code getMsg}, {@code blankToNull} 등 반복되던 보일러플레이트를 한 곳에 모아 서비스 클래스의 가독성과
 * 유지보수성을 높입니다.
 */
public abstract class BaseService {

  protected final UserMapper userMapper;
  protected final MessageSource messageSource;

  protected BaseService(UserMapper userMapper, MessageSource messageSource) {
    this.userMapper = userMapper;
    this.messageSource = messageSource;
  }

  protected UserDTO getUser(String username) {
    return userMapper
        .findByEmail(username)
        .orElseThrow(() -> new NoSuchElementException(getMsg("error.user.notFound")));
  }

  protected String getMsg(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  protected static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
