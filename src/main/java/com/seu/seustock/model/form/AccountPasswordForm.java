package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountPasswordForm {

  @NotBlank(message = "{valid.account.password.current.notBlank}")
  private String currentPassword;

  @NotBlank(message = "{valid.account.password.new.notBlank}")
  @Size(min = 8, max = 50, message = "{valid.account.password.new.size}")
  private String newPassword;

  @NotBlank(message = "{valid.account.password.newConfirm.notBlank}")
  private String newPasswordConfirm;
}
