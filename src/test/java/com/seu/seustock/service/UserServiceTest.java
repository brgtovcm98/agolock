package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.seu.seustock.configuration.AuthenticatedUser;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.form.LoginForm;
import com.seu.seustock.model.form.UserRegistrationForm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String RAW_PASSWORD = "plaintext123";
  private static final String ENCODED_PASSWORD = "$2a$10$encodedHashValue";

  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  // ── existsByEmail ─────────────────────────────────────────────────────────

  @Test
  void existsByEmail_returnsTrueWhenPresent() {
    UserDTO existing = new UserDTO();
    existing.setEmail("alice@test.com");

    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(existing));

    assertThat(userService.existsByEmail("alice@test.com")).isTrue();
  }

  @Test
  void existsByEmail_returnsFalseWhenAbsent() {
    when(userMapper.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

    assertThat(userService.existsByEmail("ghost@test.com")).isFalse();
  }

  @Test
  void existsByEmail_normalizesCaseAndWhitespace() {
    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(new UserDTO()));

    assertThat(userService.existsByEmail("  Alice@TEST.com  ")).isTrue();
  }

  // ── register ──────────────────────────────────────────────────────────────

  @Test
  void register_encodesPasswordAndStoresEmailAndNickname() {
    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

    UserRegistrationForm form = new UserRegistrationForm();
    form.setEmail("newuser@test.com");
    form.setNickname("뉴비");
    form.setPassword(RAW_PASSWORD);

    userService.register(form);

    ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
    verify(userMapper).insertUser(captor.capture());
    assertThat(captor.getValue().getPassword()).isEqualTo(ENCODED_PASSWORD);
    assertThat(captor.getValue().getEmail()).isEqualTo("newuser@test.com");
    assertThat(captor.getValue().getNickname()).isEqualTo("뉴비");
  }

  @Test
  void register_neverStoresRawPassword() {
    when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

    UserRegistrationForm form = new UserRegistrationForm();
    form.setEmail("newuser@test.com");
    form.setNickname("뉴비");
    form.setPassword(RAW_PASSWORD);

    userService.register(form);

    ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
    verify(userMapper).insertUser(captor.capture());
    assertThat(captor.getValue().getPassword()).isNotEqualTo(RAW_PASSWORD);
  }

  @Test
  void register_storesNormalizedEmail() {
    when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

    UserRegistrationForm form = new UserRegistrationForm();
    form.setEmail("  NewUser@TEST.COM  ");
    form.setNickname("뉴비");
    form.setPassword(RAW_PASSWORD);

    userService.register(form);

    ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
    verify(userMapper).insertUser(captor.capture());
    assertThat(captor.getValue().getEmail()).isEqualTo("newuser@test.com");
  }

  // ── authenticate ──────────────────────────────────────────────────────────

  @Test
  void authenticate_returnsUserOnCorrectCredentials() {
    UserDTO stored = new UserDTO();
    stored.setEmail("alice@test.com");
    stored.setPassword(ENCODED_PASSWORD);

    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(stored));
    when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

    LoginForm form = new LoginForm();
    form.setEmail("alice@test.com");
    form.setPassword(RAW_PASSWORD);

    Optional<UserDTO> result = userService.authenticate(form);

    assertThat(result).isPresent();
    assertThat(result.get()).isSameAs(stored);
  }

  @Test
  void authenticate_returnsEmptyOnWrongPassword() {
    UserDTO stored = new UserDTO();
    stored.setEmail("alice@test.com");
    stored.setPassword(ENCODED_PASSWORD);

    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(stored));
    when(passwordEncoder.matches(eq("wrongpassword"), anyString())).thenReturn(false);

    LoginForm form = new LoginForm();
    form.setEmail("alice@test.com");
    form.setPassword("wrongpassword");

    Optional<UserDTO> result = userService.authenticate(form);

    assertThat(result).isEmpty();
  }

  @Test
  void authenticate_returnsEmptyWhenUserNotFound() {
    when(userMapper.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

    LoginForm form = new LoginForm();
    form.setEmail("unknown@test.com");
    form.setPassword(RAW_PASSWORD);

    Optional<UserDTO> result = userService.authenticate(form);

    assertThat(result).isEmpty();
    verify(passwordEncoder, never()).matches(anyString(), anyString());
  }

  @Test
  void authenticate_normalizesEmailBeforeLookup() {
    UserDTO stored = new UserDTO();
    stored.setEmail("alice@test.com");
    stored.setPassword(ENCODED_PASSWORD);

    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(stored));
    when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

    LoginForm form = new LoginForm();
    form.setEmail("  ALICE@TEST.COM  ");
    form.setPassword(RAW_PASSWORD);

    Optional<UserDTO> result = userService.authenticate(form);

    assertThat(result).isPresent();
  }

  // ── updateNickname ────────────────────────────────────────────────────────

  @Test
  void updateNickname_updatesNicknameAndRefreshesPrincipal() {
    UserDTO user = new UserDTO();
    user.setId(1L);
    user.setEmail("alice@test.com");
    user.setNickname("old");
    user.setPassword("encoded");

    AuthenticatedUser principal =
        new AuthenticatedUser(
            "alice@test.com", "encoded", "old", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(auth);

    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(user));

    userService.updateNickname("alice@test.com", "new");

    ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
    verify(userMapper).updateNickname(captor.capture());
    assertThat(captor.getValue().getNickname()).isEqualTo("new");

    Authentication updatedAuth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(updatedAuth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
    assertThat(((AuthenticatedUser) updatedAuth.getPrincipal()).getNickname()).isEqualTo("new");

    SecurityContextHolder.clearContext();
  }

  // ── updatePassword ────────────────────────────────────────────────────────

  @Test
  void updatePassword_withCorrectCurrentPassword_encodesAndUpdates() {
    UserDTO user = new UserDTO();
    user.setId(1L);
    user.setEmail("alice@test.com");
    user.setPassword("encodedOld");

    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("old123", "encodedOld")).thenReturn(true);
    when(passwordEncoder.encode("new123")).thenReturn("encodedNew");

    userService.updatePassword("alice@test.com", "old123", "new123");

    ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
    verify(userMapper).updatePassword(captor.capture());
    assertThat(captor.getValue().getPassword()).isEqualTo("encodedNew");
  }

  @Test
  void updatePassword_withWrongCurrentPassword_throws() {
    UserDTO user = new UserDTO();
    user.setEmail("alice@test.com");
    user.setPassword("encodedOld");

    when(userMapper.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "encodedOld")).thenReturn(false);

    assertThatThrownBy(() -> userService.updatePassword("alice@test.com", "wrong", "new123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.password.incorrect");
  }
}
