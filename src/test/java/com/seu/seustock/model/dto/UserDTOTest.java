package com.seu.seustock.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserDTOTest {

  @Test
  void toStringExcludesPassword() {
    UserDTO user = new UserDTO();
    user.setId(1L);
    user.setEmail("user@example.com");
    user.setPassword("encoded-password");

    assertThat(user.toString()).doesNotContain("encoded-password");
    assertThat(user.toString()).doesNotContain("password=");
  }
}
