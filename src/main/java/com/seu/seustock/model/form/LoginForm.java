package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginForm {

  @NotBlank(message = "{valid.login.email.notBlank}")
  private String email;

  @NotBlank(message = "{valid.login.password.notBlank}")
  private String password;
}
