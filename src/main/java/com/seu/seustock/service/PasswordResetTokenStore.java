package com.seu.seustock.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 토큰을 Redis에 저장/조회한다. 토큰은 TTL이 지나면 자동 만료되며, 사용 즉시 삭제(1회용)한다. 재발송 남용을 막기 위해 이메일 단위의 쿨다운 키도
 * 관리한다.
 *
 * <p>{@code StringRedisTemplate}은 세션 저장소 설정(store-type)과 무관하게 {@code spring.data.redis.*}가 설정되어 있으면
 * 자동 구성된다.
 */
@Component
public class PasswordResetTokenStore {

  private static final String TOKEN_PREFIX = "pw-reset:";
  private static final String COOLDOWN_PREFIX = "pw-reset-cd:";
  private static final int TOKEN_BYTES = 32; // 256-bit, URL-safe

  private final StringRedisTemplate redis;
  private final SecureRandom random = new SecureRandom();
  private final Duration tokenTtl;
  private final Duration cooldown;

  public PasswordResetTokenStore(
      StringRedisTemplate redis,
      @Value("${seustock.password-reset.token-ttl}") Duration tokenTtl,
      @Value("${seustock.password-reset.resend-cooldown}") Duration cooldown) {
    this.redis = redis;
    this.tokenTtl = tokenTtl;
    this.cooldown = cooldown;
  }

  /** 새 토큰을 발급해 이메일과 매핑하여 TTL과 함께 저장하고, 토큰 문자열을 반환한다. */
  public String issue(String email) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redis.opsForValue().set(TOKEN_PREFIX + token, email, tokenTtl);
    return token;
  }

  /** 토큰에 매핑된 이메일을 조회한다. 만료/미존재 시 빈 값. */
  public Optional<String> emailFor(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(redis.opsForValue().get(TOKEN_PREFIX + token));
  }

  /** 토큰을 즉시 폐기한다(1회용). */
  public void consume(String token) {
    redis.delete(TOKEN_PREFIX + token);
  }

  /** 해당 이메일이 재발송 쿨다운 중인지 여부. */
  public boolean onCooldown(String email) {
    return Boolean.TRUE.equals(redis.hasKey(COOLDOWN_PREFIX + email));
  }

  /** 해당 이메일에 대한 재발송 쿨다운을 시작한다. */
  public void startCooldown(String email) {
    redis.opsForValue().set(COOLDOWN_PREFIX + email, "1", cooldown);
  }
}
