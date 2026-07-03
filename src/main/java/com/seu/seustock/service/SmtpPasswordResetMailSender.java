package com.seu.seustock.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 운영용 SMTP 메일 발송기. {@code seustock.mail.type=smtp}일 때만 활성화된다 (기본값 logging — {@link
 * LoggingPasswordResetMailSender}). 본문은 Thymeleaf 템플릿({@code
 * templates/email/password-reset.html})으로 렌더링하며 항상 한국어로 보낸다.
 *
 * <p>Gmail SMTP 사용 시 발신 주소는 인증된 계정으로 고정되므로 {@code seustock.mail.from}은 {@code
 * spring.mail.username}과 동일한 Gmail 주소여야 한다.
 */
@Service
@ConditionalOnProperty(name = "seustock.mail.type", havingValue = "smtp")
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailSender.class);

  private final JavaMailSender mailSender;
  private final SpringTemplateEngine templateEngine;
  private final MessageSource messageSource;
  private final String from;

  public SmtpPasswordResetMailSender(
      JavaMailSender mailSender,
      SpringTemplateEngine templateEngine,
      MessageSource messageSource,
      @Value("${seustock.mail.from}") String from) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.messageSource = messageSource;
    this.from = from;
  }

  @Override
  public void send(String toEmail, String resetUrl) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(from);
      helper.setTo(toEmail);
      helper.setSubject(
          messageSource.getMessage("mail.passwordReset.subject", null, Locale.KOREAN));

      Context context = new Context(Locale.KOREAN);
      context.setVariable("resetUrl", resetUrl);
      String html = templateEngine.process("email/password-reset", context);
      helper.setText(html, true);

      mailSender.send(message);
      // 수신자/토큰은 로그에 남기지 않는다(기존 보안 규율 유지).
      log.info("password reset mail sent via smtp");
    } catch (MessagingException e) {
      throw new RuntimeException(
          messageSource.getMessage(
              "error.passwordReset.sendFailed", null, LocaleContextHolder.getLocale()),
          e);
    }
  }
}
