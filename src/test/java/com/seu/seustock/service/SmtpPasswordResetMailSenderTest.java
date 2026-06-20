package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@ExtendWith(MockitoExtension.class)
class SmtpPasswordResetMailSenderTest {

  private static final String FROM = "no-reply@seustock.test";
  private static final String SUBJECT = "[SeuStock] 비밀번호 재설정 안내";

  @Mock private JavaMailSender mailSender;
  @Mock private SpringTemplateEngine templateEngine;
  @Mock private MessageSource messageSource;

  private SmtpPasswordResetMailSender sender;

  @BeforeEach
  void setUp() {
    sender = new SmtpPasswordResetMailSender(mailSender, templateEngine, messageSource, FROM);
  }

  private MimeMessage emptyMimeMessage() {
    return new MimeMessage(Session.getInstance(new Properties()));
  }

  @Test
  void sendBuildsHtmlMessageWithCorrectHeadersAndBody() throws Exception {
    String to = "user@example.com";
    String resetUrl = "https://example.com/password/reset?token=abc123";
    String html =
        "<html><body><a href=\"" + resetUrl + "\">reset</a> " + resetUrl + "</body></html>";

    when(mailSender.createMimeMessage()).thenReturn(emptyMimeMessage());
    when(messageSource.getMessage(eq("mail.passwordReset.subject"), isNull(), eq(Locale.KOREAN)))
        .thenReturn(SUBJECT);
    when(templateEngine.process(eq("email/password-reset"), any(Context.class))).thenReturn(html);

    sender.send(to, resetUrl);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage sent = captor.getValue();
    // 실제 JavaMailSender.send()가 호출하는 단계 — 헤더(Content-Type 등)를 본문에서 플러시한다.
    sent.saveChanges();

    assertThat(sent.getAllRecipients()).hasSize(1);
    assertThat(sent.getAllRecipients()[0].toString()).isEqualTo(to);
    assertThat(((InternetAddress) sent.getFrom()[0]).getAddress()).isEqualTo(FROM);
    assertThat(sent.getSubject()).isEqualTo(SUBJECT);
    assertThat(sent.getContentType()).contains("text/html");
    assertThat((String) sent.getContent()).contains(resetUrl);
  }

  @Test
  void sendPassesResetUrlToTemplateContext() {
    when(mailSender.createMimeMessage()).thenReturn(emptyMimeMessage());
    when(messageSource.getMessage(eq("mail.passwordReset.subject"), isNull(), eq(Locale.KOREAN)))
        .thenReturn(SUBJECT);
    when(templateEngine.process(eq("email/password-reset"), any(Context.class)))
        .thenReturn("<html></html>");

    sender.send("user@example.com", "https://example.com/password/reset?token=xyz");

    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine).process(eq("email/password-reset"), contextCaptor.capture());
    assertThat(contextCaptor.getValue().getVariable("resetUrl"))
        .isEqualTo("https://example.com/password/reset?token=xyz");
  }

  @Test
  void sendPropagatesMailSendFailureAsRuntimeException() {
    when(mailSender.createMimeMessage()).thenReturn(emptyMimeMessage());
    when(messageSource.getMessage(eq("mail.passwordReset.subject"), isNull(), eq(Locale.KOREAN)))
        .thenReturn(SUBJECT);
    when(templateEngine.process(eq("email/password-reset"), any(Context.class)))
        .thenReturn("<html></html>");
    doThrow(new MailSendException("smtp unavailable"))
        .when(mailSender)
        .send(any(MimeMessage.class));

    assertThatThrownBy(
            () -> sender.send("user@example.com", "https://example.com/password/reset?token=t"))
        .isInstanceOf(RuntimeException.class);
  }
}
