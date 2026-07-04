package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountNicknameForm {

  @NotBlank(message = "{valid.account.nickname.notBlank}")
  @Size(min = 2, max = 20, message = "{valid.account.nickname.size}")
  private String nickname;
}
