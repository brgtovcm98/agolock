package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoggingPasswordResetMailSenderTest {

  @Test
  void sendDoesNotLogRecipientOrResetToken(CapturedOutput output) {
    LoggingPasswordResetMailSender sender = new LoggingPasswordResetMailSender();

    sender.send("user@example.com", "https://example.com/password/reset?token=secret-token");

    assertThat(output).contains("password reset mail prepared");
    assertThat(output).doesNotContain("user@example.com");
    assertThat(output).doesNotContain("secret-token");
    assertThat(output).doesNotContain("/password/reset?token=");
  }
}
