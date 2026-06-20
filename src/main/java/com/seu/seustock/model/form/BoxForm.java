package com.seu.seustock.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoxForm {

  @NotBlank(message = "{valid.box.name.notBlank}")
  @Size(max = 100, message = "{valid.box.name.size}")
  private String name;
}
