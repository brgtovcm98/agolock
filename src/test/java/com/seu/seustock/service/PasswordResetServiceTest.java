package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final String BASE_URL = "http://localhost:8080";
  private static final String EMAIL = "user@example.com";
  private static final String TOKEN = "TOKEN123";
  private static final String RAW_PASSWORD = "newSecret1";
  private static final String ENCODED_PASSWORD = "$2a$10$encodedHashValue";

  @Mock private UserMapper userMapper;
  @Mock private PasswordResetTokenStore tokenStore;
  @Mock private PasswordResetMailSender mailSender;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private MessageSource messageSource;

  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    service =
        new PasswordResetService(
            userMapper, tokenStore, mailSender, passwordEncoder, messageSource, BASE_URL);
  }

  private void stubMessageSource() {
    when(messageSource.getMessage(anyString(), any(), any())).thenReturn("유효하지 않거나 만료된 링크입니다.");
  }

  private UserDTO storedUser() {
    UserDTO user = new UserDTO();
    user.setId(1L);
    user.setEmail(EMAIL);
    user.setPassword("OLD_HASH");
    return user;
  }

  // ── requestReset ──────────────────────────────────────────────────────────

  @Test
  void requestReset_existingUser_issuesTokenAndSendsResetLink() {
    when(userMapper.findByEmail(EMAIL)).thenReturn(Optional.of(storedUser()));
    when(tokenStore.onCooldown(EMAIL)).thenReturn(false);
    when(tokenStore.issue(EMAIL)).thenReturn(TOKEN);

    service.requestReset(EMAIL);

    verify(tokenStore).issue(EMAIL);
    verify(tokenStore).startCooldown(EMAIL);

    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(mailSender).send(eq(EMAIL), urlCaptor.capture());
    assertThat(urlCaptor.getValue()).isEqualTo(BASE_URL + "/password/reset?token=" + TOKEN);
  }

  @Test
  void requestReset_unknownEmail_doesNothing() {
    when(userMapper.findByEmail(EMAIL)).thenReturn(Optional.empty());

    service.requestReset(EMAIL);

    verify(tokenStore, never()).issue(anyString());
    verify(mailSender, never()).send(anyString(), anyString());
  }

  @Test
  void requestReset_onCooldown_doesNotSendMail() {
    when(userMapper.findByEmail(EMAIL)).thenReturn(Optional.of(storedUser()));
    when(tokenStore.onCooldown(EMAIL)).thenReturn(true);

    service.requestReset(EMAIL);

    verify(tokenStore, never()).issue(anyString());
    verify(mailSender, never()).send(anyString(), anyString());
  }

  @Test
  void requestReset_normalizesEmail() {
    when(userMapper.findByEmail(EMAIL)).thenReturn(Optional.of(storedUser()));
    when(tokenStore.onCooldown(EMAIL)).thenReturn(false);
    when(tokenStore.issue(EMAIL)).thenReturn(TOKEN);

    service.requestReset("  User@EXAMPLE.com  ");

    verify(tokenStore).issue(EMAIL);
    verify(mailSender).send(eq(EMAIL), anyString());
  }

  // ── resetPassword ─────────────────────────────────────────────────────────

  @Test
  void resetPassword_validToken_updatesEncodedPasswordAndConsumesToken() {
    when(tokenStore.emailFor(TOKEN)).thenReturn(Optional.of(EMAIL));
    when(userMapper.findByEmail(EMAIL)).thenReturn(Optional.of(storedUser()));
    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

    service.resetPassword(TOKEN, RAW_PASSWORD);

    ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
    verify(userMapper).updatePassword(captor.capture());
    assertThat(captor.getValue().getPassword()).isEqualTo(ENCODED_PASSWORD);
    assertThat(captor.getValue().getPassword()).isNotEqualTo(RAW_PASSWORD);
    verify(tokenStore).consume(TOKEN);
  }

  @Test
  void resetPassword_invalidToken_throwsAndDoesNotUpdate() {
    stubMessageSource();
    when(tokenStore.emailFor("BAD")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resetPassword("BAD", RAW_PASSWORD))
        .isInstanceOf(IllegalArgumentException.class);

    verify(userMapper, never()).updatePassword(any());
    verify(tokenStore, never()).consume(anyString());
  }
}
