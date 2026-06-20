package com.seu.seustock.model.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegistrationForm {

  @NotBlank(message = "{valid.userRegistration.email.notBlank}")
  @Email(message = "{valid.userRegistration.email.invalid}")
  @Size(max = 255, message = "{valid.userRegistration.email.size}")
  private String email;

  @NotBlank(message = "{valid.userRegistration.nickname.notBlank}")
  @Size(min = 2, max = 20, message = "{valid.userRegistration.nickname.size}")
  private String nickname;

  @NotBlank(message = "{valid.userRegistration.password.notBlank}")
  @Size(min = 8, max = 50, message = "{valid.userRegistration.password.size}")
  private String password;

  @NotBlank(message = "{valid.userRegistration.passwordConfirm.notBlank}")
  private String passwordConfirm;
}
