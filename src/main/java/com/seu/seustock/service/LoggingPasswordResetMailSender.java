package com.seu.seustock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 개발용 기본 메일 발송기. 실제로 메일을 보내지 않고 발송 준비 이벤트만 로그로 출력한다. {@code seustock.mail.type=smtp}로 설정하면 {@link
 * SmtpPasswordResetMailSender}로 교체된다 (기본값은 logging). 두 구현은 상호배타적인 {@code @ConditionalOnProperty}로
 * 정확히 하나만 활성화된다.
 */
@Service
@ConditionalOnProperty(name = "seustock.mail.type", havingValue = "logging", matchIfMissing = true)
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailSender.class);

  @Override
  public void send(String toEmail, String resetUrl) {
    log.info("password reset mail prepared");
  }
}
