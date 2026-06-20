package com.seu.seustock.model.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordForm {

  @NotBlank(message = "{valid.forgotPassword.email.notBlank}")
  @Email(message = "{valid.forgotPassword.email.invalid}")
  @Size(max = 255, message = "{valid.forgotPassword.email.size}")
  private String email;
}
