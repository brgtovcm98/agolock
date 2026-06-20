package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordForm {

  @NotBlank private String token;

  @NotBlank(message = "{valid.resetPassword.password.notBlank}")
  @Size(min = 8, max = 50, message = "{valid.resetPassword.password.size}")
  private String password;

  @NotBlank(message = "{valid.resetPassword.passwordConfirm.notBlank}")
  private String passwordConfirm;
}
