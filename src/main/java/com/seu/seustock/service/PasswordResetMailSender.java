package com.seu.seustock.service;

/**
 * 비밀번호 재설정 안내 메일 발송기. 구현체를 교체할 수 있도록 인터페이스로 분리한다 (예: 개발용 로깅 구현 → 운영용 SMTP 구현). {@code
 * ImageStorageService}와 동일한 "인터페이스 + @Primary 구현" 패턴을 따른다.
 */
public interface PasswordResetMailSender {

  /**
   * 재설정 링크를 수신자에게 전달한다.
   *
   * @param toEmail 수신자 이메일
   * @param resetUrl 새 비밀번호를 설정할 수 있는 전체 URL
   */
  void send(String toEmail, String resetUrl);
}
